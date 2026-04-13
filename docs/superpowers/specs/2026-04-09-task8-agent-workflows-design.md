# Task 8 Agent Workflows Design

Date: 2026-04-09
Task: Task 8 - Implement LangGraph Workflows and Result Schemas
Scope: `services/agent`

## Goal

Add the smallest safe LangGraph-backed execution path behind the existing `/agent/tasks` API.

Task 8 must:

- protect live external-cost routes with an internal API key
- run real LangGraph workflows through the OpenAI Python client configured for OpenRouter
- reuse duplicate in-memory tasks when the effective input is unchanged
- cap concurrent live executions inside the process
- validate outputs against the authoritative system schema
- store both raw and reviewer-safe redacted results in the in-memory task store

Task 8 still accepts JSON metadata only. Multipart PDF upload, PDF parsing, Oracle persistence, Java polling integration, durable queues, retries, and OpenRouter fallback-key behavior remain deferred.

## Runtime Dependencies

Add to `services/agent/pyproject.toml`:

- `langgraph`
- `openai`
- `python-dotenv`

## Environment

- `AGENT_INTERNAL_API_KEY`
  Required when `require_internal_api_key=True`.
- `OPENROUTER_API_KEY`
  Required only when a live workflow starts.
- `OPENROUTER_BASE_URL`
  Optional, default `https://openrouter.ai/api/v1`.
- `OPENROUTER_MODEL`
  Required only when a live workflow starts.
- `AGENT_MAX_CONCURRENT_TASKS`
  Optional positive integer, default `2`.

Behavior:

- `/health` remains unauthenticated.
- `/agent/tasks*` requires `X-Agent-Api-Key` when `require_internal_api_key=True`.
- Missing or wrong `X-Agent-Api-Key` returns `401`.
- If `require_internal_api_key=True` and `AGENT_INTERNAL_API_KEY` is unset, `/agent/tasks*` returns `503`.
- If OpenRouter key or model is missing, the service still boots; the task runner marks the task `FAILED` when execution starts.
- Tests may call `create_app(enable_background_execution=False, require_internal_api_key=False)`.

## API Additions

`POST /agent/tasks` keeps the Task 7 JSON shape and adds one optional field:

```json
{
  "taskType": "SCREENING_ANALYSIS",
  "manuscriptId": 1,
  "versionId": 1,
  "roundId": null,
  "requestPayload": {},
  "force": false
}
```

`force=false` reuses an existing task with the same cache key.
`force=true` bypasses cache reuse and creates a fresh task.

## Task Store and Cache

`TaskRecord` must carry:

- `task_id`
- `task_type`
- `manuscript_id`
- `version_id`
- `round_id`
- `request_payload`
- `force`
- `status`
- `step`
- `error`
- `result`
- `input_fingerprint`
- `cache_key`
- `workflow_revision`

Use these statuses:

- `PENDING`
- `PROCESSING`
- `SUCCESS`
- `FAILED`

Use these steps:

- `queued`
- `understanding`
- `analyzing`
- `summarizing`
- `validating`
- `completed`
- `failed`

`parsing` remains reserved for Task 9 PDF ingestion and is not used in Task 8.

`workflow_revision` is a simple manual constant, for example `task8-v1`. Do not build a dynamic revision system in Task 8.

Compute:

- `input_fingerprint = sha256(canonical_json(request_payload))`
- `cache_key = task_type + manuscript_id + version_id + round_id + workflow_revision + input_fingerprint`

Reuse rules:

- Same cache key with `PENDING` or `PROCESSING`: return the existing task.
- Same cache key with `SUCCESS`: return the existing task and result.
- Same cache key with `FAILED`: return the failed task unless `force=true`.
- `force=true`: create a new task regardless of cache state.

The cache index stays in memory for Task 8.

## Concurrency

Use the smallest process-local concurrency guard:

- one in-process executor or semaphore
- maximum live executions from `AGENT_MAX_CONCURRENT_TASKS`, default `2`
- no external queue
- no durable checkpointing
- no retry scheduler

This is a cost and stability guard, not a full job system.

## Result Schema Contract

Final raw results must match the authoritative main-system schema names exactly.

### Common Fields

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

`DECISION_CONFLICT_ANALYSIS` is round-scoped. Missing `roundId` fails validation.

Stored result envelope:

```json
{
  "resultType": "SCREENING_ANALYSIS",
  "rawResult": {},
  "redactedResult": {}
}
```

## Workflow Shape

Use real `StateGraph` instances, but keep each graph shallow:

- `SCREENING_ANALYSIS`: understand paper -> analyze screening -> validate -> redact
- `REVIEW_ASSIST_ANALYSIS`: understand paper -> analyze review assist -> validate -> redact
- `DECISION_CONFLICT_ANALYSIS`: summarize conflicts -> validate -> redact

The paper-understanding node builds a conservative intermediate representation from JSON `requestPayload`; Task 8 does not parse PDFs.

The router is plain Python, not an LLM component. It only selects a supported workflow, builds initial state, and enforces required identifiers such as `roundId`.

## Redaction

Task 8 redaction is intentionally simple:

- Prompt the model not to repeat author names, institution names, acknowledgements, grant names, self-citation identity clues, or direct author-history references.
- Project only reviewer-safe fields into `redactedResult`.
- Sanitize reviewer-visible strings and string lists with deterministic pattern checks for identity-bearing phrases.
- If a free-text field looks unsafe, replace it with a conservative generic summary instead of returning the original text.

Do not build a full NLP policy engine in Task 8.

## Coordinator

The coordinator:

1. updates task to `PROCESSING`
2. checks OpenRouter config
3. waits for the process-local concurrency guard
4. invokes the selected LangGraph workflow
5. validates the raw result with Pydantic
6. builds the redacted result
7. stores `SUCCESS` with the result envelope, or `FAILED` with a readable error

No retries are added in Task 8.

## Tests

Required tests:

- schema validation rejects non-integer or out-of-range review-assist scores
- screening schema requires `scopeFit`
- conflict schema requires `consensusPoints` and `conflictPoints`
- `DECISION_CONFLICT_ANALYSIS` requires `roundId`
- router selects each supported task type
- auth rejects missing or wrong `X-Agent-Api-Key`
- unset `AGENT_INTERNAL_API_KEY` returns `503` when auth is required
- duplicate requests with the same cache key reuse the existing task
- changed `requestPayload` produces a different cache key
- `force=true` creates a fresh task
- redaction sanitizes identity clues

Tests must not require live OpenRouter calls.

## Acceptance Criteria

Task 8 is complete when:

1. `langgraph`, `openai`, and `python-dotenv` are declared.
2. `/agent/tasks*` is protected before live execution is enabled.
3. cache reuse uses `input_fingerprint` and supports `force=true`.
4. live execution is bounded by a simple in-process concurrency guard.
5. supported task types map to real compiled LangGraph workflows.
6. results validate against the authoritative schema names.
7. redacted results include deterministic content sanitization.
8. task status and step reach terminal `completed` or `failed`.
9. schema, router, auth, cache, and redaction tests pass without network access.
