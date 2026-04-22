import pytest

from app.agent_platform.domain import ExecutionJob
from app.agent_platform.repositories import InMemoryExecutionJobRepository
from app.agent_platform.state_machine import ExecutionStateMachine


def test_retryable_failure_transitions_to_dead_letter_after_limit() -> None:
    job = ExecutionJob.new("job-1", "key-1", "REVIEWER_ASSIST", {"title": "Paper"})

    machine = ExecutionStateMachine(max_attempts=2)
    machine.mark_running(job)
    machine.mark_retryable_failure(job, "provider timeout")
    machine.mark_running(job)
    machine.mark_retryable_failure(job, "provider timeout")

    assert job.execution_state == "DEAD_LETTERED"


def test_duplicate_intake_reuses_existing_job_id() -> None:
    repo = InMemoryExecutionJobRepository()

    first = repo.create_or_reuse("key-1", "REVIEWER_ASSIST", {"title": "Paper"})
    second = repo.create_or_reuse("key-1", "REVIEWER_ASSIST", {"title": "Paper"})

    assert second.job_id == first.job_id


def test_succeeded_job_cannot_be_revived_to_running() -> None:
    job = ExecutionJob.new("job-1", "key-1", "REVIEWER_ASSIST", {"title": "Paper"})
    machine = ExecutionStateMachine(max_attempts=2)
    machine.mark_running(job)
    machine.mark_succeeded(job)

    with pytest.raises(RuntimeError, match="terminal state SUCCEEDED"):
        machine.mark_running(job)

    assert job.execution_state == "SUCCEEDED"


def test_terminal_failure_job_cannot_be_overwritten_with_success() -> None:
    job = ExecutionJob.new("job-1", "key-1", "REVIEWER_ASSIST", {"title": "Paper"})
    machine = ExecutionStateMachine(max_attempts=2)
    machine.mark_running(job)
    machine.mark_terminal_failure(job, "bad input")

    with pytest.raises(RuntimeError, match="terminal state FAILED_TERMINAL"):
        machine.mark_succeeded(job)

    assert job.execution_state == "FAILED_TERMINAL"
