from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from pydantic import BaseModel, Field


TASK_TYPES = {
    "SCREENING_ANALYSIS",
    "REVIEW_ASSIST_ANALYSIS",
    "DECISION_CONFLICT_ANALYSIS",
}


class CreateTaskRequest(BaseModel):
    task_type: str = Field(alias="taskType")
    manuscript_id: int = Field(alias="manuscriptId")
    version_id: int = Field(alias="versionId")
    round_id: int | None = Field(default=None, alias="roundId")
    request_payload: dict[str, Any] | None = Field(default=None, alias="requestPayload")
    force: bool = False


class TaskSummaryResponse(BaseModel):
    task_id: str = Field(alias="taskId")
    status: str
    step: str


class TaskStatusResponse(TaskSummaryResponse):
    task_type: str = Field(alias="taskType")
    error: str | None = None


@dataclass(slots=True)
class TaskRecord:
    task_id: str
    task_type: str
    manuscript_id: int
    version_id: int
    round_id: int | None
    request_payload: dict[str, Any] | None = None
    force: bool = False
    status: str = "PENDING"
    step: str = "queued"
    error: str | None = None
    result: dict[str, Any] | None = None
    input_fingerprint: str = ""
    cache_key: str = ""
    workflow_revision: str = "task8-v1"
    execution_started: bool = False
