from __future__ import annotations

from typing import Any, TypedDict


class PaperUnderstanding(TypedDict):
    manuscriptId: str
    versionId: str
    title: str
    abstractSummary: str
    claimedContributions: list[str]
    methodSummary: str
    experimentSummary: str
    possibleBlindnessRisks: list[str]


def build_paper_understanding(state: dict[str, Any]) -> dict[str, Any]:
    payload = state.get("request_payload") or {}
    understanding: PaperUnderstanding = {
        "manuscriptId": str(state.get("manuscript_id", "")),
        "versionId": str(state.get("version_id", "")),
        "title": str(payload.get("title", "")),
        "abstractSummary": str(payload.get("abstractSummary") or payload.get("abstract") or ""),
        "claimedContributions": list(payload.get("claimedContributions") or []),
        "methodSummary": str(payload.get("methodSummary", "")),
        "experimentSummary": str(payload.get("experimentSummary", "")),
        "possibleBlindnessRisks": list(payload.get("possibleBlindnessRisks") or []),
    }
    return {"paper_understanding": understanding, "step": "understanding"}
