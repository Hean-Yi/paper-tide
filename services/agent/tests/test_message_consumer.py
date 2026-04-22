import pytest

from app.agent_platform.consumer import AnalysisRequestedConsumer
from app.agent_platform.messages import AnalysisRequestedMessage
from app.agent_platform.outbox import InMemoryExecutionOutbox
from app.agent_platform.publisher import AnalysisRequestedPublisher
from app.agent_platform.repositories import InMemoryExecutionJobRepository
from app.main import create_app


def test_analysis_requested_consumer_reuses_existing_job_id() -> None:
    repository = InMemoryExecutionJobRepository()
    consumer = AnalysisRequestedConsumer(repository)
    message = {
        "idempotencyKey": "key-1",
        "analysisType": "REVIEWER_ASSIST",
        "requestPayload": {"title": "Paper"},
    }

    first = consumer.handle(message)
    second = consumer.handle(message)

    assert second.job_id == first.job_id


def test_create_app_exposes_agent_platform_components() -> None:
    app = create_app(enable_background_execution=False, require_internal_api_key=False)

    assert app.state.analysis_requested_consumer is not None
    assert app.state.agent_platform is not None
    assert not hasattr(app.state, "execution_job_repository")
    assert not hasattr(app.state, "execution_outbox")


@pytest.mark.parametrize(
    "message",
    [
        {
            "idempotencyKey": "key-1",
            "analysisType": "REVIEWER_ASSIST",
        },
        {
            "idempotencyKey": "key-1",
            "analysisType": "REVIEWER_ASSIST",
            "requestPayload": None,
        },
    ],
)
def test_analysis_requested_consumer_rejects_missing_request_payload(message: dict[str, object]) -> None:
    repository = InMemoryExecutionJobRepository()
    consumer = AnalysisRequestedConsumer(repository)

    with pytest.raises(ValueError, match="requestPayload"):
        consumer.handle(message)

    assert repository.get_by_idempotency_key("key-1") is None


def test_analysis_requested_publisher_creates_distinct_outbox_rows_for_same_job() -> None:
    outbox = InMemoryExecutionOutbox()
    publisher = AnalysisRequestedPublisher(outbox)
    message = AnalysisRequestedMessage(
        idempotency_key="key-1",
        analysis_type="REVIEWER_ASSIST",
        request_payload={"title": "Paper"},
        job_id="job-1",
    )

    first = publisher.publish(message)
    second = publisher.publish(message)

    assert first.message_id != second.message_id
    assert len(outbox.pending()) == 2


def test_outbox_payload_snapshot_is_recursively_immutable() -> None:
    outbox = InMemoryExecutionOutbox()
    publisher = AnalysisRequestedPublisher(outbox)
    message = AnalysisRequestedMessage(
        idempotency_key="key-1",
        analysis_type="REVIEWER_ASSIST",
        request_payload={"paper": {"title": "Paper"}, "sections": [{"name": "intro"}]},
    )

    published = publisher.publish(message)

    with pytest.raises(TypeError):
        published.payload["requestPayload"]["paper"]["title"] = "Changed"

    with pytest.raises(TypeError):
        published.payload["requestPayload"]["sections"][0]["name"] = "changed"
