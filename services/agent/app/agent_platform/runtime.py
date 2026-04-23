from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.agent_platform.config import AgentPlatformConfig
from app.agent_platform.consumer import AnalysisRequestedConsumer
from app.agent_platform.handler_registry import AnalysisHandlerRegistry
from app.agent_platform.messages import AnalysisCompletedMessage
from app.agent_platform.provider_executor import ProviderExecutor
from app.agent_platform.publisher import AnalysisRequestedPublisher
from app.agent_platform.repositories import ExecutionJobRepository
from app.agent_platform.domain import ExecutionJob
from app.agent_platform.state_machine import ExecutionStateMachine


@dataclass(frozen=True, slots=True)
class AgentPlatformRuntime:
    config: AgentPlatformConfig
    analysis_requested_consumer: AnalysisRequestedConsumer
    execution_message_publisher: AnalysisRequestedPublisher
    execution_state_machine: ExecutionStateMachine
    execution_job_repository: ExecutionJobRepository
    handler_registry: AnalysisHandlerRegistry
    provider_executor: ProviderExecutor

    def execute_requested_job(self, job: ExecutionJob) -> dict[str, Any]:
        if job.intent_reference is None:
            raise ValueError("intent_reference is required to publish completion events")

        running_job = self.execution_state_machine.mark_running(job)
        self.execution_job_repository.save(running_job)

        handler = self.handler_registry.get(running_job.analysis_type)
        result = handler.execute(running_job, self.provider_executor)

        completed_job = self.execution_state_machine.mark_succeeded(running_job)
        self.execution_job_repository.save(completed_job)

        event = AnalysisCompletedMessage(
            message_key=f"analysis.completed:{completed_job.idempotency_key}",
            intent_id=int(job.intent_reference),
            job_id=completed_job.job_id,
            analysis_type=completed_job.analysis_type,
            business_status=result["summary_projection"]["businessStatus"],
            summary_projection=result["summary_projection"],
            redacted_result=result["redacted_result"],
        )
        return event.to_dict()
