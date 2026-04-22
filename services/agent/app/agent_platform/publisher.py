from __future__ import annotations

from app.agent_platform.messages import AnalysisRequestedMessage
from app.agent_platform.outbox import ExecutionOutboxMessage, InMemoryExecutionOutbox


class AnalysisRequestedPublisher:
    def __init__(self, outbox: InMemoryExecutionOutbox, *, topic: str = "analysis.requested") -> None:
        self._outbox = outbox
        self._topic = topic

    def publish(self, message: AnalysisRequestedMessage) -> ExecutionOutboxMessage:
        return self._outbox.enqueue(self._topic, message.to_dict())
