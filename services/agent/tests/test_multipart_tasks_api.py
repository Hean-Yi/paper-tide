import json

from fastapi.testclient import TestClient

from app.main import create_app
from app.routes.tasks import build_tasks_router
from app.task_store import TaskStore


PDF_BYTES = b"""%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>
endobj
4 0 obj
<< /Length 171 >>
stream
BT
/F1 12 Tf
72 720 Td
(Introduction This paper studies robust review systems.) Tj
72 700 Td
(Method We use deterministic parsing.) Tj
72 680 Td
(Experiment Results show stable behavior.) Tj
72 660 Td
(Conclusion The approach is practical.) Tj
ET
endstream
endobj
5 0 obj
<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>
endobj
xref
0 6
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000115 00000 n
0000000241 00000 n
0000000464 00000 n
trailer
<< /Root 1 0 R /Size 6 >>
startxref
534
%%EOF
"""


def test_multipart_task_creation_normalizes_pdf_payload() -> None:
    task_store = TaskStore()
    started_task_ids: list[str] = []
    app = create_app(enable_background_execution=False, require_internal_api_key=False, task_store=task_store)
    client = TestClient(app)

    metadata = {
        "taskType": "REVIEW_ASSIST_ANALYSIS",
        "manuscriptId": 101,
        "versionId": 201,
        "roundId": 12,
        "requestPayload": {
            "title": "Robust Review Systems",
            "abstract": "A review workflow paper.",
            "keywords": ["review", "workflow"],
        },
    }

    response = client.post(
        "/agent/tasks",
        data={"metadata": json.dumps(metadata)},
        files={"file": ("paper.pdf", PDF_BYTES, "application/pdf")},
    )

    assert response.status_code == 200
    task_id = response.json()["taskId"]
    task = task_store.get_task(task_id)
    assert task is not None
    assert task.request_payload is not None
    assert task.request_payload["title"] == "Robust Review Systems"
    assert task.request_payload["keywords"] == ["review", "workflow"]
    assert "robust review systems" in task.request_payload["pdfText"].lower()
    assert task.request_payload["sections"]["introduction"]
    assert task.request_payload["sections"]["method"]
    assert task.request_payload["pdfExtraction"]["fileName"] == "paper.pdf"
    assert task.request_payload["pdfExtraction"]["status"] == "EXTRACTED"
    assert started_task_ids == []


def test_multipart_tasks_require_internal_api_key(monkeypatch) -> None:
    monkeypatch.setenv("AGENT_INTERNAL_API_KEY", "secret")
    client = TestClient(create_app(enable_background_execution=False))
    metadata = {
        "taskType": "SCREENING_ANALYSIS",
        "manuscriptId": 101,
        "versionId": 201,
        "requestPayload": {"title": "A paper"},
    }

    missing_header = client.post(
        "/agent/tasks",
        data={"metadata": json.dumps(metadata)},
        files={"file": ("paper.pdf", PDF_BYTES, "application/pdf")},
    )
    correct_header = client.post(
        "/agent/tasks",
        headers={"X-Agent-Api-Key": "secret"},
        data={"metadata": json.dumps(metadata)},
        files={"file": ("paper.pdf", PDF_BYTES, "application/pdf")},
    )

    assert missing_header.status_code == 401
    assert correct_header.status_code == 200


def test_multipart_rejects_non_pdf_upload() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))
    metadata = {
        "taskType": "SCREENING_ANALYSIS",
        "manuscriptId": 101,
        "versionId": 201,
        "requestPayload": {},
    }

    response = client.post(
        "/agent/tasks",
        data={"metadata": json.dumps(metadata)},
        files={"file": ("paper.txt", b"not a pdf", "text/plain")},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "Only PDF uploads are supported"


def test_multipart_reuses_existing_cache_entry() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))
    metadata = {
        "taskType": "SCREENING_ANALYSIS",
        "manuscriptId": 101,
        "versionId": 201,
        "requestPayload": {"title": "A paper"},
    }

    first = client.post(
        "/agent/tasks",
        data={"metadata": json.dumps(metadata)},
        files={"file": ("paper.pdf", PDF_BYTES, "application/pdf")},
    )
    second = client.post(
        "/agent/tasks",
        data={"metadata": json.dumps(metadata)},
        files={"file": ("paper.pdf", PDF_BYTES, "application/pdf")},
    )

    assert first.status_code == 200
    assert second.status_code == 200
    assert second.json()["taskId"] == first.json()["taskId"]


def test_json_task_creation_still_works() -> None:
    client = TestClient(create_app(enable_background_execution=False, require_internal_api_key=False))

    response = client.post(
        "/agent/tasks",
        json={
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": 101,
            "versionId": 201,
            "requestPayload": {"title": "A paper"},
        },
    )

    assert response.status_code == 200
    assert response.json()["status"] == "PENDING"
