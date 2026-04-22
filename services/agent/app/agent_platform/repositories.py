from __future__ import annotations

from threading import Lock
from typing import Any, Protocol

from app.agent_platform.domain import ExecutionJob


class ExecutionJobRepository(Protocol):
    def create_or_reuse(
        self,
        idempotency_key: str,
        analysis_type: str,
        input_snapshot: dict[str, Any],
        job_id: str | None = None,
    ) -> ExecutionJob: ...

    def save(self, job: ExecutionJob) -> ExecutionJob: ...

    def get(self, job_id: str) -> ExecutionJob | None: ...

    def get_by_idempotency_key(self, idempotency_key: str) -> ExecutionJob | None: ...


class InMemoryExecutionJobRepository:
    def __init__(self) -> None:
        self._jobs: dict[str, ExecutionJob] = {}
        self._by_idempotency_key: dict[str, str] = {}
        self._lock = Lock()

    def create_or_reuse(
        self,
        idempotency_key: str,
        analysis_type: str,
        input_snapshot: dict[str, Any],
        job_id: str | None = None,
    ) -> ExecutionJob:
        with self._lock:
            existing_job_id = self._by_idempotency_key.get(idempotency_key)
            if existing_job_id is not None:
                return self._jobs[existing_job_id]

            job = ExecutionJob.new(job_id, idempotency_key, analysis_type, input_snapshot)
            return self._save_locked(job)

    def save(self, job: ExecutionJob) -> ExecutionJob:
        with self._lock:
            return self._save_locked(job)

    def _save_locked(self, job: ExecutionJob) -> ExecutionJob:
        self._jobs[job.job_id] = job
        self._by_idempotency_key[job.idempotency_key] = job.job_id
        return job

    def get(self, job_id: str) -> ExecutionJob | None:
        with self._lock:
            return self._jobs.get(job_id)

    def get_by_idempotency_key(self, idempotency_key: str) -> ExecutionJob | None:
        with self._lock:
            job_id = self._by_idempotency_key.get(idempotency_key)
            if job_id is None:
                return None
            return self._jobs.get(job_id)
