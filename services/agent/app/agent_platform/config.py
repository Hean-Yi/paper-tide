from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(slots=True)
class AgentPlatformConfig:
    max_attempts: int = 3
    analysis_requested_topic: str = "analysis.requested"
    db_user: str | None = None
    db_password: str | None = None
    db_dsn: str | None = None

    @classmethod
    def from_env(cls) -> "AgentPlatformConfig":
        max_attempts = int(os.getenv("AGENT_PLATFORM_MAX_ATTEMPTS", "3"))
        analysis_requested_topic = os.getenv("AGENT_PLATFORM_ANALYSIS_REQUESTED_TOPIC", "analysis.requested")
        return cls(
            max_attempts=max_attempts,
            analysis_requested_topic=analysis_requested_topic,
            db_user=os.getenv("AGENT_PLATFORM_DB_USER"),
            db_password=os.getenv("AGENT_PLATFORM_DB_PASSWORD"),
            db_dsn=os.getenv("AGENT_PLATFORM_DB_DSN"),
        )

    def has_durable_execution_store(self) -> bool:
        return bool(self.db_user and self.db_password and self.db_dsn)
