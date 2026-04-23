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

    def run_conflict_analysis(self, payload: dict[str, Any]) -> dict[str, Any]:
        context = payload.get("conflictAnalysis") or {}
        reports = list(payload.get("reviewReports") or [])
        recommendations = [str(report.get("recommendation")) for report in reports if report.get("recommendation")]
        unique_recommendations = sorted(set(recommendations))
        conflict_points = []
        if len(unique_recommendations) > 1:
            conflict_points.append("Reviewer recommendations differ: " + ", ".join(unique_recommendations))
        for report in reports:
            weakness = report.get("weaknesses") or report.get("commentsToChair")
            if weakness:
                conflict_points.append(str(weakness))
        if not conflict_points:
            conflict_points.append("No explicit conflict points were supplied; verify reviewer rationale manually.")
        consensus = [
            "Submitted reviews available for synthesis."
            if reports
            else "No submitted review reports were supplied."
        ]
        return {
            "taskType": "DECISION_CONFLICT_ANALYSIS",
            "manuscriptId": str(context.get("manuscriptId", "")),
            "versionId": str(context.get("versionId", "")),
            "status": "SUCCESS",
            "consensusPoints": consensus,
            "conflictPoints": conflict_points,
            "highRiskIssues": [],
            "decisionSummary": (
                f"{len(reports)} review report(s) analyzed for round {context.get('roundId', 'unknown')}."
            ),
            "confidence": 0.5,
        }

    def run_screening(self, payload: dict[str, Any]) -> dict[str, Any]:
        context = payload.get("screening") or {}
        keywords = list(payload.get("keywords") or [])
        format_risks = []
        if not payload.get("abstract"):
            format_risks.append("Missing abstract text in screening payload.")
        if not keywords:
            format_risks.append("Missing keyword metadata for scope screening.")
        if not format_risks:
            format_risks.append("No obvious format risks detected from metadata.")
        blindness_risks = []
        combined_text = f"{payload.get('title', '')} {payload.get('abstract', '')}".lower()
        for marker in ("author", "institution", "university", "grant"):
            if marker in combined_text:
                blindness_risks.append(f"Potential blind-review marker: {marker}.")
        return {
            "taskType": "SCREENING_ANALYSIS",
            "manuscriptId": str(context.get("manuscriptId", "")),
            "versionId": str(context.get("versionId", "")),
            "status": "SUCCESS",
            "topicCategory": ", ".join(str(keyword) for keyword in keywords[:3]) or "Unclassified",
            "scopeFit": "FIT",
            "formatRisks": format_risks,
            "blindnessRisks": blindness_risks,
            "screeningSummary": "Metadata and abstract are ready for chair screening.",
            "confidence": 0.5,
        }
