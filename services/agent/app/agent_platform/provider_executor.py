from __future__ import annotations

import json
from json import JSONDecodeError
from typing import Any

from app.agent_platform.config import AgentPlatformConfig
from app.agent_platform.errors import PROVIDER_SCHEMA, PROVIDER_TRANSIENT, ProviderExecutionError
from app.agent_platform.schemas import (
    ConflictAnalysisResult,
    ReviewAssistResult,
    ReviewerAssignmentAssistResult,
    ScreeningAnalysisResult,
)

_DEFAULT_TEXT_BUDGET = 4000
_PDF_TEXT_BUDGET = 3000
_SECTION_TEXT_BUDGET = 1200
_SHORT_TEXT_BUDGET = 240
_LIST_ITEM_BUDGETS = {
    "candidateDrafts": 40,
    "reviewReports": 25,
}
_SHORT_TEXT_KEYS = {"reason", "rationale", "conflictDescription", "commentsToChair"}
_ASSIGNMENT_RESULT_LIMIT = 20


class ProviderExecutor:
    def __init__(self, config: AgentPlatformConfig | None = None, client: Any | None = None) -> None:
        self._config = config or AgentPlatformConfig()
        self._client = client
        if self._client is None and self._config.has_llm_provider():
            from openai import OpenAI

            self._client = OpenAI(
                api_key=self._config.llm_api_key,
                base_url=self._config.llm_base_url,
            )

    def run_reviewer_assist(self, paper: dict[str, Any]) -> dict[str, Any]:
        fallback = {
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
        return self._request_structured_result(
            schema_name="review_assist_analysis",
            schema=ReviewAssistResult.model_json_schema(),
            instruction=(
                "Create checklist-only reviewer assistance for a double-blind paper review workflow. "
                "Do not include scores, recommendations, decisions, complete review text, author names, "
                "institutions, acknowledgements, grants, or self-citation clues. Return only the schema fields. "
                f"Paper understanding JSON: {_prompt_json(paper)}"
            ),
            fallback=fallback,
        )

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
        fallback = {
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
        return self._request_structured_result(
            schema_name="decision_conflict_analysis",
            schema=ConflictAnalysisResult.model_json_schema(),
            instruction=(
                "Summarize reviewer consensus and conflicts for the chair decision workflow. "
                "Use evidence from submitted reports only, keep the output concise, and return only the schema fields. "
                f"Conflict payload JSON: {_prompt_json(payload)}"
            ),
            fallback=fallback,
        )

    def run_reviewer_assignment_assist(self, payload: dict[str, Any]) -> dict[str, Any]:
        context = payload.get("assignmentAssist") or {}
        candidates = list(payload.get("candidateDrafts") or [])
        ranked = []
        for index, candidate in enumerate(candidates[:_ASSIGNMENT_RESULT_LIMIT], start=1):
            ranked.append(
                {
                    "reviewerId": str(candidate.get("reviewerId", "")),
                    "draftId": str(candidate.get("draftId", "")),
                    "rank": index,
                    "rationale": str(candidate.get("reason") or "Candidate from deterministic assignment draft."),
                    "riskFlags": [],
                }
            )
        fallback = {
            "taskType": "REVIEWER_ASSIGNMENT_ASSIST",
            "manuscriptId": str(context.get("manuscriptId", "")),
            "versionId": str(context.get("versionId", "")),
            "status": "SUCCESS",
            "rankedCandidates": ranked,
            "assignmentSummary": f"{len(ranked)} of {len(candidates)} reviewer candidate(s) ranked for chair confirmation.",
            "confidence": 0.5,
        }
        return self._request_structured_result(
            schema_name="reviewer_assignment_assist",
            schema=ReviewerAssignmentAssistResult.model_json_schema(),
            instruction=(
                "Rank reviewer assignment draft candidates for a paper review chair. Use only supplied candidate "
                "metadata, bidding signals, load, and risk flags. Do not invent reviewers, scores, decisions, "
                "or author identity. Return only the schema fields. "
                f"Assignment payload JSON: {_prompt_json(payload)}"
            ),
            fallback=fallback,
        )

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
        fallback = {
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
        return self._request_structured_result(
            schema_name="screening_analysis",
            schema=ScreeningAnalysisResult.model_json_schema(),
            instruction=(
                "Assess whether this manuscript is ready for chair screening in a paper review system. "
                "Identify scope fit, format risks, and blind-review risks. Return only the schema fields. "
                f"Screening payload JSON: {_prompt_json(payload)}"
            ),
            fallback=fallback,
        )

    def _request_structured_result(
        self,
        *,
        schema_name: str,
        schema: dict[str, Any],
        instruction: str,
        fallback: dict[str, Any],
    ) -> dict[str, Any]:
        if self._client is None or not self._config.has_llm_provider():
            return fallback

        try:
            response = self._client.chat.completions.create(
                model=self._config.llm_model,
                temperature=0,
                max_tokens=self._config.llm_max_tokens,
                response_format={
                    "type": "json_schema",
                    "json_schema": {
                        "name": schema_name,
                        "strict": True,
                        "schema": schema,
                    },
                },
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "You are an internal paper-review analysis component. Return strict JSON only. "
                            "Do not expose chain-of-thought; provide concise final fields that match the schema."
                        ),
                    },
                    {"role": "user", "content": instruction},
                ],
            )
        except Exception as exc:
            raise ProviderExecutionError(
                f"LLM provider request failed: {exc}",
                category=PROVIDER_TRANSIENT,
                retryable=True,
            ) from exc
        content = response.choices[0].message.content or "{}"
        try:
            provider_result = json.loads(content)
        except JSONDecodeError as exc:
            raise ProviderExecutionError(
                "LLM provider returned invalid JSON",
                category=PROVIDER_SCHEMA,
                retryable=False,
            ) from exc
        if not isinstance(provider_result, dict):
            raise ProviderExecutionError(
                "LLM provider returned a non-object JSON payload",
                category=PROVIDER_SCHEMA,
                retryable=False,
            )
        return {**fallback, **provider_result}


def _prompt_json(value: dict[str, Any]) -> str:
    return json.dumps(_budget_payload(value), ensure_ascii=False, sort_keys=True)


def _budget_payload(value: Any, *, key: str | None = None) -> Any:
    if isinstance(value, dict):
        return {str(child_key): _budget_payload(child_value, key=str(child_key)) for child_key, child_value in value.items()}
    if isinstance(value, list):
        limit = _LIST_ITEM_BUDGETS.get(key or "")
        items = value if limit is None else value[:limit]
        budgeted = [_budget_payload(item, key=key) for item in items]
        if limit is not None and len(value) > limit:
            budgeted.append({"truncatedItems": len(value) - limit})
        return budgeted
    if isinstance(value, str):
        limit = (
            _PDF_TEXT_BUDGET
            if key == "pdfText"
            else _SECTION_TEXT_BUDGET
            if key in {"introduction", "method", "experiment", "conclusion"}
            else _SHORT_TEXT_BUDGET
            if key in _SHORT_TEXT_KEYS
            else _DEFAULT_TEXT_BUDGET
        )
        return _truncate_text(value, limit)
    return value


def _truncate_text(value: str, limit: int) -> str:
    if len(value) <= limit:
        return value
    omitted = len(value) - limit
    return value[:limit] + f"... [truncated {omitted} chars]"
