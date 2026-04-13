from fastapi import FastAPI

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
