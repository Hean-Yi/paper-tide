from app.agent_platform.domain import ExecutionJob
from app.agent_platform.handler_registry import AnalysisHandlerRegistry
from app.agent_platform.handlers.screening import ScreeningAnalysisHandler
from app.agent_platform.provider_executor import ProviderExecutor


def test_screening_analysis_handler_returns_projection_payload() -> None:
    job = ExecutionJob.new(
        "job-3",
        "key-3",
        "SCREENING",
        {
            "title": "Screening Seed",
            "abstract": "A paper ready for chair screening.",
            "keywords": ["workflow", "screening"],
            "screening": {"manuscriptId": 11, "versionId": 21, "pdfFileSize": 512},
        },
    )

    result = ScreeningAnalysisHandler().execute(job, ProviderExecutor())

    assert result["summary_projection"]["businessStatus"] == "AVAILABLE"
    assert result["summary_projection"]["summary"]
    assert result["summary_projection"]["scopeFit"] == "FIT"
    assert result["raw_result"]["taskType"] == "SCREENING_ANALYSIS"
    assert result["redacted_result"]["taskType"] == "SCREENING_ANALYSIS"


def test_handler_registry_selects_screening_analysis_handler() -> None:
    handler = AnalysisHandlerRegistry().get("SCREENING")

    assert isinstance(handler, ScreeningAnalysisHandler)
