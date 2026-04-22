from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(slots=True)
class AgentPlatformConfig:
    max_attempts: int = 3
    analysis_requested_topic: str = "analysis.requested"

    @classmethod
    def from_env(cls) -> "AgentPlatformConfig":
        max_attempts = int(os.getenv("AGENT_PLATFORM_MAX_ATTEMPTS", "3"))
        analysis_requested_topic = os.getenv("AGENT_PLATFORM_ANALYSIS_REQUESTED_TOPIC", "analysis.requested")
        return cls(
            max_attempts=max_attempts,
            analysis_requested_topic=analysis_requested_topic,
        )
