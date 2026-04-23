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
        if "requestPayload" not in value:
            raise ValueError("requestPayload is required")
        request_payload = value["requestPayload"]
        if request_payload is None:
            raise ValueError("requestPayload is required")
        if not isinstance(request_payload, Mapping):
            raise ValueError("requestPayload must be a mapping")

        return cls(
            idempotency_key=value["idempotencyKey"],
            analysis_type=value["analysisType"],
            request_payload=deepcopy(dict(request_payload)),
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


@dataclass(slots=True)
class AnalysisCompletedMessage:
    message_key: str
    intent_id: int
    job_id: str
    analysis_type: str
    business_status: str
    summary_projection: dict[str, Any]
    redacted_result: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "messageKey": self.message_key,
            "eventType": "analysis.completed",
            "intentId": self.intent_id,
            "jobId": self.job_id,
            "analysisType": self.analysis_type,
            "businessStatus": self.business_status,
            "summaryProjection": deepcopy(self.summary_projection),
            "redactedResult": deepcopy(self.redacted_result),
        }
