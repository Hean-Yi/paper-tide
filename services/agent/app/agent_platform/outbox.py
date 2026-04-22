from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field
from datetime import UTC, datetime
from threading import Lock
from typing import Any
from uuid import uuid4


@dataclass(slots=True)
class ExecutionOutboxMessage:
    message_id: str
    topic: str
    payload: dict[str, Any]
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    published_at: datetime | None = None
    publish_attempts: int = 0


class InMemoryExecutionOutbox:
    def __init__(self) -> None:
        self._messages: dict[str, ExecutionOutboxMessage] = {}
        self._lock = Lock()

    def enqueue(
        self,
        topic: str,
        payload: dict[str, Any],
        *,
        message_id: str | None = None,
    ) -> ExecutionOutboxMessage:
        resolved_message_id = message_id or str(uuid4())
        with self._lock:
            if resolved_message_id in self._messages:
                raise ValueError(f"message_id already exists: {resolved_message_id}")
            message = ExecutionOutboxMessage(
                message_id=resolved_message_id,
                topic=topic,
                payload=deepcopy(payload),
            )
            self._messages[message.message_id] = message
            return message

    def pending(self) -> list[ExecutionOutboxMessage]:
        with self._lock:
            return [message for message in self._messages.values() if message.published_at is None]

    def mark_published(self, message_id: str) -> ExecutionOutboxMessage | None:
        with self._lock:
            message = self._messages.get(message_id)
            if message is None:
                return None
            message.publish_attempts += 1
            message.published_at = datetime.now(UTC)
            return message
