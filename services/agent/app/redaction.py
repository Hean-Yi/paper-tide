from __future__ import annotations

from typing import Any

UNSAFE_REPLACEMENT = "Reviewer-safe summary unavailable due to redaction."

_IDENTITY_MARKERS = (
    "acknowledgement",
    "acknowledgment",
    "author",
    "authors",
    "grant",
    "institution",
    "university",
    "project",
    "funded by",
    "self-citation",
    "our previous",
)


def sanitize_text(value: str) -> str:
    lowered = value.lower()
    if any(marker in lowered for marker in _IDENTITY_MARKERS):
        return UNSAFE_REPLACEMENT
    return value


def _sanitize_value(value: Any) -> Any:
    if isinstance(value, str):
        return sanitize_text(value)
    if isinstance(value, list):
        return [_sanitize_value(item) for item in value]
    if isinstance(value, dict):
        return {key: _sanitize_value(nested) for key, nested in value.items()}
    return value


def redact_result(task_type: str, raw_result: dict[str, Any]) -> dict[str, Any]:
    if task_type == "SCREENING_ANALYSIS":
        safe_keys = {
            "taskType",
            "manuscriptId",
            "versionId",
            "status",
            "topicCategory",
            "scopeFit",
            "formatRisks",
            "blindnessRisks",
            "screeningSummary",
            "confidence",
        }
    elif task_type == "REVIEW_ASSIST_ANALYSIS":
        safe_keys = {
            "taskType",
            "manuscriptId",
            "versionId",
            "status",
            "summary",
            "novelty",
            "methodology",
            "writing",
            "risks",
            "finalSuggestion",
            "confidence",
        }
    elif task_type == "DECISION_CONFLICT_ANALYSIS":
        safe_keys = {
            "taskType",
            "manuscriptId",
            "versionId",
            "status",
            "consensusPoints",
            "conflictPoints",
            "highRiskIssues",
            "decisionSummary",
            "confidence",
        }
    else:
        safe_keys = set(raw_result)

    projected = {key: raw_result[key] for key in safe_keys if key in raw_result}
    return _sanitize_value(projected)
