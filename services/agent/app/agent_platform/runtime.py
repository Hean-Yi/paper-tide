from __future__ import annotations

from dataclasses import dataclass
import logging
from typing import Any

from pydantic import ValidationError

from app.agent_platform.config import AgentPlatformConfig
from app.agent_platform.consumer import AnalysisRequestedConsumer
from app.agent_platform.handler_registry import AnalysisHandlerRegistry
from app.agent_platform.messages import AnalysisCompletedMessage
from app.agent_platform.provider_executor import ProviderExecutor
from app.agent_platform.publisher import AnalysisCompletedPublisher, AnalysisRequestedPublisher
from app.agent_platform.repositories import ExecutionJobRepository
from app.agent_platform.domain import ExecutionJob
from app.agent_platform.errors import (
    BUSINESS_INPUT,
    PROVIDER_SCHEMA,
    UNEXPECTED_RUNTIME,
    ProviderExecutionError,
)
from app.agent_platform.state_machine import ExecutionStateMachine

LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class AgentPlatformRuntime:
    config: AgentPlatformConfig
    analysis_requested_consumer: AnalysisRequestedConsumer
    execution_message_publisher: AnalysisRequestedPublisher
    execution_completed_publisher: AnalysisCompletedPublisher
    execution_state_machine: ExecutionStateMachine
    execution_job_repository: ExecutionJobRepository
    handler_registry: AnalysisHandlerRegistry
    provider_executor: ProviderExecutor

    def execute_requested_job(self, job: ExecutionJob) -> dict[str, Any]:
        if job.intent_reference is None:
            raise ValueError("intent_reference is required to publish completion events")

        running_job = self.execution_state_machine.mark_running(job)
        self.execution_job_repository.save(running_job)
        trace_id = _trace_id(running_job)
        LOGGER.info(
            "analysis job started jobId=%s intentId=%s analysisType=%s traceId=%s",
            running_job.job_id,
            running_job.intent_reference,
            running_job.analysis_type,
            trace_id,
        )

        try:
            handler = self.handler_registry.get(running_job.analysis_type)
            result = handler.execute(running_job, self.provider_executor)
        except ProviderExecutionError as exc:
            failed_job = _mark_failure(self.execution_state_machine, running_job, str(exc), exc.category, exc.retryable)
            self.execution_job_repository.save(failed_job)
            LOGGER.warning(
                "analysis job failed jobId=%s state=%s category=%s retryable=%s attempts=%s traceId=%s reason=%s",
                failed_job.job_id,
                failed_job.execution_state,
                failed_job.last_error_category,
                exc.retryable,
                failed_job.attempt_count,
                trace_id,
                failed_job.failure_reason,
            )
            raise
        except ValidationError as exc:
            failed_job = self.execution_state_machine.mark_terminal_failure(
                running_job,
                "LLM provider result failed schema validation",
                error_category=PROVIDER_SCHEMA,
            )
            self.execution_job_repository.save(failed_job)
            LOGGER.warning(
                "analysis job failed terminal schema validation jobId=%s traceId=%s reason=%s",
                failed_job.job_id,
                trace_id,
                exc,
            )
            raise
        except ValueError as exc:
            failed_job = self.execution_state_machine.mark_terminal_failure(
                running_job,
                str(exc),
                error_category=BUSINESS_INPUT,
            )
            self.execution_job_repository.save(failed_job)
            LOGGER.warning(
                "analysis job failed terminal business input jobId=%s traceId=%s reason=%s",
                failed_job.job_id,
                trace_id,
                failed_job.failure_reason,
            )
            raise
        except Exception as exc:
            failed_job = self.execution_state_machine.mark_retryable_failure(
                running_job,
                str(exc),
                error_category=UNEXPECTED_RUNTIME,
            )
            self.execution_job_repository.save(failed_job)
            LOGGER.warning(
                "analysis job failed jobId=%s state=%s category=%s attempts=%s traceId=%s reason=%s",
                failed_job.job_id,
                failed_job.execution_state,
                failed_job.last_error_category,
                failed_job.attempt_count,
                trace_id,
                failed_job.failure_reason,
            )
            raise

        completed_job = self.execution_state_machine.mark_succeeded(running_job)
        self.execution_job_repository.save(completed_job)
        LOGGER.info(
            "analysis job succeeded jobId=%s intentId=%s analysisType=%s traceId=%s",
            completed_job.job_id,
            completed_job.intent_reference,
            completed_job.analysis_type,
            trace_id,
        )

        event = AnalysisCompletedMessage(
            message_key=f"analysis.completed:{completed_job.idempotency_key}",
            intent_id=int(job.intent_reference),
            job_id=completed_job.job_id,
            analysis_type=completed_job.analysis_type,
            business_status=result["summary_projection"]["businessStatus"],
            summary_projection=result["summary_projection"],
            redacted_result=result["redacted_result"],
            trace_id=trace_id,
        )
        self.execution_completed_publisher.publish(event)
        return event.to_dict()


def _trace_id(job: ExecutionJob) -> str | None:
    value = job.input_snapshot_copy().get("traceId")
    if isinstance(value, str) and value:
        return value
    return None


def _mark_failure(
    state_machine: ExecutionStateMachine,
    job: ExecutionJob,
    reason: str,
    category: str,
    retryable: bool,
) -> ExecutionJob:
    if retryable:
        return state_machine.mark_retryable_failure(job, reason, error_category=category)
    return state_machine.mark_terminal_failure(job, reason, error_category=category)
