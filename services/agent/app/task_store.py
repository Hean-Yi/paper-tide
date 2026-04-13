from __future__ import annotations

import hashlib
import json
from threading import Lock
from typing import Any
from uuid import uuid4

from app.models import TaskRecord

WORKFLOW_REVISION = "task8-v1"


def canonical_json(value: dict[str, Any] | None) -> str:
    return json.dumps(value or {}, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def input_fingerprint(request_payload: dict[str, Any] | None) -> str:
    return hashlib.sha256(canonical_json(request_payload).encode("utf-8")).hexdigest()


def build_cache_key(
    *,
    task_type: str,
    manuscript_id: int,
    version_id: int,
    round_id: int | None,
    request_payload: dict[str, Any] | None,
    workflow_revision: str = WORKFLOW_REVISION,
) -> str:
    fingerprint = input_fingerprint(request_payload)
    return f"{task_type}:{manuscript_id}:{version_id}:{round_id}:{workflow_revision}:{fingerprint}"


class TaskStore:
    def __init__(self) -> None:
        self._tasks: dict[str, TaskRecord] = {}
        self._cache_index: dict[str, str] = {}
        self._lock = Lock()

    def create_or_reuse_task(
        self,
        *,
        task_type: str,
        manuscript_id: int,
        version_id: int,
        round_id: int | None,
        request_payload: dict[str, Any] | None,
        force: bool = False,
    ) -> TaskRecord:
        fingerprint = input_fingerprint(request_payload)
        cache_key = build_cache_key(
            task_type=task_type,
            manuscript_id=manuscript_id,
            version_id=version_id,
            round_id=round_id,
            request_payload=request_payload,
        )
        with self._lock:
            if not force:
                cached_task_id = self._cache_index.get(cache_key)
                cached_task = self._tasks.get(cached_task_id) if cached_task_id else None
                if cached_task is not None and cached_task.status in {"PENDING", "PROCESSING", "SUCCESS", "FAILED"}:
                    return cached_task

            task = TaskRecord(
                task_id=str(uuid4()),
                task_type=task_type,
                manuscript_id=manuscript_id,
                version_id=version_id,
                round_id=round_id,
                request_payload=request_payload,
                force=force,
                input_fingerprint=fingerprint,
                cache_key=cache_key,
                workflow_revision=WORKFLOW_REVISION,
            )
            self._tasks[task.task_id] = task
            self._cache_index[cache_key] = task.task_id
            return task

    def get_task(self, task_id: str) -> TaskRecord | None:
        with self._lock:
            return self._tasks.get(task_id)

    def get_result(self, task_id: str) -> dict | None:
        with self._lock:
            task = self._tasks.get(task_id)
            return None if task is None else task.result

    def update_status(self, task_id: str, *, status: str, step: str, error: str | None = None) -> TaskRecord | None:
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                return None
            task.status = status
            task.step = step
            task.error = error
            return task

    def mark_execution_started(self, task_id: str) -> bool:
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None or task.execution_started:
                return False
            task.execution_started = True
            return True

    def set_result(self, task_id: str, result: dict, *, step: str = "completed") -> TaskRecord | None:
        with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                return None
            task.result = result
            task.status = "SUCCESS"
            task.step = step
            task.error = None
            return task
