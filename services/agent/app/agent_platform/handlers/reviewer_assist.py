from __future__ import annotations

from typing import Any

from app.agent_platform.domain import ExecutionJob
from app.agent_platform.handlers.base import ProviderExecutor
from app.redaction import redact_result
from app.workflows.paper_understanding import build_paper_understanding
from app.workflows.schemas import validate_result


class ReviewerAssistHandler:
    analysis_type = "REVIEWER_ASSIST"

    def execute(self, job: ExecutionJob, provider_executor: ProviderExecutor) -> dict[str, Any]:
        payload = job.input_snapshot_copy()
        assist_context = payload.get("reviewerAssist") or {}
        understanding = build_paper_understanding(
            {
                "request_payload": payload,
                "manuscript_id": assist_context.get("manuscriptId", "0"),
                "version_id": assist_context.get("versionId", "0"),
            }
        )["paper_understanding"]
        raw_result = validate_result("REVIEW_ASSIST_ANALYSIS", provider_executor.run_reviewer_assist(understanding))
        redacted_result = redact_result("REVIEW_ASSIST_ANALYSIS", raw_result)
        return {
            "raw_result": raw_result,
            "redacted_result": redacted_result,
            "summary_projection": {
                "businessStatus": "AVAILABLE",
                "summary": raw_result["paperSummary"],
            },
        }
