import pytest
from pydantic import ValidationError

from app.redaction import redact_result
from app.agent_platform.handler_registry import AnalysisHandlerRegistry
from app.agent_platform.schemas import (
    ConflictAnalysisResult,
    ReviewerAssignmentAssistResult,
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


def test_screening_schema_rejects_extra_model_output() -> None:
    with pytest.raises(ValidationError):
        ScreeningAnalysisResult.model_validate(
            {
                "taskType": "SCREENING_ANALYSIS",
                "manuscriptId": "101",
                "versionId": "201",
                "status": "SUCCESS",
                "topicCategory": "Systems",
                "scopeFit": "FIT",
                "formatRisks": [],
                "blindnessRisks": [],
                "screeningSummary": "In scope",
                "confidence": 0.8,
                "authorGuess": "Example University",
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


def test_conflict_schema_rejects_extra_model_output() -> None:
    with pytest.raises(ValidationError):
        ConflictAnalysisResult.model_validate(
            {
                "taskType": "DECISION_CONFLICT_ANALYSIS",
                "manuscriptId": "101",
                "versionId": "201",
                "status": "SUCCESS",
                "consensusPoints": ["Clear contribution"],
                "conflictPoints": ["Evaluation disagreement"],
                "highRiskIssues": [],
                "decisionSummary": "Reviews disagree on evaluation.",
                "confidence": 0.6,
                "recommendation": "ACCEPT",
            }
        )


def test_reviewer_assignment_assist_schema_accepts_ranked_candidates() -> None:
    result = ReviewerAssignmentAssistResult.model_validate(
        {
            "taskType": "REVIEWER_ASSIGNMENT_ASSIST",
            "manuscriptId": "101",
            "versionId": "201",
            "status": "SUCCESS",
            "rankedCandidates": [
                {
                    "reviewerId": "1002",
                    "draftId": "501",
                    "rank": 1,
                    "rationale": "Strong bid and available load.",
                    "riskFlags": [],
                }
            ],
            "assignmentSummary": "One candidate is ready for chair confirmation.",
            "confidence": 0.6,
        }
    )

    assert result.rankedCandidates[0].reviewerId == "1002"


def test_agent_platform_registry_selects_handler_for_each_task_type() -> None:
    registry = AnalysisHandlerRegistry()

    for task_type in (
        "SCREENING",
        "REVIEWER_ASSIST",
        "CONFLICT_ANALYSIS",
        "REVIEWER_ASSIGNMENT_ASSIST",
    ):
        assert registry.get(task_type).analysis_type == task_type


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
