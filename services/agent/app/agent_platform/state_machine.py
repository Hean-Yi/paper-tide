from __future__ import annotations

from app.agent_platform.domain import ExecutionJob


class ExecutionStateMachine:
    _RUNNABLE_STATES = {"QUEUED", "FAILED_RETRYABLE"}
    _ACTIVE_STATE = "RUNNING"
    _TERMINAL_STATES = {"SUCCEEDED", "FAILED_TERMINAL", "DEAD_LETTERED"}

    def __init__(self, max_attempts: int) -> None:
        if max_attempts < 1:
            raise ValueError("max_attempts must be at least 1")
        self._max_attempts = max_attempts

    def mark_running(self, job: ExecutionJob) -> None:
        self._require_state(job, self._RUNNABLE_STATES, "mark_running")
        job.attempt_count += 1
        job.execution_state = self._ACTIVE_STATE
        job.failure_reason = None

    def mark_retryable_failure(self, job: ExecutionJob, reason: str) -> None:
        self._require_state(job, {self._ACTIVE_STATE}, "mark_retryable_failure")
        job.failure_reason = reason
        job.execution_state = "DEAD_LETTERED" if job.attempt_count >= self._max_attempts else "FAILED_RETRYABLE"

    def mark_succeeded(self, job: ExecutionJob) -> None:
        self._require_state(job, {self._ACTIVE_STATE}, "mark_succeeded")
        job.execution_state = "SUCCEEDED"
        job.failure_reason = None

    def mark_terminal_failure(self, job: ExecutionJob, reason: str) -> None:
        self._require_state(job, {self._ACTIVE_STATE}, "mark_terminal_failure")
        job.failure_reason = reason
        job.execution_state = "FAILED_TERMINAL"

    def _require_state(self, job: ExecutionJob, allowed_states: set[str], action: str) -> None:
        if job.execution_state in self._TERMINAL_STATES:
            raise RuntimeError(f"cannot {action} from terminal state {job.execution_state}")
        if job.execution_state not in allowed_states:
            raise RuntimeError(f"cannot {action} from state {job.execution_state}")
