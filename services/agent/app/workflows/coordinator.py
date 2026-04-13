from __future__ import annotations

import os
from concurrent.futures import ThreadPoolExecutor
from threading import BoundedSemaphore

from dotenv import load_dotenv
from openai import OpenAI

from app.task_store import TaskStore
from app.workflows.router import build_initial_state, select_workflow

load_dotenv()


class WorkflowCoordinator:
    def __init__(self, task_store: TaskStore, *, enable_background_execution: bool = True) -> None:
        self._task_store = task_store
        self._enable_background_execution = enable_background_execution
        self._executor = ThreadPoolExecutor(max_workers=_max_concurrent_tasks())
        self._semaphore = BoundedSemaphore(_max_concurrent_tasks())

    def start_task(self, task_id: str) -> None:
        if not self._enable_background_execution:
            return
        self._executor.submit(self.run_task, task_id)

    def run_task(self, task_id: str) -> None:
        task = self._task_store.get_task(task_id)
        if task is None:
            return

        self._task_store.update_status(task_id, status="PROCESSING", step="analyzing")
        try:
            client, model = self._openrouter_client()
            with self._semaphore:
                workflow = select_workflow(task.task_type)
                initial_state = build_initial_state(task)
                initial_state["llm_client"] = client
                initial_state["llm_model"] = model
                final_state = workflow.invoke(initial_state)
            raw_result = final_state["raw_result"]
            redacted_result = final_state["redacted_result"]
            self._task_store.set_result(
                task_id,
                {
                    "resultType": task.task_type,
                    "rawResult": raw_result,
                    "redactedResult": redacted_result,
                },
                step="completed",
            )
        except Exception as exc:
            self._task_store.update_status(task_id, status="FAILED", step="failed", error=str(exc))

    def _openrouter_client(self) -> tuple[OpenAI, str]:
        api_key = os.getenv("OPENROUTER_API_KEY")
        model = os.getenv("OPENROUTER_MODEL")
        if not api_key:
            raise RuntimeError("OPENROUTER_API_KEY is required for workflow execution")
        if not model:
            raise RuntimeError("OPENROUTER_MODEL is required for workflow execution")
        client = OpenAI(api_key=api_key, base_url=os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"))
        return client, model


def _max_concurrent_tasks() -> int:
    raw_value = os.getenv("AGENT_MAX_CONCURRENT_TASKS", "2")
    try:
        value = int(raw_value)
    except ValueError:
        return 2
    return max(value, 1)
