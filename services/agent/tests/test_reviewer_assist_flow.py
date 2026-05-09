from app.agent_platform.domain import ExecutionJob
from app.agent_platform.handler_registry import AnalysisHandlerRegistry
from app.agent_platform.handlers.reviewer_assist import ReviewerAssistHandler
from app.agent_platform.provider_executor import ProviderExecutor


class CapturingProviderExecutor:
    def __init__(self) -> None:
        self.paper: dict[str, object] | None = None

    def run_reviewer_assist(self, paper: dict[str, object]) -> dict[str, object]:
        self.paper = paper
        return {
            "taskType": "REVIEW_ASSIST_ANALYSIS",
            "manuscriptId": str(paper["manuscriptId"]),
            "versionId": str(paper["versionId"]),
            "status": "SUCCESS",
            "paperSummary": "论文摘要：基于真实正文生成。",
            "claimedContributions": ["核查贡献是否由正文支持。"],
            "methodChecklist": ["检查正文中的方法细节。"],
            "experimentChecklist": ["检查正文中的实验设置。"],
            "evidenceToVerify": ["核查正文证据。"],
            "potentialWeaknesses": ["关注正文未覆盖的问题。"],
            "questionsForReviewer": ["正文是否支撑主要结论？"],
            "blindReviewRisks": [],
            "confidence": 0.7,
        }


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


def test_reviewer_assist_handler_passes_pdf_text_to_provider() -> None:
    provider = CapturingProviderExecutor()
    job = ExecutionJob.new(
        "job-1",
        "key-1",
        "REVIEWER_ASSIST",
        {
            "title": "Robust Review Systems",
            "abstract": "A paper about review workflow.",
            "keywords": ["review"],
            "pdfText": "Real paper body includes evaluation details and method limitations.",
            "sections": {"fullText": "Real paper body includes evaluation details and method limitations."},
            "reviewerAssist": {"assignmentId": 77, "roundId": 8, "manuscriptId": 10, "versionId": 11},
        },
    )

    ReviewerAssistHandler().execute(job, provider)

    assert provider.paper is not None
    assert provider.paper["pdfText"] == "Real paper body includes evaluation details and method limitations."
    assert provider.paper["sections"] == {"fullText": "Real paper body includes evaluation details and method limitations."}


def test_handler_registry_selects_reviewer_assist_handler() -> None:
    handler = AnalysisHandlerRegistry().get("REVIEWER_ASSIST")

    assert isinstance(handler, ReviewerAssistHandler)
