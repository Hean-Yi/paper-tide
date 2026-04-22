import pytest

from app.agent_platform.domain import ExecutionJob
from app.agent_platform.messages import AnalysisRequestedMessage
from app.agent_platform.outbox import InMemoryExecutionOutbox
from app.agent_platform.repositories import InMemoryExecutionJobRepository
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
