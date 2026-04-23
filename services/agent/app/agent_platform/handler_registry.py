from __future__ import annotations

from app.agent_platform.handlers.base import AnalysisTaskHandler
from app.agent_platform.handlers.conflict_analysis import ConflictAnalysisHandler
from app.agent_platform.handlers.reviewer_assist import ReviewerAssistHandler
from app.agent_platform.handlers.screening import ScreeningAnalysisHandler


class AnalysisHandlerRegistry:
    def __init__(self, handlers: list[AnalysisTaskHandler] | None = None) -> None:
        resolved_handlers = handlers or [
            ReviewerAssistHandler(),
            ConflictAnalysisHandler(),
            ScreeningAnalysisHandler(),
        ]
        self._handlers = {handler.analysis_type: handler for handler in resolved_handlers}

    def get(self, analysis_type: str) -> AnalysisTaskHandler:
        try:
            return self._handlers[analysis_type]
        except KeyError as exc:
            raise ValueError(f"Unsupported analysis type: {analysis_type}") from exc
