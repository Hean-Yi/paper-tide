from app.agent_platform.domain import ExecutionJob
from app.agent_platform.handler_registry import AnalysisHandlerRegistry
from app.agent_platform.handlers.reviewer_assist import ReviewerAssistHandler
from app.agent_platform.provider_executor import ProviderExecutor


def test_reviewer_assist_handler_returns_projection_payload() -> None:
    job = ExecutionJob.new(
        "job-1",
        "key-1",
        "REVIEWER_ASSIST",
        {
            "title": "Robust Review Systems",
            "abstract": "A paper about review workflow.",
            "keywords": ["review"],
            "reviewerAssist": {"assignmentId": 77, "roundId": 8, "manuscriptId": 10, "versionId": 11},
        },
    )

    result = ReviewerAssistHandler().execute(job, ProviderExecutor())

    assert result["summary_projection"]["businessStatus"] == "AVAILABLE"
    assert result["summary_projection"]["summary"]
    assert result["raw_result"]["taskType"] == "REVIEW_ASSIST_ANALYSIS"
    assert "recommendation" not in result["redacted_result"]


def test_handler_registry_selects_reviewer_assist_handler() -> None:
    handler = AnalysisHandlerRegistry().get("REVIEWER_ASSIST")

    assert isinstance(handler, ReviewerAssistHandler)
