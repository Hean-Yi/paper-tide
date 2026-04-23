from app.agent_platform.domain import ExecutionJob
from app.agent_platform.handler_registry import AnalysisHandlerRegistry
from app.agent_platform.handlers.conflict_analysis import ConflictAnalysisHandler
from app.agent_platform.provider_executor import ProviderExecutor


def test_conflict_analysis_handler_returns_projection_payload() -> None:
    job = ExecutionJob.new(
        "job-2",
        "key-2",
        "CONFLICT_ANALYSIS",
        {
            "title": "Conflict Seed",
            "abstract": "A paper with mixed reviews.",
            "keywords": ["workflow", "conflict"],
            "conflictAnalysis": {"roundId": 31, "manuscriptId": 11, "versionId": 21},
            "reviewReports": [
                {
                    "recommendation": "MINOR_REVISION",
                    "strengths": "Clear contribution.",
                    "weaknesses": "Limited evaluation.",
                    "commentsToChair": "Borderline but promising.",
                }
            ],
        },
    )

    result = ConflictAnalysisHandler().execute(job, ProviderExecutor())

    assert result["summary_projection"]["businessStatus"] == "AVAILABLE"
    assert result["summary_projection"]["summary"]
    assert result["summary_projection"]["conflictPoints"]
    assert result["raw_result"]["taskType"] == "DECISION_CONFLICT_ANALYSIS"
    assert result["redacted_result"]["taskType"] == "DECISION_CONFLICT_ANALYSIS"


def test_handler_registry_selects_conflict_analysis_handler() -> None:
    handler = AnalysisHandlerRegistry().get("CONFLICT_ANALYSIS")

    assert isinstance(handler, ConflictAnalysisHandler)
