from __future__ import annotations

from typing import Any

from app.agent_platform.domain import ExecutionJob
from app.agent_platform.handlers.base import ProviderExecutor
from app.redaction import redact_result
from app.workflows.schemas import validate_result


class ConflictAnalysisHandler:
    analysis_type = "CONFLICT_ANALYSIS"

    def execute(self, job: ExecutionJob, provider_executor: ProviderExecutor) -> dict[str, Any]:
        payload = job.input_snapshot_copy()
        raw_result = validate_result(
            "DECISION_CONFLICT_ANALYSIS",
            provider_executor.run_conflict_analysis(payload),
        )
        redacted_result = redact_result("DECISION_CONFLICT_ANALYSIS", raw_result)
        return {
            "raw_result": raw_result,
            "redacted_result": redacted_result,
            "summary_projection": {
                "businessStatus": "AVAILABLE",
                "summary": raw_result["decisionSummary"],
                "conflictPoints": raw_result["conflictPoints"],
            },
        }
