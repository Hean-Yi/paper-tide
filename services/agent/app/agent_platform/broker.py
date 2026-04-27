from __future__ import annotations

import asyncio
import inspect
import json
from collections.abc import Awaitable, Callable
from typing import Any, Protocol

from app.agent_platform.config import AgentPlatformConfig
from app.agent_platform.outbox import payload_copy
from app.agent_platform.runtime import AgentPlatformRuntime


class AnalysisBroker(Protocol):
    def publish(self, topic: str, payload: dict[str, Any]) -> None | Awaitable[None]: ...


class InMemoryAnalysisBroker:
    def __init__(self) -> None:
        self.published: list[dict[str, Any]] = []

    def publish(self, topic: str, payload: dict[str, Any]) -> None:
        self.published.append({"topic": topic, "payload": payload})


class AgentPlatformBrokerWorker:
    def __init__(self, runtime: AgentPlatformRuntime, broker: AnalysisBroker) -> None:
        self._runtime = runtime
        self._broker = broker

    def handle_requested(self, message: dict[str, Any]) -> dict[str, Any]:
        job = self._runtime.analysis_requested_consumer.handle(message)
        return self._runtime.execute_requested_job(job)

    def pending_completed(self) -> list[dict[str, Any]]:
        return [payload_copy(message) for message in self._runtime.execution_completed_publisher.pending()]

    def publish_pending_completed_once(self) -> int:
        published = 0
        for message in self._runtime.execution_completed_publisher.pending():
            result = self._broker.publish(message.topic, payload_copy(message))
            if inspect.isawaitable(result):
                raise RuntimeError("Use publish_pending_completed_once_async for async brokers")
            self._runtime.execution_completed_publisher.mark_published(message.message_id)
            published += 1
        return published

    async def publish_pending_completed_once_async(self) -> int:
        published = 0
        for message in self._runtime.execution_completed_publisher.pending():
            result = self._broker.publish(message.topic, payload_copy(message))
            if inspect.isawaitable(result):
                await result
            self._runtime.execution_completed_publisher.mark_published(message.message_id)
            published += 1
        return published


class AioPikaAnalysisBroker:
    def __init__(self, connection: Any, channel: Any, exchange: Any, request_queue: Any) -> None:
        self._connection = connection
        self._channel = channel
        self._exchange = exchange
        self._request_queue = request_queue

    @classmethod
    async def connect(cls, config: AgentPlatformConfig) -> "AioPikaAnalysisBroker":
        import aio_pika

        connection = await aio_pika.connect_robust(config.rabbitmq_url)
        channel = await connection.channel()
        exchange = await channel.declare_exchange(
            config.broker_exchange,
            aio_pika.ExchangeType.DIRECT,
            durable=True,
        )
        request_queue = await channel.declare_queue(config.request_queue, durable=True)
        await request_queue.bind(exchange, routing_key=config.request_routing_key)
        return cls(connection, channel, exchange, request_queue)

    async def consume_requested(self, handler: Callable[[dict[str, Any]], Awaitable[None]]) -> None:
        async def on_message(message: Any) -> None:
            async with message.process():
                payload = json.loads(message.body.decode("utf-8"))
                await handler(payload)

        await self._request_queue.consume(on_message)

    async def publish(self, topic: str, payload: dict[str, Any]) -> None:
        import aio_pika

        message = aio_pika.Message(
            body=json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8"),
            content_type="application/json",
            delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        )
        await self._exchange.publish(message, routing_key=topic)

    async def close(self) -> None:
        await self._connection.close()


class AgentPlatformBrokerLifecycle:
    def __init__(self, runtime: AgentPlatformRuntime, config: AgentPlatformConfig) -> None:
        self._runtime = runtime
        self._config = config
        self._broker: AioPikaAnalysisBroker | None = None
        self._worker: AgentPlatformBrokerWorker | None = None
        self._publisher_task: asyncio.Task[None] | None = None

    async def start(self) -> None:
        self._broker = await AioPikaAnalysisBroker.connect(self._config)
        self._worker = AgentPlatformBrokerWorker(self._runtime, self._broker)
        await self._broker.consume_requested(self._handle_requested)
        self._publisher_task = asyncio.create_task(self._publish_completed_loop())

    async def stop(self) -> None:
        if self._publisher_task is not None:
            self._publisher_task.cancel()
            try:
                await self._publisher_task
            except asyncio.CancelledError:
                pass
        if self._broker is not None:
            await self._broker.close()

    async def _handle_requested(self, payload: dict[str, Any]) -> None:
        if self._worker is None:
            raise RuntimeError("Agent broker worker has not started")
        await asyncio.to_thread(self._worker.handle_requested, payload)

    async def _publish_completed_loop(self) -> None:
        if self._worker is None:
            raise RuntimeError("Agent broker worker has not started")
        while True:
            await self._worker.publish_pending_completed_once_async()
            await asyncio.sleep(self._config.completed_publish_interval_seconds)
