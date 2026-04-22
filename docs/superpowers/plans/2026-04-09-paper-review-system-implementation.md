# Paper Review System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and harden the approved paper review system, including the Spring Boot main system, Oracle schema, FastAPI agent service, Vue frontend, and the next-stage architecture refactor that separates business intent from agent execution.

**Architecture:** The delivered system currently uses a monorepo with `apps/web`, `apps/api`, `services/agent`, and `database/oracle`. The original build stream completed the end-to-end review workflow and first-generation agent integration. The current follow-on stream is an architecture refactor: `apps/api` will own business intent and user-facing projections, `services/agent` will own durable execution jobs and platform governance, and RabbitMQ plus outbox/inbox patterns will replace mirrored local/external task state.

**Tech Stack:** Vue 3, Element Plus, Vite, Java 21, Spring Boot, Spring Security, JWT, JdbcTemplate, Oracle, Python 3.11+, FastAPI, LangGraph or a simpler orchestration replacement if justified during refactor, RabbitMQ, pytest, Vitest.

---

## Target Repository Structure

- `apps/api/`
- `apps/api/src/main/java/com/example/review/`
- `apps/api/src/test/java/com/example/review/`
- `apps/web/src/`
- `services/agent/app/`
- `services/agent/tests/`
- `database/oracle/`
- `scripts/`
- `docs/superpowers/specs/`
- `docs/superpowers/plans/`

## Milestones

1. Repository and runtime scaffolding
2. Oracle schema and backend authentication
3. Manuscript/version/review workflow backend
4. Agent service and first-generation integration
5. Frontend workflow screens and actor-specific views
6. End-to-end verification and demo preparation
7. Architecture refactor design for split-sovereignty agent execution
8. Implementation of message-driven agent platform refactor

## Current Execution Status

- Task 1 completed on 2026-04-09: repository scaffolding for API, web, agent, and scripts.
- Task 2 completed on 2026-04-09: Oracle schema, indexes, triggers, procedures, and runtime verification in a local Oracle Free container.
- Task 3 completed on 2026-04-09: Spring Security, JWT, Oracle-backed authentication, and role control.
- Task 4 completed on 2026-04-09: manuscript/version workflow backend foundations.
- Task 5 completed on 2026-04-09: review workflow backend foundations.
- Task 6 completed on 2026-04-09: decision and workflow query surfaces.
- Task 7 completed on 2026-04-13 as part of commit `250b841`: groundwork for agent-facing workflow structures.
- Task 8 completed on 2026-04-13 as part of commit `250b841`: first-generation LangGraph-backed agent workflows, schemas, and redaction.
- Task 9 completed on 2026-04-13 as commit `44f9481`, with post-review fix `2209067`: multipart/PDF agent integration between Spring API and FastAPI agent service, Oracle result persistence, and result query endpoints.
- Task 10 completed on 2026-04-13: frontend authentication shell, route guards, token persistence, and role-aware layout.
- Task 11 completed on 2026-04-13: actor-specific workflow pages for author, reviewer, chair, and admin, including reviewer paper access and admin agent monitoring.
- Task 12 design completed on 2026-04-13 in `docs/superpowers/specs/2026-04-13-task12-e2e-demo-and-visual-hardening-design.md`: Oracle-backed API e2e verification direction and demo/visual hardening scope.
- Local operational check completed on 2026-04-14: `bash scripts/dev-up.sh` started web on `http://localhost:5173`, API on `8080`, and agent service on `8001`; demo accounts and workflow seed data were available from `database/oracle/006_seed_demo_users.sql` and `database/oracle/007_seed_demo_workflow.sql`.
- Non-agent frontend loading-state cleanup completed on 2026-04-14 with successful Vitest, `vue-tsc`, and Vite build verification.
- Non-agent frontend API error feedback cleanup completed on 2026-04-14 with successful frontend verification and `git diff --check`.
- Documentation onboarding and optimization pack completed on 2026-04-13 across `README.md`, `CONTRIBUTING.md`, `TODO.md`, `docs/PROJECT_GUIDE.md`, and the expanded `docs/` bundle.
- Demo documentation pack completed on 2026-04-13 under `docs/demo/`.
- Architecture refactor design completed on 2026-04-22 in `docs/superpowers/specs/2026-04-22-agent-platform-boundary-and-execution-refactor-design.md`. The approved direction replaces mirrored API/Agent task ownership with a split-sovereignty model: `apps/api` owns business intent and result projections, `services/agent` owns execution jobs and platform governance, and RabbitMQ plus outbox/inbox patterns become the inter-service coordination backbone.
- Design-cycle verification on 2026-04-22 included direct code review and focused agent-service tests:
  - `./.venv/bin/python -m pytest services/agent/tests/test_tasks_api.py services/agent/tests/test_multipart_tasks_api.py -q`
  - `./.venv/bin/python -m pytest services/agent/tests/test_workflow_schemas.py -q`
- Spring Agent integration and e2e tests were attempted on 2026-04-22 with:
  - `mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest,ReviewFlowE2eTest test`
  - Result: failed in the sandbox due to Oracle connectivity restrictions, reporting `ORA-17820` and `SocketException: Operation not permitted`.

## Task Ledger

### Task 1: Scaffold the Monorepo

**Status:** Completed on 2026-04-09.

**What changed:**

- Added backend, frontend, and agent-service skeletons.
- Added startup and test scripts.
- Established the repository layout used by all later tasks.

**Verification run:**

- `bash scripts/dev-up.sh`
- Maven tests, pytest, Vitest, `vue-tsc`, and Vite build were later verified after local environment bootstrap.

### Task 2: Create the Oracle Schema

**Status:** Completed on 2026-04-09.

**What changed:**

- Added `database/oracle/001_init.sql`
- Added `database/oracle/002_seed_roles.sql`
- Added `database/oracle/003_indexes.sql`
- Added `database/oracle/004_procedures.sql`
- Added `database/oracle/005_triggers.sql`
- Added `database/oracle/verify_schema.sql`

**Verification run:**

- Imported schema into local Oracle Free container
- Ran `verify_schema.sql` successfully

### Task 3: Implement Authentication and Role Control

**Status:** Completed on 2026-04-09.

**What changed:**

- Added Spring Security and JWT authentication flow.
- Bound auth to real Oracle user and role tables.
- Added protected-route testing and demo-user seed support.

**Verification run:**

- Backend tests against Oracle-backed auth flow
- Mockito configuration adjusted for newer JDK compatibility

### Task 4: Manuscript and Version Workflow Backend

**Status:** Completed on 2026-04-09.

**What changed:**

- Added manuscript creation, version creation, PDF upload, and submission flow.
- Bound version data to Oracle manuscript structures.

**Verification run:**

- Backend unit/integration tests for manuscript and version behaviors

### Task 5: Review Workflow Backend

**Status:** Completed on 2026-04-09.

**What changed:**

- Added review rounds, reviewer assignment, decline/accept paths, conflict checks, and report submission.
- Established reviewer-scoped access paths for workflow data.

**Verification run:**

- Backend unit/integration tests for review workflow services and repositories

### Task 6: Decision Flow and Workflow Queries

**Status:** Completed on 2026-04-09.

**What changed:**

- Added chair decision flow and workflow query surfaces.
- Added notification and audit support around core workflow transactions.

**Verification run:**

- Backend unit/integration tests for decision service and workflow queries

### Task 7: Agent Workflow Groundwork

**Status:** Completed on 2026-04-13.

**What changed:**

- Added supporting structures required to expose agent-driven workflow slices.

**Verification run:**

- Covered by the Task 8 and Task 9 verification passes

### Task 8: First-Generation Agent Workflows

**Status:** Completed on 2026-04-13.

**What changed:**

- Added FastAPI task endpoints with internal API key protection.
- Added first-generation workflow routing, validation, redaction, and in-memory task handling.
- Added focused pytest coverage for task APIs and workflow schemas.

**Verification run:**

- Agent pytest suite on the first-generation workflows

### Task 9: First-Generation Main-System Agent Integration

**Status:** Completed on 2026-04-13.

**What changed:**

- Added multipart/PDF submission from Spring API to FastAPI agent service.
- Added Oracle persistence of raw and redacted analysis results.
- Added local task/result repositories and polling scheduler in the first-generation design.
- Added result query endpoints and conflict-analysis payload aggregation from Oracle review reports.

**Verification run:**

- API integration tests
- Agent pytest suite
- Review-flow e2e verification in the local environment

### Task 10: Frontend Authentication Shell

**Status:** Completed on 2026-04-13.

**What changed:**

- Added login flow, token storage, route guards, and role-aware app shell.

**Verification run:**

- Frontend test, typecheck, and build passes

### Task 11: Workflow Screens

**Status:** Completed on 2026-04-13.

**What changed:**

- Added role-specific pages for author, reviewer, chair, and admin.
- Added reviewer paper reading support and admin agent-monitor surfaces.
- Aligned chair/admin route behavior with backend authorization.

**Verification run:**

- Frontend test, typecheck, and build passes
- Backend tests for workflow query and access control slices

### Task 12: Demo and Visual Hardening Design

**Status:** Designed on 2026-04-13; implementation state remains partial and scoped by later priorities.

**What changed:**

- Captured the approved e2e and demo direction in a dedicated design doc.

**Verification run:**

- Design review and later local operational checks

### Task 13: Architecture Refactor Design For Agent Platform Split Sovereignty

**Status:** Completed on 2026-04-22.

**Files:**

- Added `docs/superpowers/specs/2026-04-22-agent-platform-boundary-and-execution-refactor-design.md`
- Updated this plan file as the authoritative execution ledger

**What changed:**

- Defined the approved split-sovereignty target architecture:
  - `apps/api` owns business intent, authorization, and result projections
  - `services/agent` owns durable execution jobs, retries, orchestration, provider routing, and redaction
  - RabbitMQ carries explicit commands and events
- Replaced mirrored task ownership with a business-intent model on the API side and an execution-job model on the agent side.
- Chose `Transactional Outbox`, `Idempotent Consumer`, `State`, `Strategy + Registry`, `Policy Object`, and `Projection` as the primary design patterns for the refactor.
- Defined migration order: message contracts and infrastructure first, then `REVIEW_ASSIST_ANALYSIS`, then `DECISION_CONFLICT_ANALYSIS`, then `SCREENING_ANALYSIS`, then legacy deletion.

**Verification run:**

- Focused agent pytest verification:
  - `./.venv/bin/python -m pytest services/agent/tests/test_tasks_api.py services/agent/tests/test_multipart_tasks_api.py -q`
  - `./.venv/bin/python -m pytest services/agent/tests/test_workflow_schemas.py -q`
- Attempted Spring integration/e2e verification:
  - `mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest,ReviewFlowE2eTest test`
  - Failed due to sandboxed Oracle connectivity limits rather than a proven application regression

**Current completion state:**

- Design is approved and documented.
- Implementation planning for the refactor is the next active stream.

## Active Task

- Active task: convert the approved architecture refactor design into a concrete implementation plan and then execute the refactor task by task.

## Working Rules For Next Execution Cycle

- Do not implement new agent-platform code until the implementation plan derived from `docs/superpowers/specs/2026-04-22-agent-platform-boundary-and-execution-refactor-design.md` is written and approved.
- Keep this file as the sole authoritative execution ledger for the implementation stream.
- Record each architecture-refactor sub-step here once implementation begins:
  - what was executed
  - what changed
  - what verification ran
  - the resulting completion state
