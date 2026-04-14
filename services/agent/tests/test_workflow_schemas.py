import pytest
from pydantic import ValidationError

from app.redaction import redact_result
from app.workflows.router import build_initial_state, select_workflow
from app.workflows.schemas import (
    ConflictAnalysisResult,
    ReviewAssistResult,
    ScreeningAnalysisResult,
)


def test_review_assist_schema_rejects_score_or_recommendation_output() -> None:
    with pytest.raises(ValidationError):
        ReviewAssistResult.model_validate(
            {
                "taskType": "REVIEW_ASSIST_ANALYSIS",
                "manuscriptId": "101",
                "versionId": "201",
                "status": "SUCCESS",
                "paperSummary": "Concise summary",
                "claimedContributions": ["A system"],
                "methodChecklist": ["Verify method assumptions"],
                "experimentChecklist": ["Check baselines"],
                "evidenceToVerify": ["Dataset split"],
                "potentialWeaknesses": ["Limited ablation"],
                "questionsForReviewer": ["Is the baseline fair?"],
                "blindReviewRisks": [],
                "recommendation": "Weak accept",
                "confidence": 0.7,
            }
        )


def test_review_assist_schema_accepts_checklist_only_output() -> None:
    result = ReviewAssistResult.model_validate(
        {
            "taskType": "REVIEW_ASSIST_ANALYSIS",
            "manuscriptId": "101",
            "versionId": "201",
            "status": "SUCCESS",
            "paperSummary": "Concise summary",
            "claimedContributions": ["A system"],
            "methodChecklist": ["Verify method assumptions"],
            "experimentChecklist": ["Check baselines"],
            "evidenceToVerify": ["Dataset split"],
            "potentialWeaknesses": ["Limited ablation"],
            "questionsForReviewer": ["Is the baseline fair?"],
            "blindReviewRisks": [],
            "confidence": 0.7,
        }
    )

    assert result.paperSummary == "Concise summary"
    assert result.methodChecklist == ["Verify method assumptions"]


def test_screening_schema_has_scope_fit() -> None:
    with pytest.raises(ValidationError):
        ScreeningAnalysisResult.model_validate(
            {
                "taskType": "SCREENING_ANALYSIS",
                "manuscriptId": "101",
                "versionId": "201",
                "status": "SUCCESS",
                "topicCategory": "Systems",
                "formatRisks": [],
                "blindnessRisks": [],
                "screeningSummary": "In scope",
                "confidence": 0.8,
            }
        )


def test_conflict_schema_has_consensus_and_conflicts() -> None:
    with pytest.raises(ValidationError):
        ConflictAnalysisResult.model_validate(
            {
                "taskType": "DECISION_CONFLICT_ANALYSIS",
                "manuscriptId": "101",
                "versionId": "201",
                "status": "SUCCESS",
                "highRiskIssues": [],
                "decisionSummary": "Reviews agree on method weakness",
                "confidence": 0.6,
            }
        )


def test_decision_conflict_requires_round_id() -> None:
    task = {
        "task_id": "task-1",
        "task_type": "DECISION_CONFLICT_ANALYSIS",
        "manuscript_id": 101,
        "version_id": 201,
        "round_id": None,
        "request_payload": {},
    }

    with pytest.raises(ValueError, match="roundId is required"):
        build_initial_state(task)


def test_router_selects_graph_for_each_task_type() -> None:
    for task_type in (
        "SCREENING_ANALYSIS",
        "REVIEW_ASSIST_ANALYSIS",
        "DECISION_CONFLICT_ANALYSIS",
    ):
        workflow = select_workflow(task_type)
        assert hasattr(workflow, "invoke")


def test_redaction_sanitizes_identity_clues() -> None:
    raw_result = {
        "taskType": "SCREENING_ANALYSIS",
        "manuscriptId": "101",
        "versionId": "201",
        "status": "SUCCESS",
        "topicCategory": "Systems",
        "scopeFit": "FIT",
        "formatRisks": [],
        "blindnessRisks": ["Acknowledgements mention Example University"],
        "screeningSummary": "The authors from Example University disclose grant ABC-123.",
        "confidence": 0.8,
    }

    redacted = redact_result("SCREENING_ANALYSIS", raw_result)

    assert redacted["screeningSummary"] == "Reviewer-safe summary unavailable due to redaction."
    assert redacted["blindnessRisks"] == ["Reviewer-safe summary unavailable due to redaction."]


def test_review_assist_redaction_removes_forbidden_fields() -> None:
    raw_result = {
        "taskType": "REVIEW_ASSIST_ANALYSIS",
        "manuscriptId": "101",
        "versionId": "201",
        "status": "SUCCESS",
        "paperSummary": "Concise summary",
        "claimedContributions": ["A system"],
        "methodChecklist": ["Verify method assumptions"],
        "experimentChecklist": ["Check baselines"],
        "evidenceToVerify": ["Dataset split"],
        "potentialWeaknesses": ["Limited ablation"],
        "questionsForReviewer": ["Is the baseline fair?"],
        "blindReviewRisks": ["Acknowledgements mention Example University"],
        "recommendation": "accept",
        "overallScore": 5,
        "fullReviewText": "This is a complete review.",
        "confidence": 0.7,
    }

    redacted = redact_result("REVIEW_ASSIST_ANALYSIS", raw_result)

    assert "recommendation" not in redacted
    assert "overallScore" not in redacted
    assert "fullReviewText" not in redacted
    assert redacted["blindReviewRisks"] == ["Reviewer-safe summary unavailable due to redaction."]
