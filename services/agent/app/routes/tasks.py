from __future__ import annotations

import hmac
import os
from collections.abc import Callable

from fastapi import APIRouter, Depends, Header, HTTPException, status

from app.models import (
    TASK_TYPES,
    CreateTaskRequest,
    TaskStatusResponse,
    TaskSummaryResponse,
)
from app.task_store import TaskStore


def build_tasks_router(
    task_store: TaskStore,
    *,
    start_task: Callable[[str], None] | None = None,
    require_internal_api_key: bool = True,
) -> APIRouter:
    router = APIRouter(prefix="/agent/tasks", tags=["tasks"])

    def require_api_key(x_agent_api_key: str | None = Header(default=None, alias="X-Agent-Api-Key")) -> None:
        if not require_internal_api_key:
            return
        expected = os.getenv("AGENT_INTERNAL_API_KEY")
        if not expected:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Agent internal API key is not configured",
            )
        if x_agent_api_key is None or not hmac.compare_digest(x_agent_api_key, expected):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid agent API key",
            )

    @router.post("", response_model=TaskSummaryResponse)
    def create_task(request: CreateTaskRequest, _: None = Depends(require_api_key)) -> TaskSummaryResponse:
        if request.task_type not in TASK_TYPES:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Unsupported task type",
            )

        task = task_store.create_or_reuse_task(
            task_type=request.task_type,
            manuscript_id=request.manuscript_id,
            version_id=request.version_id,
            round_id=request.round_id,
            request_payload=request.request_payload,
            force=request.force,
        )
        response = TaskSummaryResponse(taskId=task.task_id, status=task.status, step=task.step)
        if start_task is not None and task.status == "PENDING" and task_store.mark_execution_started(task.task_id):
            start_task(task.task_id)
        return response

    @router.get("/{task_id}", response_model=TaskStatusResponse)
    def get_task(task_id: str, _: None = Depends(require_api_key)) -> TaskStatusResponse:
        task = task_store.get_task(task_id)
        if task is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Task not found",
            )
        return TaskStatusResponse(
            taskId=task.task_id,
            taskType=task.task_type,
            status=task.status,
            step=task.step,
            error=task.error,
        )

    @router.get("/{task_id}/result")
    def get_result(task_id: str, _: None = Depends(require_api_key)) -> dict:
        task = task_store.get_task(task_id)
        if task is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Task not found",
            )
        if task.status != "SUCCESS" or task.result is None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Task result is not ready",
            )
        return task.result

    return router
