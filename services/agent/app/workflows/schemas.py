from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, conint

Score = conint(strict=True, ge=1, le=5)


class ScoredAnalysis(BaseModel):
    analysis: str
    score: Score


class ScreeningAnalysisResult(BaseModel):
    taskType: Literal["SCREENING_ANALYSIS"]
    manuscriptId: str
    versionId: str
    status: str
    topicCategory: str
    scopeFit: Literal["FIT", "PARTIAL", "UNFIT"]
    formatRisks: list[str]
    blindnessRisks: list[str]
    screeningSummary: str
    confidence: float = Field(ge=0, le=1)


class ReviewAssistResult(BaseModel):
    taskType: Literal["REVIEW_ASSIST_ANALYSIS"]
    manuscriptId: str
    versionId: str
    status: str
    summary: str
    novelty: ScoredAnalysis
    methodology: ScoredAnalysis
    writing: ScoredAnalysis
    risks: list[str]
    finalSuggestion: str
    confidence: float = Field(ge=0, le=1)


class ConflictAnalysisResult(BaseModel):
    taskType: Literal["DECISION_CONFLICT_ANALYSIS"]
    manuscriptId: str
    versionId: str
    status: str
    consensusPoints: list[str]
    conflictPoints: list[str]
    highRiskIssues: list[str]
    decisionSummary: str
    confidence: float = Field(ge=0, le=1)


def validate_result(task_type: str, raw_result: dict) -> dict:
    if task_type == "SCREENING_ANALYSIS":
        return ScreeningAnalysisResult.model_validate(raw_result).model_dump()
    if task_type == "REVIEW_ASSIST_ANALYSIS":
        return ReviewAssistResult.model_validate(raw_result).model_dump()
    if task_type == "DECISION_CONFLICT_ANALYSIS":
        return ConflictAnalysisResult.model_validate(raw_result).model_dump()
    raise ValueError(f"Unsupported task type: {task_type}")
