from __future__ import annotations

from app.agent_platform.messages import AnalysisCompletedMessage, AnalysisRequestedMessage
from app.agent_platform.outbox import ExecutionOutbox, ExecutionOutboxMessage


class AnalysisRequestedPublisher:
    def __init__(self, outbox: ExecutionOutbox, *, topic: str = "analysis.requested") -> None:
        self._outbox = outbox
        self._topic = topic

    def publish(self, message: AnalysisRequestedMessage) -> ExecutionOutboxMessage:
        return self._outbox.enqueue(self._topic, message.to_dict())


class AnalysisCompletedPublisher:
    def __init__(self, outbox: ExecutionOutbox, *, topic: str = "analysis.completed") -> None:
        self._outbox = outbox
        self._topic = topic

    def publish(self, message: AnalysisCompletedMessage) -> ExecutionOutboxMessage:
        return self._outbox.enqueue(self._topic, message.to_dict(), message_id=message.message_key)

    def pending(self) -> list[ExecutionOutboxMessage]:
        return self._outbox.pending()
