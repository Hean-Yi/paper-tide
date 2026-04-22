from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any, Mapping


@dataclass(slots=True)
class AnalysisRequestedMessage:
    idempotency_key: str
    analysis_type: str
    request_payload: dict[str, Any]
    job_id: str | None = None
    intent_reference: str | None = None
    trace_id: str | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any]) -> "AnalysisRequestedMessage":
        return cls(
            idempotency_key=value["idempotencyKey"],
            analysis_type=value["analysisType"],
            request_payload=deepcopy(value.get("requestPayload") or {}),
            job_id=value.get("jobId"),
            intent_reference=value.get("intentReference"),
            trace_id=value.get("traceId"),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "idempotencyKey": self.idempotency_key,
            "analysisType": self.analysis_type,
            "requestPayload": deepcopy(self.request_payload),
            "jobId": self.job_id,
            "intentReference": self.intent_reference,
            "traceId": self.trace_id,
            "createdAt": self.created_at.isoformat(),
        }
