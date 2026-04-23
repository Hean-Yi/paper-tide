from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from app.agent_platform.domain import ExecutionJob
from app.agent_platform.messages import AnalysisRequestedMessage
from app.agent_platform.repositories import ExecutionJobRepository


class AnalysisRequestedConsumer:
    def __init__(self, repository: ExecutionJobRepository) -> None:
        self._repository = repository

    def handle(self, message: Mapping[str, Any] | AnalysisRequestedMessage) -> ExecutionJob:
        if isinstance(message, AnalysisRequestedMessage):
            requested = message
        else:
            requested = AnalysisRequestedMessage.from_mapping(message)

        return self._repository.create_or_reuse(
            requested.idempotency_key,
            requested.analysis_type,
            requested.request_payload,
            job_id=requested.job_id,
            intent_reference=requested.intent_reference,
        )
