from fastapi import FastAPI
from typing import Any, Callable

from app.agent_platform.config import AgentPlatformConfig
from app.agent_platform.consumer import AnalysisRequestedConsumer
from app.agent_platform.handler_registry import AnalysisHandlerRegistry
from app.agent_platform.outbox import InMemoryExecutionOutbox, OracleExecutionOutbox
from app.agent_platform.provider_executor import ProviderExecutor
from app.agent_platform.publisher import AnalysisRequestedPublisher
from app.agent_platform.repositories import InMemoryExecutionJobRepository, OracleExecutionJobRepository
from app.agent_platform.runtime import AgentPlatformRuntime
from app.agent_platform.state_machine import ExecutionStateMachine


def create_app(
    *,
    enable_background_execution: bool = True,
    require_internal_api_key: bool = True,
    db_connection_factory: Callable[[], Any] | None = None,
) -> FastAPI:
    app = FastAPI(title="review-agent")
    agent_platform_config = AgentPlatformConfig.from_env()
    connection_factory = db_connection_factory or _build_connection_factory(agent_platform_config)
    if connection_factory is None:
        execution_job_repository = InMemoryExecutionJobRepository()
        execution_outbox = InMemoryExecutionOutbox()
    else:
        execution_job_repository = OracleExecutionJobRepository(connection_factory)
        execution_outbox = OracleExecutionOutbox(connection_factory)
    execution_message_publisher = AnalysisRequestedPublisher(
        execution_outbox,
        topic=agent_platform_config.analysis_requested_topic,
    )
    analysis_requested_consumer = AnalysisRequestedConsumer(execution_job_repository)
    execution_state_machine = ExecutionStateMachine(agent_platform_config.max_attempts)
    handler_registry = AnalysisHandlerRegistry()
    provider_executor = ProviderExecutor()
    agent_platform = AgentPlatformRuntime(
        config=agent_platform_config,
        analysis_requested_consumer=analysis_requested_consumer,
        execution_message_publisher=execution_message_publisher,
        execution_state_machine=execution_state_machine,
        execution_job_repository=execution_job_repository,
        handler_registry=handler_registry,
        provider_executor=provider_executor,
    )

    app.state.agent_platform_config = agent_platform_config
    app.state.agent_platform = agent_platform
    app.state.execution_message_publisher = execution_message_publisher
    app.state.analysis_requested_consumer = analysis_requested_consumer
    app.state.execution_state_machine = execution_state_machine
    app.state.handler_registry = handler_registry
    app.state.provider_executor = provider_executor

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    return app


def _build_connection_factory(config: AgentPlatformConfig) -> Callable[[], Any] | None:
    if not config.has_durable_execution_store():
        return None

    def connect() -> Any:
        import oracledb

        return oracledb.connect(
            user=config.db_user,
            password=config.db_password,
            dsn=config.db_dsn,
        )

    return connect


app = create_app()
