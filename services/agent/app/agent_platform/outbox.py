from __future__ import annotations

import json
from collections.abc import Callable
from dataclasses import dataclass, field, replace
from datetime import UTC, datetime
from threading import Lock
from typing import Any, Mapping, Protocol
from uuid import uuid4

from app.agent_platform.snapshots import freeze_json_mapping, thaw_json_mapping


@dataclass(frozen=True, slots=True)
class ExecutionOutboxMessage:
    message_id: str
    topic: str
    payload: Mapping[str, Any]
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    published_at: datetime | None = None
    publish_attempts: int = 0


class ExecutionOutbox(Protocol):
    def enqueue(
        self,
        topic: str,
        payload: dict[str, Any],
        *,
        message_id: str | None = None,
    ) -> ExecutionOutboxMessage: ...

    def pending(self) -> list[ExecutionOutboxMessage]: ...

    def mark_published(self, message_id: str) -> ExecutionOutboxMessage | None: ...


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
                payload=freeze_json_mapping(payload),
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
            published = replace(
                message,
                publish_attempts=message.publish_attempts + 1,
                published_at=datetime.now(UTC),
            )
            self._messages[message_id] = published
            return published


def payload_copy(message: ExecutionOutboxMessage) -> dict[str, Any]:
    return thaw_json_mapping(message.payload)


class OracleExecutionOutbox:
    def __init__(self, connection_factory: Callable[[], Any]) -> None:
        self._connection_factory = connection_factory

    def enqueue(
        self,
        topic: str,
        payload: dict[str, Any],
        *,
        message_id: str | None = None,
    ) -> ExecutionOutboxMessage:
        resolved_message_id = message_id or str(uuid4())
        job_id = payload.get("jobId")
        if not isinstance(job_id, str) or not job_id:
            raise ValueError("jobId is required for durable execution outbox messages")

        created_at = datetime.now(UTC)
        connection = self._connection_factory()
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO EXECUTION_OUTBOX (
                      JOB_ID,
                      MESSAGE_TYPE,
                      MESSAGE_KEY,
                      MESSAGE_PAYLOAD,
                      MESSAGE_STATUS,
                      RETRY_COUNT,
                      CREATED_AT
                    ) VALUES (
                      :job_id,
                      :message_type,
                      :message_key,
                      :message_payload,
                      'PENDING',
                      0,
                      :created_at
                    )
                    """,
                    {
                        "job_id": job_id,
                        "message_type": topic,
                        "message_key": resolved_message_id,
                        "message_payload": _serialize_payload(payload),
                        "created_at": created_at,
                    },
                )
            connection.commit()
            return ExecutionOutboxMessage(
                message_id=resolved_message_id,
                topic=topic,
                payload=freeze_json_mapping(payload),
                created_at=created_at,
            )
        finally:
            _close_quietly(connection)

    def pending(self) -> list[ExecutionOutboxMessage]:
        connection = self._connection_factory()
        try:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT MESSAGE_KEY,
                           MESSAGE_TYPE,
                           MESSAGE_PAYLOAD,
                           CREATED_AT,
                           PUBLISHED_AT,
                           RETRY_COUNT
                      FROM EXECUTION_OUTBOX
                     WHERE PUBLISHED_AT IS NULL
                     ORDER BY CREATED_AT
                    """
                )
                rows = cursor.fetchall()
            return [_message_from_row(row) for row in rows]
        finally:
            _close_quietly(connection)

    def mark_published(self, message_id: str) -> ExecutionOutboxMessage | None:
        connection = self._connection_factory()
        try:
            current = self._get_message(connection, message_id)
            if current is None:
                return None

            published_at = datetime.now(UTC)
            retry_count = current.publish_attempts + 1
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE EXECUTION_OUTBOX
                       SET MESSAGE_STATUS = 'PUBLISHED',
                           RETRY_COUNT = :retry_count,
                           PUBLISHED_AT = :published_at
                     WHERE MESSAGE_KEY = :message_key
                    """,
                    {
                        "message_key": message_id,
                        "retry_count": retry_count,
                        "published_at": published_at,
                    },
                )
            connection.commit()
            return replace(current, publish_attempts=retry_count, published_at=published_at)
        finally:
            _close_quietly(connection)

    def _get_message(self, connection: Any, message_id: str) -> ExecutionOutboxMessage | None:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                SELECT MESSAGE_KEY,
                       MESSAGE_TYPE,
                       MESSAGE_PAYLOAD,
                       CREATED_AT,
                       PUBLISHED_AT,
                       RETRY_COUNT
                  FROM EXECUTION_OUTBOX
                 WHERE MESSAGE_KEY = :message_key
                """,
                {"message_key": message_id},
            )
            row = cursor.fetchone()
        if row is None:
            return None
        return _message_from_row(row)


def _message_from_row(row: tuple[Any, ...]) -> ExecutionOutboxMessage:
    return ExecutionOutboxMessage(
        message_id=str(row[0]),
        topic=str(row[1]),
        payload=freeze_json_mapping(json.loads(str(row[2]))),
        created_at=row[3] if isinstance(row[3], datetime) else datetime.now(UTC),
        published_at=row[4] if isinstance(row[4], datetime) else row[4],
        publish_attempts=int(row[5]),
    )


def _serialize_payload(payload: dict[str, Any]) -> str:
    return json.dumps(payload, separators=(",", ":"), sort_keys=True)


def _close_quietly(connection: Any) -> None:
    close = getattr(connection, "close", None)
    if callable(close):
        close()
