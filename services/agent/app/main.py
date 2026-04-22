from fastapi import FastAPI

from app.agent_platform.config import AgentPlatformConfig
from app.agent_platform.consumer import AnalysisRequestedConsumer
from app.agent_platform.outbox import InMemoryExecutionOutbox
from app.agent_platform.publisher import AnalysisRequestedPublisher
from app.agent_platform.repositories import InMemoryExecutionJobRepository
from app.agent_platform.state_machine import ExecutionStateMachine
from app.routes.tasks import build_tasks_router
from app.task_store import TaskStore
from app.workflows.coordinator import WorkflowCoordinator


def create_app(
    *,
    enable_background_execution: bool = True,
    require_internal_api_key: bool = True,
    task_store: TaskStore | None = None,
) -> FastAPI:
    app = FastAPI(title="review-agent")
    task_store = task_store or TaskStore()
    coordinator = WorkflowCoordinator(task_store, enable_background_execution=enable_background_execution)
    agent_platform_config = AgentPlatformConfig.from_env()
    execution_job_repository = InMemoryExecutionJobRepository()
    execution_outbox = InMemoryExecutionOutbox()
    execution_message_publisher = AnalysisRequestedPublisher(
        execution_outbox,
        topic=agent_platform_config.analysis_requested_topic,
    )
    analysis_requested_consumer = AnalysisRequestedConsumer(execution_job_repository)
    execution_state_machine = ExecutionStateMachine(agent_platform_config.max_attempts)

    app.state.agent_platform_config = agent_platform_config
    app.state.execution_job_repository = execution_job_repository
    app.state.execution_outbox = execution_outbox
    app.state.execution_message_publisher = execution_message_publisher
    app.state.analysis_requested_consumer = analysis_requested_consumer
    app.state.execution_state_machine = execution_state_machine

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    app.include_router(
        build_tasks_router(
            task_store,
            start_task=coordinator.start_task,
            require_internal_api_key=require_internal_api_key,
        )
    )

    return app


app = create_app()
