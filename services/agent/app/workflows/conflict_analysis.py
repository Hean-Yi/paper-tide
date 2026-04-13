from __future__ import annotations

from typing import Any

from app.redaction import redact_result
from app.workflows.llm import request_json_from_model
from app.workflows.schemas import validate_result


def conflict_summarize(state: dict[str, Any]) -> dict[str, Any]:
    payload = state.get("request_payload") or {}
    fallback = {
        "taskType": "DECISION_CONFLICT_ANALYSIS",
        "manuscriptId": str(state["manuscript_id"]),
        "versionId": str(state["version_id"]),
        "status": "SUCCESS",
        "consensusPoints": list(payload.get("consensusPoints") or ["No consensus points provided."]),
        "conflictPoints": list(payload.get("conflictPoints") or payload.get("conflicts") or []),
        "highRiskIssues": list(payload.get("highRiskIssues") or []),
        "decisionSummary": str(payload.get("decisionSummary") or "Summarize submitted review differences."),
        "confidence": 0.5,
    }
    raw_result = request_json_from_model(
        state,
        instruction=(
            "Summarize review consensus and conflicts for this round using the DECISION_CONFLICT_ANALYSIS schema: "
            f"{payload}"
        ),
        fallback=fallback,
    )
    return {"raw_result": raw_result, "step": "analyzing"}


def validate_conflict_result(state: dict[str, Any]) -> dict[str, Any]:
    return {"raw_result": validate_result("DECISION_CONFLICT_ANALYSIS", state["raw_result"]), "step": "validating"}


def redact_conflict_result(state: dict[str, Any]) -> dict[str, Any]:
    return {"redacted_result": redact_result("DECISION_CONFLICT_ANALYSIS", state["raw_result"]), "step": "summarizing"}
