# Task 8 Agent Workflows Design

Date: 2026-04-09
Task: Task 8 - Implement LangGraph Workflows and Result Schemas
Scope: `services/agent`

## Goal

Implement real LangGraph-backed workflows in the FastAPI agent service so that Task 7's `/agent/tasks` API can authenticate the caller, reuse cached work when possible, immediately execute a workflow in the background, validate the final output against the authoritative task-specific schemas, generate reviewer-safe redacted results, and persist the completed payload back into the in-memory task store.

Task 8 must not yet integrate with the main Java system's Oracle BLOB upload path. That remains deferred to Task 9.

## Confirmed Constraints

- Use real `langgraph`, not a fake orchestration layer.
- Use the Python `openai` client against an OpenAI-compatible provider by configuring `base_url`.
- The available secrets are currently in `services/agent/.env` as:
  - `OPENROUTER_API_KEY`
  - `OPENROUTER_FALLBACK_API_KEY`
- Task 8 will use `OPENROUTER_API_KEY` only.
- Task 8 must start workflow execution immediately after authenticated `POST /agent/tasks`.
- Task 8 continues to accept JSON metadata only.
- Multipart metadata + PDF upload remains deferred to Task 9.
- Task 8 must not expose a live external-cost execution route without service-to-service authentication.

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

## Service-to-Service Authentication

Task 8 brings live external-cost execution into scope, so service authentication must be included before background execution is enabled.

Agent routes under `/agent/tasks` must require an internal API key header:

- header name: `X-Agent-Api-Key`
- environment variable: `AGENT_INTERNAL_API_KEY`

Rules:

- `/health` remains unauthenticated
- `POST /agent/tasks` requires the header
- `GET /agent/tasks/{taskId}` requires the header
- `GET /agent/tasks/{taskId}/result` requires the header

Behavior:

- missing or wrong key returns `401`
- if `AGENT_INTERNAL_API_KEY` is unset, the service may still boot, but authenticated task execution must not be considered enabled

For deterministic tests, the app factory should allow auth to be disabled explicitly, for example:

- `create_app(enable_background_execution=False, require_internal_api_key=False)`

Task 9 will supply the real key from the Java side.

## Environment Model

Task 8 uses these environment variables:

- `OPENROUTER_API_KEY`
  Required for live workflow execution.
- `OPENROUTER_BASE_URL`
  Optional.
  Default: `https://openrouter.ai/api/v1`
- `OPENROUTER_MODEL`
  Required for execution.
  Task 8 will not hardcode a model name in Python.
- `AGENT_INTERNAL_API_KEY`
  Required for authenticated access to `/agent/tasks*` in non-test operation.

Behavior when LLM configuration is incomplete:

- if `OPENROUTER_API_KEY` or `OPENROUTER_MODEL` is missing, the service should still boot
- the task runner should mark the task as `FAILED` with a clear configuration error when execution starts

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
  Selects the workflow for a given `TASK_TYPE`, computes workflow revision, and builds initial state from the stored task record.
- `schemas.py`
  Defines the shared intermediate representation and the task-specific result schemas that must match the authoritative main-system design.
- `coordinator.py`
  Runs workflows in a background thread, updates task status and step, catches failures, and stores final results.
- `paper_understanding.py`
  Defines the shared LangGraph subgraph for normalizing manuscript context into a stable intermediate representation.
- `review_assist.py`
  Defines the `REVIEW_ASSIST_ANALYSIS` graph.
- `conflict_analysis.py`
  Defines the `DECISION_CONFLICT_ANALYSIS` graph.
- `router.py`
  Also contains the `SCREENING_ANALYSIS` graph because the plan does not allocate a separate `screening.py`.
- `redaction.py`
  Produces reviewer-safe redacted outputs from raw workflow outputs, including content-level sanitization of identity-bearing clues.

## Task Store Changes and Cache Semantics

Task 7 already introduced the in-memory `TaskStore`.
Task 8 extends it rather than replacing it.

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
- `cache_key`
- `workflow_revision`

Task 8 keeps `status` aligned with the Oracle `AGENT_ANALYSIS_TASK` table:

- `PENDING`
- `PROCESSING`
- `SUCCESS`
- `FAILED`

Task 8 standardizes `step` to the design-spec enum:

- `queued`
- `parsing`
- `understanding`
- `analyzing`
- `summarizing`
- `validating`
- `completed`
- `failed`

Task 8 actively uses this subset:

- create task: `queued`
- paper-understanding node: `understanding`
- task-specific analysis node: `analyzing`
- result shaping node: `summarizing`
- schema validation node: `validating`
- success terminal: `completed`
- failure terminal: `failed`

`parsing` is reserved until Task 9 adds multipart PDF handling.

`result` is stored only on success and has this shape:

```json
{
  "resultType": "string",
  "rawResult": {},
  "redactedResult": {}
}
```

### De-duplication and Cache Reuse

Task 8 must not create a new UUID task for every repeated request.

Before creating a task, the service computes a deterministic `cache_key` from:

- `task_type`
- `manuscript_id`
- `version_id`
- `round_id`
- `workflow_revision`

`workflow_revision` is a stable version string derived from:

- prompt version
- schema version
- redaction version
- model name

This lets the system invalidate cached results cleanly when the workflow contract changes.

Reuse policy:

- if an existing task with the same cache key is `PENDING` or `PROCESSING`, return that existing task instead of creating a duplicate
- if an existing task with the same cache key is `SUCCESS`, return that existing task and reuse its stored result
- if an existing task with the same cache key is `FAILED`, create a new task only when the caller explicitly retries or when `workflow_revision` changed

Task 8 can keep the cache index in memory because persistence is still deferred, but the cache-key semantics must be fixed now so Task 9 and the frontend do not guess.

## Result Schema Contract

Task 8 must align exactly with the authoritative output schema in the main system design document. These names are contractual and must not be flattened or renamed.

### Common Envelope Fields

- `taskType`
- `manuscriptId`
- `versionId`
- `status`
- `confidence`

### `SCREENING_ANALYSIS`

```json
{
  "taskType": "SCREENING_ANALYSIS",
  "manuscriptId": "string",
  "versionId": "string",
  "topicCategory": "string",
  "scopeFit": "FIT|PARTIAL|UNFIT",
  "formatRisks": ["string"],
  "blindnessRisks": ["string"],
  "screeningSummary": "string",
  "confidence": "number"
}
```

### `REVIEW_ASSIST_ANALYSIS`

```json
{
  "taskType": "REVIEW_ASSIST_ANALYSIS",
  "manuscriptId": "string",
  "versionId": "string",
  "summary": "string",
  "novelty": {
    "analysis": "string",
    "score": "integer(1-5)"
  },
  "methodology": {
    "analysis": "string",
    "score": "integer(1-5)"
  },
  "writing": {
    "analysis": "string",
    "score": "integer(1-5)"
  },
  "risks": ["string"],
  "finalSuggestion": "string",
  "confidence": "number"
}
```

### `DECISION_CONFLICT_ANALYSIS`

```json
{
  "taskType": "DECISION_CONFLICT_ANALYSIS",
  "manuscriptId": "string",
  "versionId": "string",
  "consensusPoints": ["string"],
  "conflictPoints": ["string"],
  "highRiskIssues": ["string"],
  "decisionSummary": "string",
  "confidence": "number"
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

- keep `create_app(enable_background_execution: bool = True, require_internal_api_key: bool = True)`
- production/default behavior: both `True`
- tests that need deterministic reads can disable background execution and auth explicitly

When background execution is enabled:

1. authenticate caller
2. compute cache key and reuse an existing task when eligible
3. create task with `PENDING` and step `queued` only if no reusable task exists
4. spawn a background thread
5. background runner loads the task from `TaskStore`
6. update to `PROCESSING`
7. run the selected graph
8. validate final result
9. generate redacted result
10. write `SUCCESS` + result, or `FAILED` + error

This preserves Task 7's API contract while making execution conditional on auth and de-duplication checks.

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

Output schema must match:

- `topicCategory`
- `scopeFit`
- `formatRisks`
- `blindnessRisks`
- `screeningSummary`
- `confidence`

This directly supports the plan test `test_screening_schema_has_scope_fit`.

### Review Assist Workflow

Task type: `REVIEW_ASSIST_ANALYSIS`

Graph shape:

1. `paper_understanding`
2. `review_assist_assess`
3. `validate_review_assist_result`
4. `redact_review_assist_result`

Output schema must match:

- `summary`
- `novelty.analysis`
- `novelty.score`
- `methodology.analysis`
- `methodology.score`
- `writing.analysis`
- `writing.score`
- `risks`
- `finalSuggestion`
- `confidence`

All score fields remain integers in the range `1-5`.

This directly supports the plan test `test_review_assist_schema_requires_integer_scores`.

### Decision Conflict Workflow

Task type: `DECISION_CONFLICT_ANALYSIS`

Graph shape:

1. `conflict_summarize`
2. `validate_conflict_result`
3. `redact_conflict_result`

This workflow is round-scoped, not only version-scoped.
`round_id` is required and router/state validation must fail if it is missing.

Output schema must match:

- `consensusPoints`
- `conflictPoints`
- `highRiskIssues`
- `decisionSummary`
- `confidence`

This directly supports the plan test `test_conflict_schema_has_consensus_and_conflicts`.

## Schema Validation

Task 8 must validate final outputs with `Pydantic` models defined in `schemas.py`.

Validation rules:

- review-assist nested scores are integers only
- review-assist nested scores must be `1-5`
- screening result must include `scopeFit`
- conflict result must include both `consensusPoints` and `conflictPoints`
- `DECISION_CONFLICT_ANALYSIS` must reject missing `round_id`

If validation fails:

- do not write a partial success result
- mark task `FAILED`
- store a readable validation error in `error`

## Redaction

Task 8 must emit both raw and redacted results.

Redaction in Task 8 must do two things:

1. prompt-level prevention
2. deterministic result sanitization

Prompt-level prevention:

- instruct the model not to repeat author names, institution names, acknowledgements, grant names, self-citation identity clues, or direct author-history references
- instruct the model to summarize evidence without identity-bearing quotations

Deterministic sanitization:

- sanitize all free-text fields that may be shown to reviewers
- inspect summary-like strings and list items for identity-bearing phrases
- mask or drop suspicious fragments involving:
  - institutions
  - acknowledgements
  - grant or project names
  - explicit self-citation clues
  - author-name-like references

If sanitization cannot confidently produce a safe reviewer-facing text field, the redacted output should replace that field with a conservative generic summary instead of leaking the original text.

Task 8 does not need a full NLP policy engine, but field projection alone is not sufficient.
`redaction.py` must include content-level text sanitization for reviewer-visible narrative fields.

## Router Design

`router.py` should expose a single selection entrypoint, for example:

- `select_workflow(task_type: str) -> CompiledStateGraph`
- `build_initial_state(task_record: TaskRecord) -> dict`
- `workflow_revision_for(task_record: TaskRecord) -> str`

The router is not an LLM component.
It only:

- validates supported task types
- chooses the correct compiled graph
- prepares the initial state
- enforces per-task required identifiers such as `round_id` for `DECISION_CONFLICT_ANALYSIS`

Unsupported `TASK_TYPE` must fail fast and mark the task `FAILED`.

## Coordinator Design

`coordinator.py` should expose the background execution entrypoint used by the FastAPI route layer.

Responsibilities:

- create the OpenAI client lazily
- read config from environment
- update task status and step in `TaskStore`
- invoke the selected LangGraph workflow
- normalize final graph output into the stored result envelope
- catch and persist errors

Failure handling:

- unsupported task type -> `FAILED`
- missing `round_id` for conflict analysis -> `FAILED`
- missing API key/model -> `FAILED`
- model/provider error -> `FAILED`
- schema validation error -> `FAILED`
- unexpected exception -> `FAILED`

Task 8 does not add retries. One execution attempt is enough.

## Prompting Strategy

Task 8 should keep prompts short and deterministic.

Use role-specific instructions in each analysis node:

- screening: venue fit, formatting risk, blindness concerns, no identity-bearing wording
- review assist: structured nested scoring, concise evidence summaries, no identity-bearing wording
- conflict analysis: summarize agreement and disagreement across a round, no author or institution clues

Do not ask the model for free-form essays.
Ask for compact JSON-shaped outputs that can be validated after parsing.

## Testing Strategy

Task 8 plan already requires:

- `test_review_assist_schema_requires_integer_scores`
- `test_screening_schema_has_scope_fit`
- `test_conflict_schema_has_consensus_and_conflicts`

Add:

- `test_router_selects_graph_for_each_task_type`
- `test_decision_conflict_requires_round_id`
- `test_redaction_sanitizes_identity_clues`

Testing boundaries:

- schema tests should not depend on live network calls
- router tests should not depend on real model execution
- auth tests should not depend on external model calls
- live execution can be covered separately after the structural tests are green

For implementation, prefer:

- direct schema unit tests
- router selection unit tests
- auth-guard tests for `X-Agent-Api-Key`
- one integration-level test that uses `create_app(enable_background_execution=False, require_internal_api_key=False)` where needed to keep Task 7 behavior deterministic

## Deferred Work

Explicitly deferred beyond Task 8:

- multipart upload with PDF file bytes
- PDF extraction tools
- Oracle-backed task/result persistence
- polling integration from Java
- retry and failover behavior using `OPENROUTER_FALLBACK_API_KEY`
- durable execution and checkpoint persistence
- deeper multi-node specialist decomposition
- moving `parsing` into active use before Task 9's PDF ingestion path exists

## Acceptance Criteria

Task 8 is complete when:

1. the agent service depends on real `langgraph` and `openai`
2. `/agent/tasks*` is protected by an internal API key before live execution is enabled
3. duplicate requests for the same natural task key reuse cache instead of spawning duplicate external calls
4. each supported `TASK_TYPE` maps to a real compiled LangGraph workflow
5. final workflow results match the authoritative schema contract from the main design document
6. the service produces both raw and redacted outputs, with content-level sanitization for reviewer-visible text
7. background execution updates `TaskStore` statuses through `PROCESSING` to terminal states while `step` follows the fixed enum
8. the schema, router, auth, and redaction tests pass
