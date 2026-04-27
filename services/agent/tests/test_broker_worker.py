import asyncio

from app.agent_platform.broker import AgentPlatformBrokerWorker, InMemoryAnalysisBroker
from app.agent_platform.config import AgentPlatformConfig
from app.agent_platform.messages import AnalysisRequestedMessage
from app.agent_platform.provider_executor import ProviderExecutor
from app.main import create_app


def test_worker_handles_requested_message_and_buffers_completed_event() -> None:
    broker = InMemoryAnalysisBroker()
    app = create_app(
        enable_background_execution=False,
        require_internal_api_key=False,
        provider_executor=ProviderExecutor(),
    )
    worker = AgentPlatformBrokerWorker(app.state.agent_platform, broker)
    requested = AnalysisRequestedMessage(
        idempotency_key="broker-key-1",
        analysis_type="REVIEWER_ASSIST",
        intent_reference="404",
        request_payload={
            "title": "Broker Paper",
            "abstract": "A paper about broker execution.",
            "reviewerAssist": {"assignmentId": 1, "roundId": 2, "manuscriptId": 3, "versionId": 4},
        },
    ).to_dict()

    event = worker.handle_requested(requested)

    assert event["eventType"] == "analysis.completed"
    assert event["intentId"] == 404
    assert len(worker.pending_completed()) == 1


def test_worker_publishes_pending_completed_events_and_marks_outbox_published() -> None:
    broker = InMemoryAnalysisBroker()
    app = create_app(
        enable_background_execution=False,
        require_internal_api_key=False,
        provider_executor=ProviderExecutor(),
    )
    worker = AgentPlatformBrokerWorker(app.state.agent_platform, broker)
    requested = AnalysisRequestedMessage(
        idempotency_key="broker-key-2",
        analysis_type="SCREENING",
        intent_reference="405",
        request_payload={
            "title": "Screening Broker Paper",
            "abstract": "A paper about broker publishing.",
            "screening": {"manuscriptId": 3, "versionId": 4},
        },
    ).to_dict()
    worker.handle_requested(requested)

    published = worker.publish_pending_completed_once()

    assert published == 1
    assert broker.published[0]["topic"] == "analysis.completed"
    assert broker.published[0]["payload"]["eventType"] == "analysis.completed"
    assert worker.pending_completed() == []


def test_worker_supports_async_completed_event_brokers() -> None:
    broker = AsyncCapturingBroker()
    app = create_app(
        enable_background_execution=False,
        require_internal_api_key=False,
        provider_executor=ProviderExecutor(),
    )
    worker = AgentPlatformBrokerWorker(app.state.agent_platform, broker)
    requested = AnalysisRequestedMessage(
        idempotency_key="broker-key-3",
        analysis_type="SCREENING",
        intent_reference="406",
        request_payload={
            "title": "Async Broker Paper",
            "abstract": "A paper about async broker publishing.",
            "screening": {"manuscriptId": 3, "versionId": 4},
        },
    ).to_dict()
    worker.handle_requested(requested)

    published = asyncio.run(worker.publish_pending_completed_once_async())

    assert published == 1
    assert broker.published[0]["topic"] == "analysis.completed"
    assert broker.published[0]["payload"]["intentId"] == 406
    assert worker.pending_completed() == []


def test_config_loads_broker_lifecycle_settings(monkeypatch) -> None:
    monkeypatch.setenv("AGENT_PLATFORM_BROKER_ENABLED", "true")
    monkeypatch.setenv("AGENT_PLATFORM_RABBITMQ_URL", "amqp://review:review@rabbitmq:5672/")
    monkeypatch.setenv("AGENT_PLATFORM_BROKER_EXCHANGE", "review.analysis.exchange")
    monkeypatch.setenv("AGENT_PLATFORM_REQUEST_QUEUE", "analysis.requested.agent")
    monkeypatch.setenv("AGENT_PLATFORM_COMPLETED_PUBLISH_INTERVAL_SECONDS", "0.25")

    config = AgentPlatformConfig.from_env("/tmp/missing-agent-env-file")

    assert config.broker_enabled is True
    assert config.rabbitmq_url == "amqp://review:review@rabbitmq:5672/"
    assert config.broker_exchange == "review.analysis.exchange"
    assert config.request_queue == "analysis.requested.agent"
    assert config.completed_publish_interval_seconds == 0.25


class AsyncCapturingBroker:
    def __init__(self) -> None:
        self.published: list[dict] = []

    async def publish(self, topic: str, payload: dict) -> None:
        self.published.append({"topic": topic, "payload": payload})
