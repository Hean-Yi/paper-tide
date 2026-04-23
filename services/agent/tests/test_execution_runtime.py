from app.agent_platform.messages import AnalysisRequestedMessage
from app.main import create_app


def test_runtime_executes_reviewer_assist_and_emits_completed_event() -> None:
    app = create_app(enable_background_execution=False, require_internal_api_key=False)
    runtime = app.state.agent_platform
    requested = AnalysisRequestedMessage(
        idempotency_key="key-1",
        analysis_type="REVIEWER_ASSIST",
        intent_reference="101",
        request_payload={
            "title": "Boundary Paper",
            "abstract": "A paper about task boundaries.",
            "keywords": ["agent", "platform"],
            "reviewerAssist": {
                "assignmentId": 77,
                "roundId": 8,
                "manuscriptId": 9,
                "versionId": 10,
            },
        },
    )
    job = runtime.analysis_requested_consumer.handle(requested)

    event = runtime.execute_requested_job(job)

    assert event["eventType"] == "analysis.completed"
    assert event["intentId"] == 101
    assert event["jobId"] == job.job_id
    assert event["analysisType"] == "REVIEWER_ASSIST"
    assert event["businessStatus"] == "AVAILABLE"
    assert event["summaryProjection"]["businessStatus"] == "AVAILABLE"
    assert event["redactedResult"]["taskType"] == "REVIEW_ASSIST_ANALYSIS"


def test_runtime_uses_durable_repository_when_db_config_present(monkeypatch) -> None:
    class FakeCursor:
        def __init__(self, connection: "FakeConnection") -> None:
            self._connection = connection
            self._fetched_one = None

        def __enter__(self) -> "FakeCursor":
            return self

        def __exit__(self, exc_type, exc, tb) -> None:
            return None

        def execute(self, sql: str, params: dict[str, object] | None = None) -> None:
            normalized = " ".join(sql.split())
            values = params or {}
            if "FROM EXECUTION_JOB WHERE IDEMPOTENCY_KEY = :idempotency_key" in normalized:
                row = self._connection.jobs.get(str(values["idempotency_key"]))
                self._fetched_one = None if row is None else (
                    row["JOB_ID"],
                    row["IDEMPOTENCY_KEY"],
                    row["ANALYSIS_TYPE"],
                    row["EXECUTION_STATE"],
                    row["INPUT_SNAPSHOT"],
                    row["FAILURE_REASON"],
                    row["ATTEMPT_COUNT"],
                    row["INTENT_ID"],
                    row["CREATED_AT"],
                )
                return
            if "FROM EXECUTION_JOB WHERE JOB_ID = :job_id" in normalized:
                row = next((item for item in self._connection.jobs.values() if item["JOB_ID"] == str(values["job_id"])), None)
                self._fetched_one = None if row is None else (
                    row["JOB_ID"],
                    row["IDEMPOTENCY_KEY"],
                    row["ANALYSIS_TYPE"],
                    row["EXECUTION_STATE"],
                    row["INPUT_SNAPSHOT"],
                    row["FAILURE_REASON"],
                    row["ATTEMPT_COUNT"],
                    row["INTENT_ID"],
                    row["CREATED_AT"],
                )
                return
            if normalized.startswith("INSERT INTO EXECUTION_JOB"):
                self._connection.jobs[str(values["idempotency_key"])] = {
                    "JOB_ID": str(values["job_id"]),
                    "IDEMPOTENCY_KEY": str(values["idempotency_key"]),
                    "ANALYSIS_TYPE": str(values["analysis_type"]),
                    "EXECUTION_STATE": str(values["execution_state"]),
                    "INPUT_SNAPSHOT": str(values["input_snapshot"]),
                    "FAILURE_REASON": values["failure_reason"],
                    "ATTEMPT_COUNT": int(values["attempt_count"]),
                    "INTENT_ID": int(values["intent_id"]),
                    "CREATED_AT": values["created_at"],
                }
                return
            if normalized.startswith("UPDATE EXECUTION_JOB"):
                row = next(item for item in self._connection.jobs.values() if item["JOB_ID"] == str(values["job_id"]))
                row["EXECUTION_STATE"] = str(values["execution_state"])
                row["FAILURE_REASON"] = values["failure_reason"]
                row["ATTEMPT_COUNT"] = int(values["attempt_count"])
                return
            if normalized.startswith("INSERT INTO EXECUTION_OUTBOX"):
                return
            raise AssertionError(f"unexpected SQL in runtime fake: {normalized}")

        def fetchone(self):
            return self._fetched_one

        def fetchall(self):
            return []

    class FakeConnection:
        def __init__(self) -> None:
            self.jobs: dict[str, dict[str, object]] = {}

        def cursor(self) -> FakeCursor:
            return FakeCursor(self)

        def commit(self) -> None:
            return None

    monkeypatch.setenv("AGENT_PLATFORM_DB_USER", "agent_user")
    monkeypatch.setenv("AGENT_PLATFORM_DB_PASSWORD", "secret")
    monkeypatch.setenv("AGENT_PLATFORM_DB_DSN", "localhost:1521/FREEPDB1")
    connection = FakeConnection()

    app = create_app(
        enable_background_execution=False,
        require_internal_api_key=False,
        db_connection_factory=lambda: connection,
    )
    runtime = app.state.agent_platform
    requested = AnalysisRequestedMessage(
        idempotency_key="key-2",
        analysis_type="REVIEWER_ASSIST",
        intent_reference="202",
        request_payload={
            "title": "Durable Boundary Paper",
            "abstract": "A paper about durable runtime boundaries.",
            "keywords": ["agent", "platform"],
            "reviewerAssist": {
                "assignmentId": 88,
                "roundId": 9,
                "manuscriptId": 10,
                "versionId": 11,
            },
        },
    )

    job = runtime.analysis_requested_consumer.handle(requested)
    event = runtime.execute_requested_job(job)
    persisted = runtime.execution_job_repository.get(job.job_id)

    assert event["intentId"] == 202
    assert persisted is not None
    assert persisted.execution_state == "SUCCEEDED"
