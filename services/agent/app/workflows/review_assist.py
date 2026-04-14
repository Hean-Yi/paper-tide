from __future__ import annotations

from typing import Any

from app.redaction import redact_result
from app.workflows.llm import request_json_from_model
from app.workflows.schemas import validate_result


def review_assist_assess(state: dict[str, Any]) -> dict[str, Any]:
    paper = state.get("paper_understanding") or {}
    fallback = {
        "taskType": "REVIEW_ASSIST_ANALYSIS",
        "manuscriptId": str(state["manuscript_id"]),
        "versionId": str(state["version_id"]),
        "status": "SUCCESS",
        "paperSummary": paper.get("abstractSummary") or "No abstract summary provided.",
        "claimedContributions": paper.get("contributionClaims") or ["Identify and verify the claimed contribution."],
        "methodChecklist": ["Check whether assumptions, baselines, and implementation details support the method claims."],
        "experimentChecklist": ["Verify datasets, metrics, baselines, and ablation coverage before scoring the work."],
        "evidenceToVerify": ["Match each major claim to a table, figure, experiment, or cited prior result."],
        "potentialWeaknesses": ["Look for unsupported claims, missing ablations, and unclear limitations."],
        "questionsForReviewer": ["What evidence would change your assessment of the paper?"],
        "blindReviewRisks": paper.get("possibleBlindnessRisks", []),
        "confidence": 0.5,
    }
    raw_result = request_json_from_model(
        state,
        instruction=(
            "Create checklist-only reviewer assistance from this paper metadata using the REVIEW_ASSIST_ANALYSIS "
            "schema. Do not include scores, recommendations, decisions, or complete review text: "
            f"{paper}"
        ),
        fallback=fallback,
    )
    return {"raw_result": raw_result, "step": "analyzing"}


def validate_review_assist_result(state: dict[str, Any]) -> dict[str, Any]:
    return {"raw_result": validate_result("REVIEW_ASSIST_ANALYSIS", state["raw_result"]), "step": "validating"}


def redact_review_assist_result(state: dict[str, Any]) -> dict[str, Any]:
    return {"redacted_result": redact_result("REVIEW_ASSIST_ANALYSIS", state["raw_result"]), "step": "summarizing"}
