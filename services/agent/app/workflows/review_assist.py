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
        "summary": paper.get("abstractSummary") or "No abstract summary provided.",
        "novelty": {"analysis": "Assess novelty from the submitted context.", "score": 3},
        "methodology": {"analysis": "Assess methodology from the submitted context.", "score": 3},
        "writing": {"analysis": "Assess writing clarity from the submitted context.", "score": 3},
        "risks": paper.get("possibleBlindnessRisks", []),
        "finalSuggestion": "Use as reviewer assistance only.",
        "confidence": 0.5,
    }
    raw_result = request_json_from_model(
        state,
        instruction=(
            "Create reviewer assistance from this paper metadata using the REVIEW_ASSIST_ANALYSIS schema: "
            f"{paper}"
        ),
        fallback=fallback,
    )
    return {"raw_result": raw_result, "step": "analyzing"}


def validate_review_assist_result(state: dict[str, Any]) -> dict[str, Any]:
    return {"raw_result": validate_result("REVIEW_ASSIST_ANALYSIS", state["raw_result"]), "step": "validating"}


def redact_review_assist_result(state: dict[str, Any]) -> dict[str, Any]:
    return {"redacted_result": redact_result("REVIEW_ASSIST_ANALYSIS", state["raw_result"]), "step": "summarizing"}
