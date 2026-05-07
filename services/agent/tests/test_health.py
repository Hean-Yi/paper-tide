from fastapi.testclient import TestClient

from app.main import create_app


def test_health_endpoint_returns_ok() -> None:
    client = TestClient(create_app())

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_liveness_endpoint_returns_alive() -> None:
    client = TestClient(create_app())

    response = client.get("/health/liveness")

    assert response.status_code == 200
    assert response.json() == {"status": "alive"}


def test_readiness_endpoint_reports_memory_store_when_oracle_is_not_configured() -> None:
    client = TestClient(create_app())

    response = client.get("/health/readiness")

    assert response.status_code == 200
    assert response.json() == {"status": "ready", "executionStore": "memory"}
