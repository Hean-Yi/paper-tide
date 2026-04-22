# Agent Platform Boundary And Execution Refactor Design

Date: 2026-04-22
Status: Approved design
Scope: `apps/api`, `services/agent`, `database/oracle`, `apps/web`, RabbitMQ integration

## 1. Goal

Refactor the project from the current dual-task-system design into a boundary-clean architecture where:

- `apps/api` owns business intent, authorization, context aggregation, and user-facing result projections
- `services/agent` owns execution lifecycle, orchestration, retries, provider routing, and execution governance
- RabbitMQ carries explicit commands and events between the two systems
- business truth and execution truth are no longer duplicated across two mirrored task state machines

This is a source-level architecture refactor, not a compatibility patch. Breaking changes are allowed. The target is productization-prep quality, not a course-demo-only compromise.

## 2. Problem Statement

The current design has real value, but it also has structural duplication:

- `apps/api` creates local agent tasks, tracks local task status, attaches external task IDs, polls the agent service, and persists results
- `services/agent` also owns a task lifecycle in memory, also deduplicates requests, and also exposes task status/results
- both services partially behave like job systems
- LangGraph is used for shallow linear workflows where orchestration complexity is still lower than the platform complexity around it

From first principles, the current architecture mixes two distinct concerns:

- business concern: whether a reviewer, chair, or admin may request and read a given analysis
- execution concern: how an analysis request is scheduled, retried, validated, redacted, traced, and completed

The refactor must separate those concerns into stable ownership boundaries.

## 3. Architectural Decision

Adopt the approved direction:

- Architecture option: layered sovereignty
- Messaging technology: RabbitMQ
- Service shape: keep a separate agent service

### 3.1 Sovereignty split

`apps/api` owns business sovereignty:

- role-based authorization
- manuscript/assignment/round precondition checks
- business context aggregation
- analysis request intent registration
- user-facing result projection reads
- user-facing visibility rules

`services/agent` owns execution sovereignty:

- job creation and idempotent intake
- queue consumption
- workflow selection and orchestration
- retries and dead-letter handling
- provider selection and execution
- result validation
- redaction
- execution audit and metrics

RabbitMQ is transport, not truth:

- it carries commands and events
- it does not own business semantics
- it does not replace durable storage

### 3.2 Explicit non-goals

This refactor does not aim to:

- preserve current HTTP task contracts for compatibility
- keep mirrored local/external task tables
- keep in-memory task truth inside `services/agent`
- treat RabbitMQ as the primary query model
- leak internal execution details directly into user-facing workflow pages

## 4. Target Topology

```text
[Browser]
   |
   v
[Vue Web]
   |
   v
[Spring API: business intent + projections] ----> [Oracle business schema]
   |
   | publish command / consume result events
   v
[RabbitMQ]
   |
   v
[FastAPI Agent Platform: execution jobs + orchestration] ----> [Agent persistence schema or DB]
   |
   v
[LLM / model providers]
```

### 4.1 Core rule

The API no longer polls the agent service for execution progress. The agent platform no longer relies on API-owned task state. Both sides communicate through durable commands and events plus their own bounded data stores.

## 5. Business Model In `apps/api`

The API side stops modeling agent execution as a local mirror of an external task. It instead models business intent and business-visible result projections.

### 5.1 `AnalysisIntent`

Represents an approved business request to perform an analysis.

Required responsibilities:

- identify who requested the analysis
- bind the request to a business anchor
- record the requested analysis type
- store the generated idempotency key
- record whether the request is active, superseded, or failed visibly
- associate the request with the execution-side reference without mirroring the full execution state machine

Business anchors may include:

- `manuscriptId + versionId`
- `assignmentId`
- `roundId`

### 5.2 `AnalysisProjection`

Represents the business-visible projection read by the frontend.

It must answer:

- which analyses are available for a manuscript/version/assignment/round
- what summary the UI should render
- what redacted view is visible to reviewers
- whether a raw view reference is available to chairs/admins
- whether the result has been superseded by a newer request

### 5.3 Business-side states

The API should expose business-oriented states, not execution internals.

Recommended business states:

- `REQUESTED`
- `AVAILABLE`
- `FAILED_VISIBLE`
- `SUPERSEDED`

These states are intentionally smaller than execution states because user-facing UX should not depend on queue-level internals.

## 6. Execution Model In `services/agent`

The agent service becomes a real execution platform with a single durable execution truth.

### 6.1 `ExecutionJob`

This is the primary execution entity and sole authority for execution lifecycle.

Required fields:

- `jobId`
- `intentReference`
- `idempotencyKey`
- `analysisType`
- `inputSnapshot`
- `executionState`
- `attemptCount`
- `providerSelection`
- `rawResult`
- `redactedResult`
- `summaryProjection`
- `failureReason`
- timestamps

### 6.2 `ExecutionAttempt`

Tracks each attempt for retries, provider choice, duration, and worker-level diagnostics.

### 6.3 Execution-side states

The execution platform uses its own explicit state machine.

Recommended states:

- `QUEUED`
- `DISPATCHED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED_RETRYABLE`
- `FAILED_TERMINAL`
- `DEAD_LETTERED`

These states are never directly exposed to regular workflow pages.

### 6.4 Retry versus rerun

The architecture must separate two distinct semantics:

- `retry`
  Agent-platform-internal re-attempt of the same `ExecutionJob`
- `rerun`
  New business request producing a new `AnalysisIntent` and therefore a new job identity

This distinction is mandatory. A user-triggered rerun must not be modeled as an internal retry.

## 7. Idempotency Model

Current deduplication logic is technical and duplicated. The new design replaces it with business identity.

### 7.1 Idempotency key generation

`apps/api` generates an explicit `idempotencyKey` based on business semantics:

- analysis type
- business anchor
- normalized input hash
- request version or rerun sequence

This key is sent on `analysis.requested` and becomes the stable identity used by the execution platform for idempotent intake.

### 7.2 Required guarantees

The system must answer these questions deterministically:

- is this the same business analysis request as before
- is this a rerun or a retry
- did the normalized business input actually change
- should an existing execution job be reused or a new one created

No in-memory fingerprint cache may remain the truth source.

## 8. Messaging Design With RabbitMQ

### 8.1 Message categories

Commands:

- `analysis.requested`

Events:

- `analysis.accepted`
- `analysis.completed`
- `analysis.failed`
- `analysis.superseded` (optional for later phase)

### 8.2 Messaging rules

- commands originate from `apps/api`
- execution acceptance and terminal events originate from `services/agent`
- neither side infers truth by polling the other side
- both sides must treat messages as potentially duplicated and out of order

### 8.3 Recommended middleware choice

RabbitMQ is preferred over Kafka for this refactor because the immediate problem is reliable command dispatch, retries, acknowledgements, and dead-letter control for bounded execution jobs, not a high-scale immutable analytics stream.

## 9. Data Storage And Consistency Design

### 9.1 API-side storage

Oracle business schema should include:

- `ANALYSIS_INTENT`
- `ANALYSIS_PROJECTION`
- `ANALYSIS_OUTBOX`
- `ANALYSIS_INBOX`

### 9.2 Agent-side storage

Agent persistence should include:

- `EXECUTION_JOB`
- `EXECUTION_ATTEMPT`
- `EXECUTION_ARTIFACT`
- `EXECUTION_OUTBOX`
- `EXECUTION_INBOX`

Recommended deployment path:

- Phase 1: same Oracle instance, separate schema for the agent platform
- Phase 2: optional separate agent database when platform isolation becomes operationally necessary

### 9.3 Consistency rule

The system must use `Transactional Outbox + Idempotent Consumer`.

Direct "write DB then publish MQ" is not allowed.

Required flow:

API request flow:

1. create `ANALYSIS_INTENT`
2. write `ANALYSIS_OUTBOX`
3. outbox publisher sends `analysis.requested`

Agent intake flow:

1. consume message
2. dedupe via `EXECUTION_INBOX`
3. create `EXECUTION_JOB`
4. ack after durable persistence

Agent completion flow:

1. update `EXECUTION_JOB`
2. write `EXECUTION_OUTBOX`
3. publish terminal event

API result flow:

1. consume event
2. dedupe via `ANALYSIS_INBOX`
3. update `ANALYSIS_PROJECTION`
4. ack after persistence

## 10. Module Decomposition

### 10.1 `apps/api`

Refactor toward business use-case boundaries.

Recommended structure:

- `analysis/application`
  - request analysis use cases
  - projection read use cases
- `analysis/domain`
  - `AnalysisIntent`
  - `AnalysisProjection`
  - `AnalysisType`
  - request/visibility policies
- `analysis/infrastructure`
  - Oracle repositories
  - outbox publisher
  - inbox consumer handlers
- `analysis/interfaces`
  - REST controllers
  - DTO mappers

Controllers must not publish RabbitMQ messages directly and must not know execution-state details.

### 10.2 `services/agent`

Refactor toward a platform structure.

Recommended structure:

- `agent_platform/application`
  - command handlers
  - event publishers
- `agent_platform/domain`
  - `ExecutionJob`
  - `ExecutionState`
  - value objects and policies
- `agent_platform/orchestration`
  - workflow orchestration
  - task handler registry
- `agent_platform/providers`
  - provider adapters
- `agent_platform/messaging`
  - RabbitMQ consumers/publishers
  - retry and DLQ coordination
- `agent_platform/persistence`
  - job/attempt/artifact repositories

FastAPI routes must become thin transport adapters, not the application layer.

## 11. Design Pattern Decisions

The refactor should use explicit patterns where they simplify boundaries.

### 11.1 Required patterns

- `Command`
  - `AnalysisRequestedCommand`
- `Domain Event`
  - `AnalysisAcceptedEvent`
  - `AnalysisCompletedEvent`
  - `AnalysisFailedEvent`
- `Transactional Outbox`
- `Idempotent Consumer`
- `Projection`
  - API reads projections instead of execution internals
- `State`
  - explicit execution state transitions in the agent platform
- `Strategy + Registry`
  - one handler per analysis type
- `Policy Object`
  - request authorization and visibility rules
- `Adapter`
  - provider adapters and transport adapters
- `Pipeline` / `Chain of Responsibility`
  - normalize -> execute -> validate -> redact -> summarize

### 11.2 Pattern usage guidance

Patterns are not added for ceremony. Each chosen pattern replaces a current structural weakness:

- duplicate switch logic becomes `Strategy + Registry`
- implicit string-based status rules become `State`
- role-specific branching inside services becomes `Policy Object`
- ad hoc HTTP-to-internal coupling becomes `Adapter`
- unreliable cross-system writes become `Transactional Outbox`

## 12. Workflow Handling

### 12.1 Analysis handlers

Each analysis type must have a dedicated handler:

- `ScreeningAnalysisHandler`
- `ReviewerAssistHandler`
- `ConflictAnalysisHandler`

Each handler is responsible for:

- input schema definition
- workflow orchestration choice
- result validation
- redaction policy selection
- summary projection construction

### 12.2 LangGraph position

LangGraph may remain only if it stays inside the orchestration module and is justified by real workflow branching or reusable graph semantics.

It must not define service boundaries or task-system semantics.

If workflows remain shallow and linear after the platform refactor, the implementation plan should permit replacing LangGraph with a simpler pipeline abstraction later.

## 13. Authorization And Visibility Model

### 13.1 Request authorization

Introduce `AnalysisRequestPolicy`.

It must answer whether a principal may request a given analysis for a specific business anchor.

Examples:

- reviewer may request reviewer assist only for owned assignments in allowed states
- chair/admin may request conflict analysis only for valid rounds
- author may not request agent results in the current workflow slice unless explicitly introduced later

### 13.2 Result visibility

Introduce `AnalysisVisibilityPolicy`.

It must determine whether the caller may read:

- raw result
- redacted result
- summary only
- no result

This policy replaces ad hoc role checks scattered through service methods.

## 14. Error Handling Model

### 14.1 Error ownership

`apps/api` handles business errors:

- authorization failures
- invalid workflow state
- missing business anchors
- invalid client input

`services/agent` handles execution errors:

- provider timeout
- provider failure
- invalid normalized input
- schema validation failure
- redaction failure
- projection build failure

RabbitMQ handles transport semantics:

- ack
- nack
- retry
- dead-letter

### 14.2 Error taxonomy

Recommended explicit execution error classes:

- `BusinessPreconditionError`
- `InputContractError`
- `ProviderTransientError`
- `ProviderTerminalError`
- `ExecutionInfrastructureError`
- `RedactionError`
- `ProjectionBuildError`

The implementation must map these classes to retry, fail-terminal, and alerting policies.

## 15. Reliability And Operations

### 15.1 Dead-letter and retry

RabbitMQ should use:

- primary queue
- retry queue with delay/TTL
- dead-letter queue

Agent execution failure states must distinguish:

- retryable transient failure
- terminal failure
- dead-lettered exhaustion

### 15.2 Degradation and protection

The platform should support:

- provider-level circuit breaking
- bounded worker concurrency
- queue backlog monitoring
- task-type-specific protection mode if repeated failures spike

Core manuscript workflow must remain usable even when agent analysis is unavailable.

### 15.3 Service authentication

The API-to-agent trust model must move beyond a static raw header key over time.

Planned progression:

- Phase 1: signed or short-lived service token
- Phase 2: stronger service authentication such as mTLS or service-mesh identity

Raw sensitive context must never be emitted to ordinary logs.

## 16. Observability

### 16.1 Trace identity

Each analysis request must propagate:

- `traceId`
- `intentId`
- `jobId`
- `idempotencyKey`

These identifiers must appear consistently in API logs, MQ metadata, agent logs, and admin/operator views.

### 16.2 Structured logging

All critical logs should be structured with at least:

- `traceId`
- `intentId`
- `jobId`
- `analysisType`
- `businessAnchor`
- `executionState`
- `attemptNo`
- `provider`
- `durationMs`
- `errorClass`

### 16.3 Metrics

Separate business metrics and platform metrics.

Business metrics:

- analysis request volume by type
- result availability rate
- reviewer assist usage rate
- conflict analysis trigger rate
- role-based projection read counts

Platform metrics:

- queue backlog
- wait time
- execution time
- provider success/failure/timeout rates
- retry distribution
- DLQ rate
- redaction failure rate
- projection-build failure rate

## 17. UI And Read Model Impact

The frontend continues to read only through `apps/api`.

Required change in principle:

- workflow pages query `AnalysisProjection`
- workflow pages no longer need to understand mirrored execution state
- reviewer pages consume reviewer-safe projection output
- chair/admin pages may consume richer summaries or raw references according to policy

The current "reviewer safe" concept remains important and should be preserved as a stable business-visible outcome, not a soft convention.

## 18. Testing Strategy

### 18.1 Test layers

Required layers:

- unit tests for domain entities and policies
- application tests for use cases and command handlers
- adapter tests for persistence, RabbitMQ, and provider adapters
- message contract tests between API and agent platform
- end-to-end async flow tests

### 18.2 Key required proofs

The refactor is incomplete unless tests prove:

- idempotent command intake
- outbox/inbox dedupe behavior
- retry versus rerun separation
- reviewer/chair/admin visibility correctness
- dead-letter behavior
- projection updates after terminal events

### 18.3 Test-data rule

Business fixtures and platform fixtures must be constructed separately. Tests must stop depending on broad cleanup patterns that hide ownership mistakes.

## 19. Migration Strategy

This refactor must use staged dual-track migration, not a big-bang cutover.

### 19.1 Phase 1: Build the new platform skeleton

- introduce RabbitMQ
- create new schemas/tables
- define commands/events
- add outbox/inbox
- add execution job model

Legacy flow still serves production/demo traffic.

### 19.2 Phase 2: Migrate `REVIEW_ASSIST_ANALYSIS`

This is the first candidate because it has:

- clear business anchor
- direct user value
- already constrained visibility semantics

### 19.3 Phase 3: Migrate `DECISION_CONFLICT_ANALYSIS`

This validates round-scoped inputs, chair/admin visibility, and review-report aggregation boundaries.

### 19.4 Phase 4: Migrate `SCREENING_ANALYSIS`

After all task types are migrated, remove:

- API polling scheduler
- local mirrored task-state logic
- agent in-memory `TaskStore`
- legacy HTTP task orchestration semantics

### 19.5 Routing during migration

Temporary per-task-type routing switches are allowed only for migration:

- reviewer assist: `legacy | platform`
- conflict analysis: `legacy | platform`
- screening analysis: `legacy | platform`

Once a task type is stable on the new architecture, the legacy route must be deleted rather than preserved indefinitely.

## 20. Completion Criteria

The architecture refactor is considered complete only when:

- `apps/api` no longer polls the agent service
- `services/agent` no longer treats in-memory task state as truth
- at least one task type fully runs through the command/event platform path
- API reads projections instead of mirrored execution state
- idempotency, retry, dead-letter, and visibility rules are explicitly tested
- legacy mirrored execution logic is removed task type by task type

## 21. Recommended Implementation Order

1. Define command/event contracts and shared analysis identities
2. Introduce outbox/inbox and RabbitMQ infrastructure
3. Build the new agent-platform execution job model
4. Migrate `REVIEW_ASSIST_ANALYSIS`
5. Migrate `DECISION_CONFLICT_ANALYSIS`
6. Migrate `SCREENING_ANALYSIS`
7. Remove legacy polling and mirrored task logic

## 22. Final Design Summary

The refactor preserves the independent agent service, but changes its role from a shallow HTTP task wrapper into a proper execution platform. The API becomes a business-intent and projection system rather than a secondary job manager. RabbitMQ becomes the command/event backbone. Design patterns are applied only where they remove current structural ambiguity:

- `Command` and `Domain Event` for inter-service coordination
- `Transactional Outbox` and `Idempotent Consumer` for reliability
- `State`, `Strategy`, `Policy`, and `Projection` for codebase clarity

The central architectural rule is simple:

- business truth belongs to `apps/api`
- execution truth belongs to `services/agent`
- neither side mirrors the other side's state machine
