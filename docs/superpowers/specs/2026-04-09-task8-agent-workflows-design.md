# Task 8 Agent Workflows Design

Date: 2026-04-09
Task: Task 8 - Implement LangGraph Workflows and Result Schemas
Scope: `services/agent`

## Goal

Implement real LangGraph-backed workflows in the FastAPI agent service so that Task 7's `/agent/tasks` API can immediately execute a workflow in the background, validate the final output against task-specific schemas, generate a reviewer-safe redacted result, and persist the completed payload back into the in-memory task store.

Task 8 must not yet integrate with the main Java system's Oracle BLOB upload path. That remains deferred to Task 9.

## Confirmed Constraints

- Use real `langgraph`, not a fake orchestration layer.
- Use the Python `openai` client against an OpenAI-compatible provider by configuring `base_url`.
- The available secrets are currently in `services/agent/.env` as:
  - `OPENROUTER_API_KEY`
  - `OPENROUTER_FALLBACK_API_KEY`
- Task 8 will use `OPENROUTER_API_KEY` only.
- Task 8 must start workflow execution immediately after `POST /agent/tasks`.
- Task 8 continues to accept JSON metadata only.
- Multipart metadata + PDF upload remains deferred to Task 9.

## Non-Goals

- No Oracle persistence from the agent service.
- No multipart file handling.
- No polling scheduler or callback integration to the Java API.
- No provider failover logic using `OPENROUTER_FALLBACK_API_KEY`.
- No long-running queue system, worker pool, or distributed execution.

## Runtime Dependencies

Add these Python dependencies to `services/agent/pyproject.toml`:

- `langgraph`
- `openai`
- `python-dotenv`

Keep the current FastAPI and pytest stack.

## Environment Model

Task 8 uses these environment variables:

- `OPENROUTER_API_KEY`
  Required for any live workflow execution.
- `OPENROUTER_BASE_URL`
  Optional.
  Default: `https://openrouter.ai/api/v1`
- `OPENROUTER_MODEL`
  Required for execution.
  Task 8 will not hardcode a model name in Python.

Behavior when configuration is incomplete:

- If `OPENROUTER_API_KEY` or `OPENROUTER_MODEL` is missing, the service should still boot.
- The task runner should mark the task as `FAILED` with a clear configuration error when execution starts.

This keeps local development bootable while preserving explicit runtime failure semantics.

## High-Level Architecture

Task 8 adds these agent modules:

- `app/workflows/router.py`
- `app/workflows/schemas.py`
- `app/workflows/coordinator.py`
- `app/workflows/paper_understanding.py`
- `app/workflows/review_assist.py`
- `app/workflows/conflict_analysis.py`
- `app/redaction.py`

Responsibilities:

- `router.py`
  Selects the workflow for a given `TASK_TYPE`.
  Builds the initial state from the stored task record.
- `schemas.py`
  Defines the shared intermediate representation and task-specific result schemas.
- `coordinator.py`
  Runs workflows in a background thread, updates task status, catches failures, and stores final results.
- `paper_understanding.py`
  Defines the shared LangGraph subgraph for normalizing manuscript context into a stable intermediate representation.
- `review_assist.py`
  Defines the `REVIEW_ASSIST_ANALYSIS` graph.
- `conflict_analysis.py`
  Defines the `DECISION_CONFLICT_ANALYSIS` graph.
- `router.py`
  Also contains the `SCREENING_ANALYSIS` graph because the plan does not allocate a separate `screening.py`.
- `redaction.py`
  Produces reviewer-safe redacted outputs from raw workflow outputs.

## Task Store Changes

Task 7 already introduced the in-memory `TaskStore`.
Task 8 extends its usage rather than replacing it.

`TaskRecord` is the authoritative execution record for the agent process and must carry:

- `task_id`
- `task_type`
- `manuscript_id`
- `version_id`
- `round_id`
- `request_payload`
- `status`
- `step`
- `error`
- `result`

Task 8 uses these status transitions:

- `PENDING -> PROCESSING`
- `PROCESSING -> SUCCESS`
- `PROCESSING -> FAILED`

`result` is stored only on success and has this shape:

```json
{
  "resultType": "string",
  "rawResult": {},
  "redactedResult": {}
}
```

## OpenAI Client Strategy

Task 8 uses the official `openai` Python client with a custom `base_url`.

Construction pattern:

```python
from openai import OpenAI

client = OpenAI(
    api_key=api_key,
    base_url=base_url,
)
```

The provider is OpenRouter, but the client remains the official OpenAI SDK.

Task 8 does not add a provider abstraction. A small factory function inside the workflow/coordinator layer is sufficient.

## LangGraph Execution Model

Each task type gets a compiled LangGraph graph.

Task 8 uses real `StateGraph` instances with typed state.
The graphs remain intentionally shallow:

- one shared `paper_understanding` stage where relevant
- one task-specific analysis stage
- one validation stage
- one redaction stage

This is enough to satisfy the design spec without prematurely splitting each scoring dimension into separate specialist subgraphs.

### Background Execution

`POST /agent/tasks` should continue to create a task synchronously, but after storing the task it must immediately trigger background execution.

Recommended implementation:

- keep `create_app(enable_background_execution: bool = True)`
- production/default behavior: `True`
- tests that need deterministic `PENDING` reads can pass `False`

When background execution is enabled:

1. create task with `PENDING`
2. spawn a background thread
3. background runner loads the task from `TaskStore`
4. update to `PROCESSING`
5. run the selected graph
6. validate final result
7. generate redacted result
8. write `SUCCESS` + result, or `FAILED` + error

This preserves Task 7's API contract while enabling real execution in Task 8.

## Shared Intermediate Representation

The shared paper-understanding representation is the normalized input for workflows that analyze manuscript content.

```python
class PaperUnderstanding(TypedDict):
    manuscriptId: str
    versionId: str
    title: str
    abstractSummary: str
    keywords: list[str]
    researchProblem: str
    claimedContributions: list[str]
    methodSummary: str
    experimentSummary: str
    mainResults: list[str]
    limitationsMentioned: list[str]
    citationSignals: list[str]
    possibleBlindnessRisks: list[str]
    sections: dict[str, str]
```

Task 8 does not parse PDFs yet.
This intermediate representation is produced from the JSON `request_payload`.

If a required input field is missing, the node should degrade gracefully and fill a conservative empty value rather than crash, unless the entire task lacks minimum required context.

## Workflow States

### Shared Base State

All workflows should share a common minimum state shape:

- `task_id`
- `task_type`
- `request_payload`
- `paper_understanding`
- `raw_result`
- `redacted_result`
- `error`

Not every workflow needs all fields populated, but using a consistent state family simplifies coordinator integration.

### Screening Workflow

Task type: `SCREENING_ANALYSIS`

Graph shape:

1. `paper_understanding`
2. `screening_assess`
3. `validate_screening_result`
4. `redact_screening_result`

Output schema must include at least:

- `scope_fit`
- `summary`
- `desk_reject_risk`
- `blindness_risks`
- `confidence`

This directly supports the plan test `test_screening_schema_has_scope_fit`.

### Review Assist Workflow

Task type: `REVIEW_ASSIST_ANALYSIS`

Graph shape:

1. `paper_understanding`
2. `review_assist_assess`
3. `validate_review_assist_result`
4. `redact_review_assist_result`

Output schema must include:

- `novelty_score`
- `method_score`
- `experiment_score`
- `writing_score`
- `overall_score`
- `strengths`
- `weaknesses`
- `recommendation`

All score fields must be integers in the range `1-5`.

This directly supports the plan test `test_review_assist_schema_requires_integer_scores`.

### Decision Conflict Workflow

Task type: `DECISION_CONFLICT_ANALYSIS`

Graph shape:

1. `conflict_summarize`
2. `validate_conflict_result`
3. `redact_conflict_result`

This workflow does not require the full paper-understanding subgraph if the payload already contains reviewer summaries, score spread, or conflicting recommendations.

Output schema must include:

- `consensus`
- `conflicts`
- `chair_summary`
- `missing_information`

This directly supports the plan test `test_conflict_schema_has_consensus_and_conflicts`.

## Schema Validation

Task 8 must validate final outputs with `Pydantic` models defined in `schemas.py`.

Validation rules:

- review-assist scores are integers only
- review-assist scores must be `1-5`
- screening result must include `scope_fit`
- conflict result must include both `consensus` and `conflicts`

If validation fails:

- do not write a partial success result
- mark task `FAILED`
- store a readable validation error in `error`

## Redaction

Task 8 must emit both raw and redacted results.

Redaction rules for this task:

- preserve structure
- remove fields that could expose internal chain-of-thought style reasoning
- keep reviewer-facing summaries concise and neutral
- keep explicit blindness-risk findings in screening output
- keep score fields in review-assist output
- keep consensus/conflict summaries in decision-conflict output

Task 8 does not need a complex policy engine.
Simple deterministic field-level projection functions in `redaction.py` are sufficient.

## Router Design

`router.py` should expose a single selection entrypoint, for example:

- `select_workflow(task_type: str) -> CompiledStateGraph`
- `build_initial_state(task_record: TaskRecord) -> dict`

The router is not an LLM component.
It only:

- validates supported task types
- chooses the correct compiled graph
- prepares the initial state

Unsupported `TASK_TYPE` must fail fast and mark the task `FAILED`.

## Coordinator Design

`coordinator.py` should expose the background execution entrypoint used by the FastAPI route layer.

Responsibilities:

- create the OpenAI client lazily
- read config from environment
- update task status in `TaskStore`
- invoke the selected LangGraph workflow
- normalize final graph output into the stored result envelope
- catch and persist errors

Failure handling:

- unsupported task type -> `FAILED`
- missing API key/model -> `FAILED`
- model/provider error -> `FAILED`
- schema validation error -> `FAILED`
- unexpected exception -> `FAILED`

Task 8 does not add retries. One execution attempt is enough.

## Prompting Strategy

Task 8 should keep prompts short and deterministic.

Use role-specific instructions in each analysis node:

- screening: venue fit, obvious desk-reject risk, blindness concerns
- review assist: structured review scoring and concise strengths/weaknesses
- conflict analysis: summarize agreement, disagreement, and missing evidence

Do not ask the model for free-form essays.
Ask for compact JSON-shaped outputs that can be validated after parsing.

## Testing Strategy

Task 8 plan already requires:

- `test_review_assist_schema_requires_integer_scores`
- `test_screening_schema_has_scope_fit`
- `test_conflict_schema_has_consensus_and_conflicts`

Add one small router test as part of the same test module:

- `test_router_selects_graph_for_each_task_type`

Testing boundaries:

- schema tests should not depend on live network calls
- router tests should not depend on real model execution
- live execution can be covered separately after the structural tests are green

For implementation, prefer:

- direct schema unit tests
- router selection unit tests
- one integration-level test that uses `create_app(enable_background_execution=False)` where needed to keep Task 7 behavior deterministic

## Deferred Work

Explicitly deferred beyond Task 8:

- multipart upload with PDF file bytes
- PDF extraction tools
- Oracle-backed task/result persistence
- polling integration from Java
- retry/fallback behavior using `OPENROUTER_FALLBACK_API_KEY`
- durable execution and checkpoint persistence
- deeper multi-node specialist decomposition

## Acceptance Criteria

Task 8 is complete when:

1. the agent service depends on real `langgraph` and `openai`
2. each supported `TASK_TYPE` maps to a real compiled LangGraph workflow
3. final workflow results are validated against task-specific schemas
4. the service produces both raw and redacted outputs
5. background execution updates `TaskStore` statuses through `PROCESSING` to terminal states
6. the schema tests and router tests pass

