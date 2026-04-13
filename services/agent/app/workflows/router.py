from __future__ import annotations

from typing import Any

from langgraph.graph import END, StateGraph

from app.redaction import redact_result
from app.workflows.conflict_analysis import (
    conflict_summarize,
    redact_conflict_result,
    validate_conflict_result,
)
from app.workflows.llm import request_json_from_model
from app.workflows.paper_understanding import build_paper_understanding
from app.workflows.review_assist import (
    redact_review_assist_result,
    review_assist_assess,
    validate_review_assist_result,
)
from app.workflows.schemas import validate_result


def _screening_assess(state: dict[str, Any]) -> dict[str, Any]:
    paper = state.get("paper_understanding") or {}
    payload = state.get("request_payload") or {}
    fallback = {
        "taskType": "SCREENING_ANALYSIS",
        "manuscriptId": str(state["manuscript_id"]),
        "versionId": str(state["version_id"]),
        "status": "SUCCESS",
        "topicCategory": str(payload.get("topicCategory") or "Unspecified"),
        "scopeFit": str(payload.get("scopeFit") or "PARTIAL"),
        "formatRisks": list(payload.get("formatRisks") or []),
        "blindnessRisks": list(payload.get("blindnessRisks") or paper.get("possibleBlindnessRisks") or []),
        "screeningSummary": str(payload.get("screeningSummary") or "Screening analysis generated from submitted metadata."),
        "confidence": 0.5,
    }
    raw_result = request_json_from_model(
        state,
        instruction=(
            "Assess screening fit from this paper metadata and return the SCREENING_ANALYSIS schema: "
            f"{paper}"
        ),
        fallback=fallback,
    )
    return {"raw_result": raw_result, "step": "analyzing"}


def _validate_screening_result(state: dict[str, Any]) -> dict[str, Any]:
    return {"raw_result": validate_result("SCREENING_ANALYSIS", state["raw_result"]), "step": "validating"}


def _redact_screening_result(state: dict[str, Any]) -> dict[str, Any]:
    return {"redacted_result": redact_result("SCREENING_ANALYSIS", state["raw_result"]), "step": "summarizing"}


def _compile_screening_workflow():
    graph = StateGraph(dict)
    graph.add_node("understand", build_paper_understanding)
    graph.add_node("analyze", _screening_assess)
    graph.add_node("validate", _validate_screening_result)
    graph.add_node("redact", _redact_screening_result)
    graph.set_entry_point("understand")
    graph.add_edge("understand", "analyze")
    graph.add_edge("analyze", "validate")
    graph.add_edge("validate", "redact")
    graph.add_edge("redact", END)
    return graph.compile()


def _compile_review_assist_workflow():
    graph = StateGraph(dict)
    graph.add_node("understand", build_paper_understanding)
    graph.add_node("analyze", review_assist_assess)
    graph.add_node("validate", validate_review_assist_result)
    graph.add_node("redact", redact_review_assist_result)
    graph.set_entry_point("understand")
    graph.add_edge("understand", "analyze")
    graph.add_edge("analyze", "validate")
    graph.add_edge("validate", "redact")
    graph.add_edge("redact", END)
    return graph.compile()


def _compile_conflict_workflow():
    graph = StateGraph(dict)
    graph.add_node("analyze", conflict_summarize)
    graph.add_node("validate", validate_conflict_result)
    graph.add_node("redact", redact_conflict_result)
    graph.set_entry_point("analyze")
    graph.add_edge("analyze", "validate")
    graph.add_edge("validate", "redact")
    graph.add_edge("redact", END)
    return graph.compile()


_WORKFLOWS = {
    "SCREENING_ANALYSIS": _compile_screening_workflow,
    "REVIEW_ASSIST_ANALYSIS": _compile_review_assist_workflow,
    "DECISION_CONFLICT_ANALYSIS": _compile_conflict_workflow,
}


def select_workflow(task_type: str):
    try:
        return _WORKFLOWS[task_type]()
    except KeyError as exc:
        raise ValueError(f"Unsupported task type: {task_type}") from exc


def build_initial_state(task_record: Any) -> dict[str, Any]:
    task_type = _read(task_record, "task_type")
    round_id = _read(task_record, "round_id")
    if task_type == "DECISION_CONFLICT_ANALYSIS" and round_id is None:
        raise ValueError("roundId is required for DECISION_CONFLICT_ANALYSIS")

    return {
        "task_id": _read(task_record, "task_id"),
        "task_type": task_type,
        "manuscript_id": _read(task_record, "manuscript_id"),
        "version_id": _read(task_record, "version_id"),
        "round_id": round_id,
        "request_payload": _read(task_record, "request_payload") or {},
        "raw_result": None,
        "redacted_result": None,
        "error": None,
    }


def _read(task_record: Any, key: str) -> Any:
    if isinstance(task_record, dict):
        return task_record.get(key)
    return getattr(task_record, key)
