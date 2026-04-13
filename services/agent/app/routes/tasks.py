from __future__ import annotations

import hmac
import json
import os
from collections.abc import Callable
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status
from pydantic import ValidationError

from app.models import (
    TASK_TYPES,
    CreateTaskRequest,
    TaskStatusResponse,
    TaskSummaryResponse,
)
from app.pdf_tools import PdfExtractionError, build_pdf_payload
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
    async def create_task(http_request: Request, _: None = Depends(require_api_key)) -> TaskSummaryResponse:
        request = await _parse_create_task_request(http_request)
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


async def _parse_create_task_request(http_request: Request) -> CreateTaskRequest:
    content_type = http_request.headers.get("content-type", "")
    if content_type.startswith("multipart/form-data"):
        return await _parse_multipart_create_task_request(http_request)

    try:
        body = await http_request.json()
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid JSON body") from exc
    return _validate_create_task_request(body)


async def _parse_multipart_create_task_request(http_request: Request) -> CreateTaskRequest:
    form = await http_request.form()
    metadata_value = form.get("metadata")
    upload = form.get("file")
    if not isinstance(metadata_value, str) or upload is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="metadata and file are required")

    try:
        metadata = json.loads(metadata_value)
    except json.JSONDecodeError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="metadata must be valid JSON") from exc

    request = _validate_create_task_request(metadata)
    file_name = getattr(upload, "filename", "") or "upload.pdf"
    content_type = getattr(upload, "content_type", None)
    if content_type != "application/pdf" and not file_name.lower().endswith(".pdf"):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Only PDF uploads are supported")

    pdf_bytes = await upload.read()
    if not pdf_bytes:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="PDF upload is empty")

    try:
        normalized_payload = build_pdf_payload(
            metadata_payload=request.request_payload,
            pdf_bytes=pdf_bytes,
            file_name=file_name,
            file_size=len(pdf_bytes),
        )
    except PdfExtractionError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    return CreateTaskRequest(
        taskType=request.task_type,
        manuscriptId=request.manuscript_id,
        versionId=request.version_id,
        roundId=request.round_id,
        requestPayload=normalized_payload,
        force=request.force,
    )


def _validate_create_task_request(body: dict[str, Any]) -> CreateTaskRequest:
    try:
        return CreateTaskRequest(**body)
    except ValidationError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=exc.errors()) from exc
