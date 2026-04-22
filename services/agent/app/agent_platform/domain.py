from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any, Mapping
from uuid import uuid4

from app.agent_platform.snapshots import freeze_json_mapping, thaw_json_mapping


@dataclass(frozen=True, slots=True)
class ExecutionJob:
    job_id: str
    idempotency_key: str
    analysis_type: str
    input_snapshot: Mapping[str, Any]
    execution_state: str
    attempt_count: int = 0
    failure_reason: str | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    @classmethod
    def new(
        cls,
        job_id: str | None,
        idempotency_key: str,
        analysis_type: str,
        input_snapshot: dict[str, Any],
    ) -> "ExecutionJob":
        return cls(
            job_id=job_id or str(uuid4()),
            idempotency_key=idempotency_key,
            analysis_type=analysis_type,
            input_snapshot=freeze_json_mapping(input_snapshot),
            execution_state="QUEUED",
        )

    def input_snapshot_copy(self) -> dict[str, Any]:
        return thaw_json_mapping(self.input_snapshot)
