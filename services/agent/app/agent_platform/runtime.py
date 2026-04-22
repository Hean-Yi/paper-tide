from __future__ import annotations

from dataclasses import dataclass

from app.agent_platform.config import AgentPlatformConfig
from app.agent_platform.consumer import AnalysisRequestedConsumer
from app.agent_platform.publisher import AnalysisRequestedPublisher
from app.agent_platform.state_machine import ExecutionStateMachine


@dataclass(frozen=True, slots=True)
class AgentPlatformRuntime:
    config: AgentPlatformConfig
    analysis_requested_consumer: AnalysisRequestedConsumer
    execution_message_publisher: AnalysisRequestedPublisher
    execution_state_machine: ExecutionStateMachine
