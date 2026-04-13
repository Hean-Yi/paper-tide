from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.main import create_app
from app.routes.tasks import build_tasks_router
from app.task_store import TaskStore


def test_create_task_returns_pending_status() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))

    response = client.post(
        "/agent/tasks",
        json={
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": 101,
            "versionId": 201,
            "roundId": None,
            "requestPayload": {"blindCheck": True},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["taskId"]
    assert body["status"] == "PENDING"
    assert body["step"] == "queued"


def test_get_created_task_returns_pending_status() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))

    create_response = client.post(
        "/agent/tasks",
        json={
            "taskType": "REVIEW_ASSIST_ANALYSIS",
            "manuscriptId": 102,
            "versionId": 202,
            "roundId": 12,
            "requestPayload": {"focus": "novelty"},
        },
    )
    task_id = create_response.json()["taskId"]

    response = client.get(f"/agent/tasks/{task_id}")

    assert response.status_code == 200
    assert response.json() == {
        "taskId": task_id,
        "taskType": "REVIEW_ASSIST_ANALYSIS",
        "status": "PENDING",
        "step": "queued",
        "error": None,
    }


def test_get_unknown_task_returns_404() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))

    response = client.get("/agent/tasks/missing-task")

    assert response.status_code == 404
    assert response.json()["detail"] == "Task not found"


def test_get_result_before_completion_returns_409() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))

    create_response = client.post(
        "/agent/tasks",
        json={
            "taskType": "DECISION_CONFLICT_ANALYSIS",
            "manuscriptId": 103,
            "versionId": 203,
            "roundId": 13,
            "requestPayload": {"conflicts": ["reviewer_vote_split"]},
        },
    )
    task_id = create_response.json()["taskId"]

    response = client.get(f"/agent/tasks/{task_id}/result")

    assert response.status_code == 409
    assert response.json()["detail"] == "Task result is not ready"


def test_tasks_require_internal_api_key(monkeypatch) -> None:
    monkeypatch.setenv("AGENT_INTERNAL_API_KEY", "secret")
    client = TestClient(create_app(enable_background_execution=False))

    missing_header = client.post(
        "/agent/tasks",
        json={
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": 101,
            "versionId": 201,
            "requestPayload": {},
        },
    )
    wrong_header = client.post(
        "/agent/tasks",
        headers={"X-Agent-Api-Key": "wrong"},
        json={
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": 101,
            "versionId": 201,
            "requestPayload": {},
        },
    )
    correct_header = client.post(
        "/agent/tasks",
        headers={"X-Agent-Api-Key": "secret"},
        json={
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": 101,
            "versionId": 201,
            "requestPayload": {},
        },
    )

    assert missing_header.status_code == 401
    assert wrong_header.status_code == 401
    assert correct_header.status_code == 200


def test_missing_internal_api_key_config_returns_503(monkeypatch) -> None:
    monkeypatch.delenv("AGENT_INTERNAL_API_KEY", raising=False)
    client = TestClient(create_app(enable_background_execution=False))

    response = client.post(
        "/agent/tasks",
        headers={"X-Agent-Api-Key": "anything"},
        json={
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": 101,
            "versionId": 201,
            "requestPayload": {},
        },
    )

    assert response.status_code == 503


def test_duplicate_task_reuses_existing_cache_entry() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))
    payload = {
        "taskType": "SCREENING_ANALYSIS",
        "manuscriptId": 101,
        "versionId": 201,
        "requestPayload": {"title": "A paper", "abstract": "An abstract"},
    }

    first = client.post("/agent/tasks", json=payload)
    second = client.post("/agent/tasks", json=payload)

    assert first.status_code == 200
    assert second.status_code == 200
    assert second.json()["taskId"] == first.json()["taskId"]


def test_duplicate_pending_task_starts_background_once() -> None:
    task_store = TaskStore()
    started_task_ids: list[str] = []
    app = FastAPI()
    app.include_router(
        build_tasks_router(
            task_store,
            start_task=started_task_ids.append,
            require_internal_api_key=False,
        )
    )
    client = TestClient(app)
    payload = {
        "taskType": "SCREENING_ANALYSIS",
        "manuscriptId": 101,
        "versionId": 201,
        "requestPayload": {"title": "A paper"},
    }

    first = client.post("/agent/tasks", json=payload)
    second = client.post("/agent/tasks", json=payload)

    assert second.json()["taskId"] == first.json()["taskId"]
    assert started_task_ids == [first.json()["taskId"]]


def test_create_task_returns_pending_snapshot_before_background_updates() -> None:
    task_store = TaskStore()

    def start_task(task_id: str) -> None:
        task_store.update_status(task_id, status="PROCESSING", step="analyzing")

    app = FastAPI()
    app.include_router(
        build_tasks_router(
            task_store,
            start_task=start_task,
            require_internal_api_key=False,
        )
    )
    client = TestClient(app)

    response = client.post(
        "/agent/tasks",
        json={
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": 101,
            "versionId": 201,
            "requestPayload": {},
        },
    )
    task_id = response.json()["taskId"]

    assert response.json()["status"] == "PENDING"
    assert response.json()["step"] == "queued"
    assert client.get(f"/agent/tasks/{task_id}").json()["status"] == "PROCESSING"


def test_changed_request_payload_changes_cache_key() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))

    first = client.post(
        "/agent/tasks",
        json={
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": 101,
            "versionId": 201,
            "requestPayload": {"title": "A paper"},
        },
    )
    second = client.post(
        "/agent/tasks",
        json={
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": 101,
            "versionId": 201,
            "requestPayload": {"title": "A revised title"},
        },
    )

    assert first.status_code == 200
    assert second.status_code == 200
    assert second.json()["taskId"] != first.json()["taskId"]


def test_failed_task_is_replaced_without_force() -> None:
    store = TaskStore()
    task = store.create_or_reuse_task(
        task_type="SCREENING_ANALYSIS",
        manuscript_id=101,
        version_id=201,
        round_id=None,
        request_payload={"title": "A paper"},
    )
    store.update_status(task.task_id, status="FAILED", step="failed", error="timeout")

    fresh = store.create_or_reuse_task(
        task_type="SCREENING_ANALYSIS",
        manuscript_id=101,
        version_id=201,
        round_id=None,
        request_payload={"title": "A paper"},
    )
    assert fresh.task_id != task.task_id
    assert fresh.status == "PENDING"


def test_failed_task_is_replaced_with_force() -> None:
    store = TaskStore()
    task = store.create_or_reuse_task(
        task_type="SCREENING_ANALYSIS",
        manuscript_id=101,
        version_id=201,
        round_id=None,
        request_payload={"title": "A paper"},
    )
    store.update_status(task.task_id, status="FAILED", step="failed", error="timeout")

    fresh = store.create_or_reuse_task(
        task_type="SCREENING_ANALYSIS",
        manuscript_id=101,
        version_id=201,
        round_id=None,
        request_payload={"title": "A paper"},
        force=True,
    )
    assert fresh.task_id != task.task_id
    assert fresh.status == "PENDING"


def test_force_creates_fresh_task() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))
    payload = {
        "taskType": "SCREENING_ANALYSIS",
        "manuscriptId": 101,
        "versionId": 201,
        "requestPayload": {"title": "A paper"},
    }

    first = client.post("/agent/tasks", json=payload)
    second = client.post("/agent/tasks", json={**payload, "force": True})

    assert first.status_code == 200
    assert second.status_code == 200
    assert second.json()["taskId"] != first.json()["taskId"]
