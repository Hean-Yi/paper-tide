from __future__ import annotations

from typing import Any, TypedDict


class PaperUnderstanding(TypedDict):
    manuscriptId: str
    versionId: str
    title: str
    abstractSummary: str
    keywords: list[str]
    researchProblem: str
    claimedContributions: list[str]
    methodSummary: str
    experimentSummary: str
    mainResults: list[str]
    limitationsMentioned: list[str]
    citationSignals: list[str]
    possibleBlindnessRisks: list[str]
    pdfText: str
    sections: dict[str, str]


def build_paper_understanding(state: dict[str, Any]) -> dict[str, Any]:
    payload = state.get("request_payload") or {}
    understanding: PaperUnderstanding = {
        "manuscriptId": str(state.get("manuscript_id", "")),
        "versionId": str(state.get("version_id", "")),
        "title": str(payload.get("title", "")),
        "abstractSummary": str(payload.get("abstractSummary") or payload.get("abstract") or ""),
        "keywords": list(payload.get("keywords") or []),
        "researchProblem": str(payload.get("researchProblem", "")),
        "claimedContributions": list(payload.get("claimedContributions") or []),
        "methodSummary": str(payload.get("methodSummary", "")),
        "experimentSummary": str(payload.get("experimentSummary", "")),
        "mainResults": list(payload.get("mainResults") or []),
        "limitationsMentioned": list(payload.get("limitationsMentioned") or []),
        "citationSignals": list(payload.get("citationSignals") or []),
        "possibleBlindnessRisks": list(payload.get("possibleBlindnessRisks") or []),
        "pdfText": str(payload.get("pdfText", "")),
        "sections": dict(payload.get("sections") or {}),
    }
    return {"paper_understanding": understanding, "step": "understanding"}
