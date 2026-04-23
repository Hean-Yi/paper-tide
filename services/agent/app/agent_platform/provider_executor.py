from __future__ import annotations

from typing import Any


class ProviderExecutor:
    def run_reviewer_assist(self, paper: dict[str, Any]) -> dict[str, Any]:
        return {
            "taskType": "REVIEW_ASSIST_ANALYSIS",
            "manuscriptId": str(paper.get("manuscriptId", "")),
            "versionId": str(paper.get("versionId", "")),
            "status": "SUCCESS",
            "paperSummary": paper.get("abstractSummary") or "No abstract summary provided.",
            "claimedContributions": paper.get("claimedContributions") or ["Verify the claimed contribution."],
            "methodChecklist": ["Check whether assumptions, baselines, and implementation details support the method claims."],
            "experimentChecklist": ["Verify datasets, metrics, baselines, and ablation coverage."],
            "evidenceToVerify": ["Match each major claim to a table, figure, experiment, or cited prior result."],
            "potentialWeaknesses": ["Look for unsupported claims, missing ablations, and unclear limitations."],
            "questionsForReviewer": ["What evidence would change your assessment of the paper?"],
            "blindReviewRisks": paper.get("possibleBlindnessRisks", []),
            "confidence": 0.5,
        }
