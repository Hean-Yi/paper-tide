from __future__ import annotations

from typing import Any, Protocol

from app.agent_platform.domain import ExecutionJob


class ProviderExecutor(Protocol):
    def run_reviewer_assist(self, paper: dict[str, Any]) -> dict[str, Any]: ...


class AnalysisTaskHandler(Protocol):
    analysis_type: str

    def execute(self, job: ExecutionJob, provider_executor: ProviderExecutor) -> dict[str, Any]: ...
