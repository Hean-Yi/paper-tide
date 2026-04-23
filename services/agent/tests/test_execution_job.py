import pytest

from app.agent_platform.domain import ExecutionJob
from app.agent_platform.messages import AnalysisRequestedMessage
from app.agent_platform.outbox import InMemoryExecutionOutbox, OracleExecutionOutbox
from app.agent_platform.repositories import InMemoryExecutionJobRepository, OracleExecutionJobRepository
from app.agent_platform.publisher import AnalysisRequestedPublisher
from app.agent_platform.state_machine import ExecutionStateMachine


def test_retryable_failure_transitions_to_dead_letter_after_limit() -> None:
    job = ExecutionJob.new("job-1", "key-1", "REVIEWER_ASSIST", {"title": "Paper"})

    machine = ExecutionStateMachine(max_attempts=2)
    job = machine.mark_running(job)
    job = machine.mark_retryable_failure(job, "provider timeout")
    job = machine.mark_running(job)
    job = machine.mark_retryable_failure(job, "provider timeout")

    assert job.execution_state == "DEAD_LETTERED"


def test_duplicate_intake_reuses_existing_job_id() -> None:
    repo = InMemoryExecutionJobRepository()

    first = repo.create_or_reuse("key-1", "REVIEWER_ASSIST", {"title": "Paper"})
    second = repo.create_or_reuse("key-1", "REVIEWER_ASSIST", {"title": "Paper"})

    assert second.job_id == first.job_id


def test_succeeded_job_cannot_be_revived_to_running() -> None:
    job = ExecutionJob.new("job-1", "key-1", "REVIEWER_ASSIST", {"title": "Paper"})
    machine = ExecutionStateMachine(max_attempts=2)
    job = machine.mark_running(job)
    job = machine.mark_succeeded(job)

    with pytest.raises(RuntimeError, match="terminal state SUCCEEDED"):
        machine.mark_running(job)

    assert job.execution_state == "SUCCEEDED"


def test_terminal_failure_job_cannot_be_overwritten_with_success() -> None:
    job = ExecutionJob.new("job-1", "key-1", "REVIEWER_ASSIST", {"title": "Paper"})
    machine = ExecutionStateMachine(max_attempts=2)
    job = machine.mark_running(job)
    job = machine.mark_terminal_failure(job, "bad input")

    with pytest.raises(RuntimeError, match="terminal state FAILED_TERMINAL"):
        machine.mark_succeeded(job)

    assert job.execution_state == "FAILED_TERMINAL"


def test_state_machine_returns_new_job_without_mutating_original() -> None:
    job = ExecutionJob.new("job-1", "key-1", "REVIEWER_ASSIST", {"title": "Paper"})
    machine = ExecutionStateMachine(max_attempts=2)

    running = machine.mark_running(job)

    assert running is not None
    assert running is not job
    assert job.execution_state == "QUEUED"
    assert job.attempt_count == 0
    assert running.execution_state == "RUNNING"
    assert running.attempt_count == 1


def test_execution_job_input_snapshot_is_recursively_immutable() -> None:
    job = ExecutionJob.new(
        "job-1",
        "key-1",
        "REVIEWER_ASSIST",
        {"paper": {"title": "Paper"}, "sections": [{"name": "intro"}]},
    )

    with pytest.raises(TypeError):
        job.input_snapshot["paper"]["title"] = "Changed"

    with pytest.raises(TypeError):
        job.input_snapshot["sections"][0]["name"] = "changed"

    mutable_copy = job.input_snapshot_copy()
    mutable_copy["paper"]["title"] = "Changed"
    mutable_copy["sections"][0]["name"] = "changed"

    assert job.input_snapshot["paper"]["title"] == "Paper"
    assert job.input_snapshot["sections"][0]["name"] == "intro"


def test_mark_published_returns_new_message_without_mutating_pending_snapshot() -> None:
    outbox = InMemoryExecutionOutbox()
    publisher = AnalysisRequestedPublisher(outbox)
    message = publisher.publish(
        AnalysisRequestedMessage(
            idempotency_key="key-1",
            analysis_type="REVIEWER_ASSIST",
            request_payload={"title": "Paper"},
        ),
    )

    pending_snapshot = outbox.pending()[0]
    published = outbox.mark_published(message.message_id)

    assert pending_snapshot.published_at is None
    assert published is not None
    assert published is not pending_snapshot
    assert published.published_at is not None


class _FakeOracleCursor:
    def __init__(self, connection: "_FakeOracleConnection") -> None:
        self._connection = connection
        self._fetched_one: tuple[object, ...] | None = None
        self._fetched_all: list[tuple[object, ...]] = []

    def __enter__(self) -> "_FakeOracleCursor":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        return None

    def execute(self, sql: str, params: dict[str, object] | None = None) -> None:
        normalized = " ".join(sql.split())
        values = params or {}

        if "FROM EXECUTION_JOB WHERE JOB_ID = :job_id" in normalized:
            row = self._connection.jobs_by_id.get(str(values["job_id"]))
            self._fetched_one = None if row is None else self._connection.job_row_tuple(row)
            return

        if "FROM EXECUTION_JOB WHERE IDEMPOTENCY_KEY = :idempotency_key" in normalized:
            row = self._connection.jobs_by_key.get(str(values["idempotency_key"]))
            self._fetched_one = None if row is None else self._connection.job_row_tuple(row)
            return

        if normalized.startswith("INSERT INTO EXECUTION_JOB"):
            row = {
                "JOB_ID": str(values["job_id"]),
                "INTENT_ID": int(values["intent_id"]),
                "IDEMPOTENCY_KEY": str(values["idempotency_key"]),
                "ANALYSIS_TYPE": str(values["analysis_type"]),
                "EXECUTION_STATE": str(values["execution_state"]),
                "INPUT_SNAPSHOT": str(values["input_snapshot"]),
                "FAILURE_REASON": values["failure_reason"],
                "ATTEMPT_COUNT": int(values["attempt_count"]),
                "CREATED_AT": values["created_at"],
            }
            self._connection.jobs_by_id[row["JOB_ID"]] = row
            self._connection.jobs_by_key[row["IDEMPOTENCY_KEY"]] = row
            return

        if normalized.startswith("UPDATE EXECUTION_JOB"):
            row = self._connection.jobs_by_id[str(values["job_id"])]
            row["EXECUTION_STATE"] = str(values["execution_state"])
            row["FAILURE_REASON"] = values["failure_reason"]
            row["ATTEMPT_COUNT"] = int(values["attempt_count"])
            return

        if normalized.startswith("INSERT INTO EXECUTION_OUTBOX"):
            row = {
                "MESSAGE_KEY": str(values["message_key"]),
                "MESSAGE_TYPE": str(values["message_type"]),
                "MESSAGE_PAYLOAD": str(values["message_payload"]),
                "CREATED_AT": values["created_at"],
                "PUBLISHED_AT": None,
                "RETRY_COUNT": 0,
            }
            self._connection.outbox_by_key[row["MESSAGE_KEY"]] = row
            return

        if "FROM EXECUTION_OUTBOX WHERE PUBLISHED_AT IS NULL" in normalized:
            self._fetched_all = [
                self._connection.outbox_row_tuple(row)
                for row in self._connection.outbox_by_key.values()
                if row["PUBLISHED_AT"] is None
            ]
            return

        if normalized.startswith("UPDATE EXECUTION_OUTBOX"):
            row = self._connection.outbox_by_key[str(values["message_key"])]
            row["PUBLISHED_AT"] = values["published_at"]
            row["RETRY_COUNT"] = int(values["retry_count"])
            return

        if "FROM EXECUTION_OUTBOX WHERE MESSAGE_KEY = :message_key" in normalized:
            row = self._connection.outbox_by_key.get(str(values["message_key"]))
            self._fetched_one = None if row is None else self._connection.outbox_row_tuple(row)
            return

        raise AssertionError(f"unexpected SQL in fake cursor: {normalized}")

    def fetchone(self) -> tuple[object, ...] | None:
        return self._fetched_one

    def fetchall(self) -> list[tuple[object, ...]]:
        return list(self._fetched_all)


class _FakeOracleConnection:
    def __init__(self) -> None:
        self.jobs_by_id: dict[str, dict[str, object]] = {}
        self.jobs_by_key: dict[str, dict[str, object]] = {}
        self.outbox_by_key: dict[str, dict[str, object]] = {}
        self.commit_count = 0

    def cursor(self) -> _FakeOracleCursor:
        return _FakeOracleCursor(self)

    def commit(self) -> None:
        self.commit_count += 1

    @staticmethod
    def job_row_tuple(row: dict[str, object]) -> tuple[object, ...]:
        return (
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

    @staticmethod
    def outbox_row_tuple(row: dict[str, object]) -> tuple[object, ...]:
        return (
            row["MESSAGE_KEY"],
            row["MESSAGE_TYPE"],
            row["MESSAGE_PAYLOAD"],
            row["CREATED_AT"],
            row["PUBLISHED_AT"],
            row["RETRY_COUNT"],
        )


def test_oracle_execution_job_repository_round_trips_job_state() -> None:
    connection = _FakeOracleConnection()
    repository = OracleExecutionJobRepository(lambda: connection)

    created = repository.create_or_reuse(
        "key-1",
        "REVIEWER_ASSIST",
        {"title": "Paper"},
        job_id="job-1",
        intent_reference="101",
    )
    running = ExecutionStateMachine(max_attempts=2).mark_running(created)
    saved = repository.save(running)
    loaded = repository.get("job-1")

    assert saved.execution_state == "RUNNING"
    assert loaded is not None
    assert loaded.job_id == "job-1"
    assert loaded.intent_reference == "101"
    assert loaded.execution_state == "RUNNING"
    assert loaded.input_snapshot["title"] == "Paper"
    assert connection.commit_count == 2


def test_oracle_execution_job_repository_reuses_existing_idempotency_key() -> None:
    connection = _FakeOracleConnection()
    repository = OracleExecutionJobRepository(lambda: connection)

    first = repository.create_or_reuse(
        "key-1",
        "REVIEWER_ASSIST",
        {"title": "Paper"},
        job_id="job-1",
        intent_reference="101",
    )
    second = repository.create_or_reuse(
        "key-1",
        "REVIEWER_ASSIST",
        {"title": "Paper changed"},
        job_id="job-2",
        intent_reference="999",
    )

    assert second.job_id == first.job_id
    assert second.intent_reference == "101"
    assert len(connection.jobs_by_id) == 1


def test_oracle_execution_outbox_persists_pending_and_published_messages() -> None:
    connection = _FakeOracleConnection()
    outbox = OracleExecutionOutbox(lambda: connection)

    pending = outbox.enqueue(
        "analysis.requested",
        {"jobId": "job-1", "requestPayload": {"title": "Paper"}},
        message_id="msg-1",
    )
    published = outbox.mark_published("msg-1")

    assert pending.message_id == "msg-1"
    assert [message.message_id for message in outbox.pending()] == []
    assert published is not None
    assert published.published_at is not None
    assert connection.commit_count == 2
