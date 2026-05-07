from __future__ import annotations

from typing import Any

from app.agent_platform.domain import ExecutionJob
from app.agent_platform.handlers.base import ProviderExecutor
from app.redaction import redact_result
from app.agent_platform.schemas import validate_result


class ReviewerAssignmentAssistHandler:
    analysis_type = "REVIEWER_ASSIGNMENT_ASSIST"

    def execute(self, job: ExecutionJob, provider_executor: ProviderExecutor) -> dict[str, Any]:
        payload = job.input_snapshot_copy()
        raw_result = validate_result(
            "REVIEWER_ASSIGNMENT_ASSIST",
            provider_executor.run_reviewer_assignment_assist(payload),
        )
        redacted_result = redact_result("REVIEWER_ASSIGNMENT_ASSIST", raw_result)
        return {
            "raw_result": raw_result,
            "redacted_result": redacted_result,
            "summary_projection": {
                "businessStatus": "AVAILABLE",
                "summary": raw_result["assignmentSummary"],
                "rankedCandidates": raw_result["rankedCandidates"],
            },
        }
