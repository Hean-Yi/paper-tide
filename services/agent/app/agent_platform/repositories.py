from __future__ import annotations

import json
from collections.abc import Callable
from datetime import UTC, datetime
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
        intent_reference: str | None = None,
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
        intent_reference: str | None = None,
    ) -> ExecutionJob:
        with self._lock:
            existing_job_id = self._by_idempotency_key.get(idempotency_key)
            if existing_job_id is not None:
                return self._jobs[existing_job_id]

            job = ExecutionJob.new(job_id, idempotency_key, analysis_type, input_snapshot, intent_reference)
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


class OracleExecutionJobRepository:
    def __init__(self, connection_factory: Callable[[], Any]) -> None:
        self._connection_factory = connection_factory

    def create_or_reuse(
        self,
        idempotency_key: str,
        analysis_type: str,
        input_snapshot: dict[str, Any],
        job_id: str | None = None,
        intent_reference: str | None = None,
    ) -> ExecutionJob:
        if intent_reference is None:
            raise ValueError("intent_reference is required for durable execution jobs")

        connection = self._connection_factory()
        try:
            existing = self._get_by_idempotency_key_with_connection(connection, idempotency_key)
            if existing is not None:
                return existing

            job = ExecutionJob.new(job_id, idempotency_key, analysis_type, input_snapshot, intent_reference)
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO EXECUTION_JOB (
                      JOB_ID,
                      INTENT_ID,
                      IDEMPOTENCY_KEY,
                      ANALYSIS_TYPE,
                      EXECUTION_STATE,
                      INPUT_SNAPSHOT,
                      FAILURE_REASON,
                      ATTEMPT_COUNT,
                      CREATED_AT
                    ) VALUES (
                      :job_id,
                      :intent_id,
                      :idempotency_key,
                      :analysis_type,
                      :execution_state,
                      :input_snapshot,
                      :failure_reason,
                      :attempt_count,
                      :created_at
                    )
                    """,
                    {
                        "job_id": job.job_id,
                        "intent_id": int(intent_reference),
                        "idempotency_key": job.idempotency_key,
                        "analysis_type": job.analysis_type,
                        "execution_state": job.execution_state,
                        "input_snapshot": _serialize_json(job.input_snapshot_copy()),
                        "failure_reason": job.failure_reason,
                        "attempt_count": job.attempt_count,
                        "created_at": job.created_at,
                    },
                )
            connection.commit()
            return job
        except Exception:
            rollback = getattr(connection, "rollback", None)
            if callable(rollback):
                rollback()
            existing = self._get_by_idempotency_key_with_connection(connection, idempotency_key)
            if existing is not None:
                return existing
            raise
        finally:
            _close_quietly(connection)

    def save(self, job: ExecutionJob) -> ExecutionJob:
        connection = self._connection_factory()
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE EXECUTION_JOB
                       SET EXECUTION_STATE = :execution_state,
                           FAILURE_REASON = :failure_reason,
                           ATTEMPT_COUNT = :attempt_count
                     WHERE JOB_ID = :job_id
                    """,
                    {
                        "job_id": job.job_id,
                        "execution_state": job.execution_state,
                        "failure_reason": job.failure_reason,
                        "attempt_count": job.attempt_count,
                    },
                )
            connection.commit()
            return job
        finally:
            _close_quietly(connection)

    def get(self, job_id: str) -> ExecutionJob | None:
        connection = self._connection_factory()
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT JOB_ID,
                           IDEMPOTENCY_KEY,
                           ANALYSIS_TYPE,
                           EXECUTION_STATE,
                           INPUT_SNAPSHOT,
                           FAILURE_REASON,
                           ATTEMPT_COUNT,
                           INTENT_ID,
                           CREATED_AT
                      FROM EXECUTION_JOB
                     WHERE JOB_ID = :job_id
                    """,
                    {"job_id": job_id},
                )
                row = cursor.fetchone()
            return _job_from_row(row)
        finally:
            _close_quietly(connection)

    def get_by_idempotency_key(self, idempotency_key: str) -> ExecutionJob | None:
        connection = self._connection_factory()
        try:
            return self._get_by_idempotency_key_with_connection(connection, idempotency_key)
        finally:
            _close_quietly(connection)

    def _get_by_idempotency_key_with_connection(self, connection: Any, idempotency_key: str) -> ExecutionJob | None:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT JOB_ID,
                       IDEMPOTENCY_KEY,
                       ANALYSIS_TYPE,
                       EXECUTION_STATE,
                       INPUT_SNAPSHOT,
                       FAILURE_REASON,
                       ATTEMPT_COUNT,
                       INTENT_ID,
                       CREATED_AT
                  FROM EXECUTION_JOB
                 WHERE IDEMPOTENCY_KEY = :idempotency_key
                """,
                {"idempotency_key": idempotency_key},
            )
            row = cursor.fetchone()
        return _job_from_row(row)


def _job_from_row(row: tuple[Any, ...] | None) -> ExecutionJob | None:
    if row is None:
        return None
    created_at = row[8] if isinstance(row[8], datetime) else datetime.now(UTC)
    return ExecutionJob(
        job_id=str(row[0]),
        idempotency_key=str(row[1]),
        analysis_type=str(row[2]),
        execution_state=str(row[3]),
        input_snapshot=ExecutionJob.new(
            str(row[0]),
            str(row[1]),
            str(row[2]),
            _deserialize_json(str(row[4])),
            str(row[7]),
        ).input_snapshot,
        failure_reason=None if row[5] is None else str(row[5]),
        attempt_count=int(row[6]),
        intent_reference=str(row[7]),
        created_at=created_at,
    )


def _serialize_json(value: dict[str, Any]) -> str:
    return json.dumps(value, separators=(",", ":"), sort_keys=True)


def _deserialize_json(value: str) -> dict[str, Any]:
    return json.loads(value)


def _close_quietly(connection: Any) -> None:
    close = getattr(connection, "close", None)
    if callable(close):
        close()
