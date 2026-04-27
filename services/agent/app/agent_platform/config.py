from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from os import PathLike

from dotenv import load_dotenv


@dataclass(slots=True)
class AgentPlatformConfig:
    max_attempts: int = 3
    analysis_requested_topic: str = "analysis.requested"
    analysis_completed_topic: str = "analysis.completed"
    db_user: str | None = None
    db_password: str | None = None
    db_dsn: str | None = None
    llm_api_key: str | None = None
    llm_base_url: str | None = None
    llm_model: str | None = None
    llm_max_tokens: int = 1200

    @classmethod
    def from_env(cls, env_file: str | PathLike[str] | None = None) -> "AgentPlatformConfig":
        resolved_env_file = Path(env_file) if env_file is not None else Path(__file__).resolve().parents[2] / ".env"
        if resolved_env_file.exists():
            load_dotenv(resolved_env_file, override=True)
        max_attempts = int(os.getenv("AGENT_PLATFORM_MAX_ATTEMPTS", "3"))
        analysis_requested_topic = os.getenv("AGENT_PLATFORM_ANALYSIS_REQUESTED_TOPIC", "analysis.requested")
        analysis_completed_topic = os.getenv("AGENT_PLATFORM_ANALYSIS_COMPLETED_TOPIC", "analysis.completed")
        return cls(
            max_attempts=max_attempts,
            analysis_requested_topic=analysis_requested_topic,
            analysis_completed_topic=analysis_completed_topic,
            db_user=os.getenv("AGENT_PLATFORM_DB_USER"),
            db_password=os.getenv("AGENT_PLATFORM_DB_PASSWORD"),
            db_dsn=os.getenv("AGENT_PLATFORM_DB_DSN"),
            llm_api_key=os.getenv("API") or os.getenv("OPENROUTER_API_KEY") or os.getenv("SILICONFLOW_API_KEY"),
            llm_base_url=_normalize_llm_base_url(os.getenv("URL") or os.getenv("OPENROUTER_BASE_URL")),
            llm_model=os.getenv("MODEL") or os.getenv("LLM_MODEL") or os.getenv("OPENROUTER_MODEL"),
            llm_max_tokens=int(os.getenv("LLM_MAX_TOKENS", "1200")),
        )

    def has_durable_execution_store(self) -> bool:
        return bool(self.db_user and self.db_password and self.db_dsn)

    def has_llm_provider(self) -> bool:
        return bool(self.llm_api_key and self.llm_base_url and self.llm_model)


def _normalize_llm_base_url(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip().rstrip("/")
    chat_suffix = "/chat/completions"
    if normalized.endswith(chat_suffix):
        normalized = normalized[: -len(chat_suffix)]
    return normalized or None
