from __future__ import annotations

from typing import Any

from app.agent_platform.domain import ExecutionJob
from app.agent_platform.handlers.base import ProviderExecutor
from app.redaction import redact_result
from app.workflows.schemas import validate_result


class ScreeningAnalysisHandler:
    analysis_type = "SCREENING"

    def execute(self, job: ExecutionJob, provider_executor: ProviderExecutor) -> dict[str, Any]:
        payload = job.input_snapshot_copy()
        raw_result = validate_result(
            "SCREENING_ANALYSIS",
            provider_executor.run_screening(payload),
        )
        redacted_result = redact_result("SCREENING_ANALYSIS", raw_result)
        return {
            "raw_result": raw_result,
            "redacted_result": redacted_result,
            "summary_projection": {
                "businessStatus": "AVAILABLE",
                "summary": raw_result["screeningSummary"],
                "scopeFit": raw_result["scopeFit"],
                "formatRisks": raw_result["formatRisks"],
                "blindnessRisks": raw_result["blindnessRisks"],
            },
        }
