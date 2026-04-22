from __future__ import annotations

from app.agent_platform.domain import ExecutionJob


class ExecutionStateMachine:
    def __init__(self, max_attempts: int) -> None:
        if max_attempts < 1:
            raise ValueError("max_attempts must be at least 1")
        self._max_attempts = max_attempts

    def mark_running(self, job: ExecutionJob) -> None:
        job.attempt_count += 1
        job.execution_state = "RUNNING"
        job.failure_reason = None

    def mark_retryable_failure(self, job: ExecutionJob, reason: str) -> None:
        job.failure_reason = reason
        job.execution_state = "DEAD_LETTERED" if job.attempt_count >= self._max_attempts else "FAILED_RETRYABLE"

    def mark_succeeded(self, job: ExecutionJob) -> None:
        job.execution_state = "SUCCEEDED"
        job.failure_reason = None

    def mark_terminal_failure(self, job: ExecutionJob, reason: str) -> None:
        job.failure_reason = reason
        job.execution_state = "FAILED_TERMINAL"
