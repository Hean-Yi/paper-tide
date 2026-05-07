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
- Local dev bootstrap hardening completed on 2026-04-23: `scripts/dev-up.sh` now auto-bootstraps the default local Oracle Free instance before starting the API, verifies or applies the schema when needed, and replays the idempotent demo seeds for a no-manual local startup path. Verification: `bash -n scripts/dev-up.sh` and `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AuthControllerTest test`.
- Local seed bootstrap cleanup completed on 2026-04-23: `scripts/dev-up.sh` now calls `scripts/demo-seed.sh` as the single demo-data entrypoint and falls back to `scripts/oracle-demo-seed.sh` only when the workflow seed script is unavailable, removing the duplicate `006_seed_demo_users.sql` replay during local startup. Verification: `bash -n scripts/dev-up.sh` and `bash scripts/dev-up.sh`.
- RabbitMQ bootstrap hardening completed on 2026-04-23: `scripts/rabbitmq-up.sh` now keeps the broker container for restart/log inspection, waits through a startup grace window, checks state via `docker inspect`, and retries once with a clean container before failing. Verification: `bash -n scripts/rabbitmq-up.sh`, `bash scripts/rabbitmq-up.sh --optional`, and `bash scripts/dev-up.sh`.
- Non-agent frontend loading-state cleanup completed on 2026-04-14 with successful Vitest, `vue-tsc`, and Vite build verification.
- Non-agent frontend API error feedback cleanup completed on 2026-04-14 with successful frontend verification and `git diff --check`.
- Review editor visual hardening completed on 2026-04-27: the reviewer workspace now uses a narrower bounded assist sidebar, collapses to a reader-only layout, and keeps the review score inputs in a side-panel-friendly grid so the sidebar no longer overflows the `Review editor` width. Verification: `cd apps/web && npm run test -- --run`, `cd apps/web && npm run typecheck`, and `cd apps/web && npm run build`.
- Review editor width follow-up completed on 2026-04-27: the reviewer workspace now widens the main shell only for `Review editor`, trims the assist rail width again, and lets the PDF reader consume more of the remaining left-side space without changing other workflow pages. Verification: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts`, `cd apps/web && npm run typecheck`, and `cd apps/web && npm run build`.
- Review editor reader-height follow-up completed on 2026-04-27: the reviewer PDF reader now allocates the preview frame as the flexible remainder of the left column and raises its default minimum height so the rendered page occupies more of the reading area vertically. Verification: `cd apps/web && npm run build` and `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts`.
- Review editor reader-overflow follow-up completed on 2026-04-27: the reviewer PDF preview frame now uses border-box sizing so its `height: 100%` constraint includes padding and border, preventing the small bottom overflow past the left-column boundary. Verification: `cd apps/web && npm run build`.
- Review editor assist-rail width follow-up completed on 2026-04-27: the reviewer assist rail is widened again and the `Agent Trace` action row now stays horizontally aligned on desktop widths so `Run review assistant`, `Refresh`, and the status chip fit on one line. Verification: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts` and `cd apps/web && npm run build`.
- Review editor column-balance follow-up completed on 2026-04-27: the desktop review workspace now uses a proportional two-column grid that narrows the left reader column and grants the assist rail a larger bounded share, keeping the right rail inside the `Review editor` container width. Verification: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts` and `cd apps/web && npm run build`.
- Documentation onboarding and optimization pack completed on 2026-04-13 across `README.md`, `CONTRIBUTING.md`, `TODO.md`, `docs/PROJECT_GUIDE.md`, and the expanded `docs/` bundle.
- Demo documentation pack completed on 2026-04-13 under `docs/demo/`.
- Architecture refactor design completed on 2026-04-22 in `docs/superpowers/specs/2026-04-22-agent-platform-boundary-and-execution-refactor-design.md`. The approved direction replaces mirrored API/Agent task ownership with a split-sovereignty model: `apps/api` owns business intent and result projections, `services/agent` owns execution jobs and platform governance, and RabbitMQ plus outbox/inbox patterns become the inter-service coordination backbone.
- Design-cycle verification on 2026-04-22 included direct code review and focused agent-service tests:
  - `./.venv/bin/python -m pytest services/agent/tests/test_tasks_api.py services/agent/tests/test_multipart_tasks_api.py -q`
  - `./.venv/bin/python -m pytest services/agent/tests/test_workflow_schemas.py -q`
- Spring Agent integration and e2e tests were attempted on 2026-04-22 with:
  - `mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest,ReviewFlowE2eTest test`
  - Result: failed in the sandbox due to Oracle connectivity restrictions, reporting `ORA-17820` and `SocketException: Operation not permitted`.
- Detailed repository review and backlog synchronization completed on 2026-04-23:
  - Re-reviewed `apps/api`, `apps/web`, `services/agent`, `database/oracle`, and developer scripts against the current split-sovereignty design.
  - Rewrote `TODO.md` into a module-by-module backlog with explicit `P0/P1/P2` priorities covering runtime closure, boundary cleanup, documentation drift, validation gaps, and productization work.
  - Updated `AGENTS.md` with reusable lessons about keeping operational docs aligned with live integration paths and treating message-driven refactors as incomplete until broker wiring and repository-level verification exist.
  - Verification: static code review of the referenced files plus `git diff --check`.

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

**Status:** Designed on 2026-04-13; partial frontend visual hardening update executed on 2026-04-27.

**What changed:**

- Captured the approved e2e and demo direction in a dedicated design doc.
- Updated the reviewer `Review editor` workspace so the assist area can be collapsed away completely, leaving only `Secure Paper Reader`.
- Reduced and bounded the right-side assist rail width and adapted the review score grid to two columns so the side panel plus reader stay visually contained within the page shell.
- Added a follow-up layout pass that widens the application shell only on the reviewer `Review editor` route and slightly trims the assist rail again so the PDF pane occupies more horizontal space.
- Added a second reader-layout follow-up that makes the PDF preview frame fill the remaining left-column height instead of staying at a comparatively short fixed viewport slice.
- Added a small constraint follow-up so the preview frame height calculation includes its own padding and border instead of overshooting the left-column boundary.
- Added a right-rail width follow-up so the `Agent Trace` controls fit horizontally without wrapping while preserving the mobile stacked layout.
- Added a column-balance follow-up so the left reader yields width to the assist rail on desktop instead of letting the right side feel visually pushed past the review workspace boundary.

**Verification run:**

- Design review and later local operational checks
- `cd apps/web && npm run test -- --run`
- `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts`
- `cd apps/web && npm run typecheck`
- `cd apps/web && npm run build`

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
- Implementation plan for the refactor was written on 2026-04-22 in this authoritative plan file.
- Task 14 is complete on 2026-04-22; Task 15 is now the next active implementation slice.

## Active Task

- Active task: continue Task 19 remediation program. Phase 1 closure, Phase 2 governance recovery, and Phase 3 durable runtime plus Oracle-backed verification are now in place locally on 2026-04-23; the next slice is commit separation.

## Working Rules For Next Execution Cycle

- Do not implement new agent-platform code until the implementation plan derived from `docs/superpowers/specs/2026-04-22-agent-platform-boundary-and-execution-refactor-design.md` is written and approved.
- Keep this file as the sole authoritative execution ledger for the implementation stream.
- Record each architecture-refactor sub-step here once implementation begins:
  - what was executed
  - what changed
  - what verification ran
  - the resulting completion state

## Refactor File Structure

The architecture-refactor implementation uses the existing repository as the base and introduces the following focused units.

### API-side structure

- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisType.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisIntent.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisProjection.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisVisibilityLevel.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisIdempotencyKeyFactory.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisRequestPolicy.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisVisibilityPolicy.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/application/RequestReviewerAssistUseCase.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/application/RequestConflictAnalysisUseCase.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/application/RequestScreeningAnalysisUseCase.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/application/GetAnalysisProjectionUseCase.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisIntentRepository.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisProjectionRepository.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisOutboxRepository.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisInboxRepository.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisOutboxPublisher.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisEventConsumer.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/interfaces/AnalysisController.java`
- Modify: `apps/api/src/main/java/com/example/review/agent/AgentTaskController.java`
- Modify: `apps/api/src/main/java/com/example/review/review/AssignmentController.java`
- Modify: `apps/api/src/main/java/com/example/review/config/SecurityConfig.java`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `apps/api/pom.xml`

### Agent-platform structure

- Create: `services/agent/app/agent_platform/__init__.py`
- Create: `services/agent/app/agent_platform/domain.py`
- Create: `services/agent/app/agent_platform/state_machine.py`
- Create: `services/agent/app/agent_platform/repositories.py`
- Create: `services/agent/app/agent_platform/messages.py`
- Create: `services/agent/app/agent_platform/outbox.py`
- Create: `services/agent/app/agent_platform/consumer.py`
- Create: `services/agent/app/agent_platform/publisher.py`
- Create: `services/agent/app/agent_platform/handlers/base.py`
- Create: `services/agent/app/agent_platform/handlers/reviewer_assist.py`
- Create: `services/agent/app/agent_platform/handlers/conflict_analysis.py`
- Create: `services/agent/app/agent_platform/handlers/screening.py`
- Create: `services/agent/app/agent_platform/handler_registry.py`
- Create: `services/agent/app/agent_platform/provider_executor.py`
- Create: `services/agent/app/agent_platform/config.py`
- Modify: `services/agent/app/main.py`
- Modify: `services/agent/pyproject.toml`
- Delete later in the stream: `services/agent/app/task_store.py`
- Delete later in the stream: `services/agent/app/routes/tasks.py`

### Schema and scripts

- Create: `database/oracle/008_agent_platform_refactor.sql`
- Modify: `database/oracle/verify_schema.sql`
- Create: `scripts/rabbitmq-up.sh`
- Modify: `scripts/dev-up.sh`
- Modify: `scripts/test-all.sh`

### Tests

- Create: `apps/api/src/test/java/com/example/review/analysis/AnalysisDomainTest.java`
- Create: `apps/api/src/test/java/com/example/review/analysis/AnalysisIntentFlowTest.java`
- Create: `services/agent/tests/test_execution_job.py`
- Create: `services/agent/tests/test_message_consumer.py`
- Create: `services/agent/tests/test_reviewer_assist_flow.py`
- Create: `services/agent/tests/test_conflict_analysis_flow.py`
- Create: `services/agent/tests/test_screening_flow.py`
- Modify: `apps/api/src/test/java/com/example/review/agent/AgentIntegrationServiceTest.java`
- Modify: `apps/api/src/test/java/com/example/review/e2e/ReviewFlowE2eTest.java`
- Modify: `apps/web/src/tests/workflow.spec.ts`
- Create: `apps/web/src/tests/agent-projection.spec.ts`

## Architecture Refactor Tasks

### Task 14: Lay Down Shared Identities, Schema, And Messaging Foundation

**Status:** Completed on 2026-04-22.

**Files:**

- Modify: `apps/api/pom.xml`
- Modify: `apps/api/src/main/resources/application.yml`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisType.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisIntent.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisProjection.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisVisibilityLevel.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisIdempotencyKeyFactory.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisRequestPolicy.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/domain/AnalysisVisibilityPolicy.java`
- Create: `apps/api/src/test/java/com/example/review/analysis/AnalysisDomainTest.java`
- Create: `database/oracle/008_agent_platform_refactor.sql`
- Modify: `database/oracle/verify_schema.sql`
- Create: `scripts/rabbitmq-up.sh`
- Modify: `scripts/dev-up.sh`
- Modify: `scripts/test-all.sh`
- Modify: `services/agent/pyproject.toml`

- [x] **Step 1: Write the failing domain test for API-side identities and visibility**

```java
package com.example.review.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.review.analysis.domain.AnalysisIdempotencyKeyFactory;
import com.example.review.analysis.domain.AnalysisType;
import com.example.review.analysis.domain.AnalysisVisibilityLevel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalysisDomainTest {
    @Test
    void buildsStableIdempotencyKeyFromBusinessAnchor() {
        String key = AnalysisIdempotencyKeyFactory.build(
                AnalysisType.REVIEWER_ASSIST,
                Map.of("assignmentId", 77L, "versionId", 11L),
                Map.of("title", "Robust Review Systems"),
                1
        );

        assertThat(key).startsWith("REVIEWER_ASSIST:");
        assertThat(key).contains(":assignmentId=77:");
    }

    @Test
    void reviewerCannotSeeRawProjection() {
        assertThat(AnalysisVisibilityLevel.REDACTED_ONLY.allowsRaw()).isFalse();
        assertThat(AnalysisVisibilityLevel.RAW_AND_REDACTED.allowsRaw()).isTrue();
    }
}
```

- [x] **Step 2: Run the new test and verify it fails because the analysis domain package does not exist yet**

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisDomainTest test`
Expected: FAIL with missing `com.example.review.analysis.domain` classes.

- [x] **Step 3: Add RabbitMQ and JSON support dependencies plus API/agent configuration**

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

```yaml
review:
  analysis:
    broker-exchange: review.analysis.exchange
    request-routing-key: analysis.requested
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
```

```toml
dependencies = [
  "aio-pika>=9.4,<10.0",
  "oracledb>=2.2,<3.0",
]
```

- [x] **Step 4: Add the new Oracle schema objects for intent/projection/outbox/inbox and execution job/attempt/artifact/outbox/inbox**

```sql
CREATE TABLE ANALYSIS_INTENT (
  INTENT_ID NUMBER(19) NOT NULL,
  ANALYSIS_TYPE VARCHAR2(40) NOT NULL,
  BUSINESS_ANCHOR_TYPE VARCHAR2(30) NOT NULL,
  BUSINESS_ANCHOR_ID NUMBER(19) NOT NULL,
  REQUESTED_BY NUMBER(19) NOT NULL,
  IDEMPOTENCY_KEY VARCHAR2(200) NOT NULL,
  BUSINESS_STATUS VARCHAR2(30) NOT NULL,
  EXECUTION_JOB_ID VARCHAR2(100),
  CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT PK_ANALYSIS_INTENT PRIMARY KEY (INTENT_ID),
  CONSTRAINT UK_ANALYSIS_INTENT_IDEMPOTENCY UNIQUE (IDEMPOTENCY_KEY)
);
```

```sql
CREATE TABLE EXECUTION_JOB (
  JOB_ID VARCHAR2(100) NOT NULL,
  IDEMPOTENCY_KEY VARCHAR2(200) NOT NULL,
  ANALYSIS_TYPE VARCHAR2(40) NOT NULL,
  EXECUTION_STATE VARCHAR2(40) NOT NULL,
  INPUT_SNAPSHOT CLOB NOT NULL,
  FAILURE_REASON CLOB,
  CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT PK_EXECUTION_JOB PRIMARY KEY (JOB_ID),
  CONSTRAINT UK_EXECUTION_JOB_IDEMPOTENCY UNIQUE (IDEMPOTENCY_KEY)
);
```

- [x] **Step 5: Add the first API-side domain classes**

```java
package com.example.review.analysis.domain;

public enum AnalysisType {
    REVIEWER_ASSIST,
    CONFLICT_ANALYSIS,
    SCREENING
}
```

```java
package com.example.review.analysis.domain;

public enum AnalysisVisibilityLevel {
    NONE,
    REDACTED_ONLY,
    RAW_AND_REDACTED;

    public boolean allowsRaw() {
        return this == RAW_AND_REDACTED;
    }
}
```

```java
package com.example.review.analysis.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

public final class AnalysisIdempotencyKeyFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AnalysisIdempotencyKeyFactory() {
    }

    public static String build(AnalysisType type, Map<String, Object> anchor, Map<String, Object> payload, int requestVersion) {
        try {
            String anchorText = new TreeMap<>(anchor).toString().replace(", ", ",");
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(OBJECT_MAPPER.writeValueAsString(new TreeMap<>(payload)).getBytes(StandardCharsets.UTF_8));
            String hash = java.util.HexFormat.of().formatHex(digest);
            return type.name() + ":" + anchorText + ":v" + requestVersion + ":" + hash;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build analysis idempotency key", ex);
        }
    }
}
```

- [x] **Step 6: Update local runtime scripts so RabbitMQ is part of the normal developer stack**

```bash
#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-review-rabbitmq}"
docker run -d --rm --name "$CONTAINER_NAME" -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

```bash
bash "$ROOT_DIR/scripts/rabbitmq-up.sh" || true
```

- [x] **Step 7: Run the focused checks**

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisDomainTest test`
Expected: PASS

Run: `./.venv/bin/python -m pytest services/agent/tests/test_health.py -q`
Expected: PASS

Run: `git diff --check`
Expected: no output

- [x] **Step 8: Commit the foundation slice**

```bash
git add apps/api/pom.xml apps/api/src/main/resources/application.yml \
  apps/api/src/main/java/com/example/review/analysis \
  apps/api/src/test/java/com/example/review/analysis/AnalysisDomainTest.java \
  services/agent/pyproject.toml database/oracle/008_agent_platform_refactor.sql \
  database/oracle/verify_schema.sql scripts/rabbitmq-up.sh scripts/dev-up.sh scripts/test-all.sh
git commit -m "feat: add analysis identities and broker foundation"
```

**What changed:**

- Added the API-side analysis identity model and stable idempotency key factory.
- Added RabbitMQ wiring on the API side and RabbitMQ startup hooks in the local scripts.
- Added the new Oracle schema objects for analysis intent/projection messaging and execution jobs/attempts/artifacts.
- Added the agent dependency bumps required for RabbitMQ and Oracle access.
- Follow-up review fixes completed across commits `7b6130b`, `bb2d716`, and `8bd94ff`:
  - made nested idempotency hashing canonical for recursively nested map/list payloads
  - added the missing schema foreign keys and queue/child-table indexes, then verified them in `verify_schema.sql`
  - fixed the Oracle verification script syntax regression in the index audit list
  - upgraded `scripts/rabbitmq-up.sh` to support explicit `--optional` and `--required` modes, readiness waits, and optional-mode skips when Docker is missing or the engine is unreachable
  - wired `scripts/dev-up.sh` and `scripts/test-all.sh` to the optional RabbitMQ bootstrap path so developer flows still start whichever runtimes are available
  - recorded the generalized review lessons in `AGENTS.md`

**Verification run:**

- `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisDomainTest test`
  - First run failed as expected with missing `com.example.review.analysis.domain` classes.
  - Second run passed after the domain types were added.
- `./.venv/bin/python -m pytest services/agent/tests/test_health.py -q`
- `git diff --check`
- Follow-up verification after review fixes:
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisDomainTest test`
  - `./.venv/bin/python -m pytest services/agent/tests/test_health.py -q`
  - `bash -n scripts/rabbitmq-up.sh && bash -n scripts/dev-up.sh && bash -n scripts/test-all.sh`
  - `PATH=/usr/bin:/bin /bin/bash scripts/rabbitmq-up.sh --optional`
    - Result: clean skip when Docker was unavailable
  - `git diff --check`
- Review closure:
  - spec review confirmed Task 14 stayed within the approved slice
  - code quality review initially raised idempotency canonicalization, schema integrity, index coverage, RabbitMQ bootstrap, Oracle SQL syntax, and optional-runtime regressions
  - final code quality re-review approved Task 14 after commit `8bd94ff`, with only residual live-environment testing gaps noted
- Additional hardening completed on 2026-04-22 after a fresh architecture review found that Task 14 still under-modeled composite business anchors and left timestamp-maintenance semantics implicit:
  - commit `c35eaf4` introduced typed anchor/status vocabulary in the analysis domain and modeled screening as a composite `manuscriptId + versionId` anchor via `AnalysisBusinessAnchor`
  - `ANALYSIS_INTENT` gained `BUSINESS_ANCHOR_VERSION_ID`, a composite-anchor check constraint, and `IDX_ANALYSIS_INTENT_ANCHOR_LOOKUP`
  - `ANALYSIS_PROJECTION.UPDATED_AT` and `EXECUTION_JOB.UPDATED_AT` gained `BEFORE UPDATE` triggers so indexed ordering fields are maintained by schema rather than caller convention
  - a follow-up local fix tightened `AnalysisIdempotencyKeyFactory` so typed anchor/type mismatches now fail fast instead of generating semantically invalid keys
  - focused verification:
    - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisDomainTest test`
    - `git diff --check`

### Task 15: Build The Durable Agent Platform Core

**Status:** Completed on 2026-04-22.

**Files:**

- Create: `services/agent/app/agent_platform/domain.py`
- Create: `services/agent/app/agent_platform/state_machine.py`
- Create: `services/agent/app/agent_platform/repositories.py`
- Create: `services/agent/app/agent_platform/messages.py`
- Create: `services/agent/app/agent_platform/outbox.py`
- Create: `services/agent/app/agent_platform/consumer.py`
- Create: `services/agent/app/agent_platform/publisher.py`
- Create: `services/agent/app/agent_platform/config.py`
- Create: `services/agent/tests/test_execution_job.py`
- Create: `services/agent/tests/test_message_consumer.py`
- Modify: `services/agent/app/main.py`

- [x] **Step 1: Write the failing pytest suite for execution state transitions and duplicate intake**

```python
from app.agent_platform.domain import ExecutionJob
from app.agent_platform.state_machine import ExecutionStateMachine


def test_retryable_failure_transitions_to_dead_letter_after_limit():
    job = ExecutionJob.new("job-1", "key-1", "REVIEWER_ASSIST", {"title": "Paper"})

    machine = ExecutionStateMachine(max_attempts=2)
    machine.mark_running(job)
    machine.mark_retryable_failure(job, "provider timeout")
    machine.mark_running(job)
    machine.mark_retryable_failure(job, "provider timeout")

    assert job.execution_state == "DEAD_LETTERED"


def test_duplicate_intake_reuses_existing_job_id():
    repo = InMemoryExecutionJobRepository()
    first = repo.create_or_reuse("key-1", "REVIEWER_ASSIST", {"title": "Paper"})
    second = repo.create_or_reuse("key-1", "REVIEWER_ASSIST", {"title": "Paper"})

    assert second.job_id == first.job_id
```

- [x] **Step 2: Run the new pytest suite and verify it fails because the platform package is missing**

Run: `cd services/agent && ./.venv/bin/python -m pytest tests/test_execution_job.py tests/test_message_consumer.py -q`
Expected: FAIL with missing `app.agent_platform` imports.

- [x] **Step 3: Add the execution entity and state machine**

```python
from dataclasses import dataclass, field
from datetime import datetime, UTC
from uuid import uuid4


@dataclass(slots=True)
class ExecutionJob:
    job_id: str
    idempotency_key: str
    analysis_type: str
    input_snapshot: dict
    execution_state: str
    attempt_count: int = 0
    failure_reason: str | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    @classmethod
    def new(cls, job_id: str | None, idempotency_key: str, analysis_type: str, input_snapshot: dict) -> "ExecutionJob":
        return cls(
            job_id=job_id or str(uuid4()),
            idempotency_key=idempotency_key,
            analysis_type=analysis_type,
            input_snapshot=input_snapshot,
            execution_state="QUEUED",
        )
```

```python
class ExecutionStateMachine:
    def __init__(self, max_attempts: int) -> None:
        self._max_attempts = max_attempts

    def mark_running(self, job: ExecutionJob) -> None:
        job.attempt_count += 1
        job.execution_state = "RUNNING"

    def mark_retryable_failure(self, job: ExecutionJob, reason: str) -> None:
        job.failure_reason = reason
        job.execution_state = "DEAD_LETTERED" if job.attempt_count >= self._max_attempts else "FAILED_RETRYABLE"
```

- [x] **Step 4: Add repositories and message consumer skeletons**

```python
class InMemoryExecutionJobRepository:
    def __init__(self) -> None:
        self._jobs: dict[str, ExecutionJob] = {}
        self._by_idempotency: dict[str, str] = {}

    def create_or_reuse(self, idempotency_key: str, analysis_type: str, input_snapshot: dict) -> ExecutionJob:
        existing_id = self._by_idempotency.get(idempotency_key)
        if existing_id is not None:
            return self._jobs[existing_id]
        job = ExecutionJob.new(None, idempotency_key, analysis_type, input_snapshot)
        self._jobs[job.job_id] = job
        self._by_idempotency[idempotency_key] = job.job_id
        return job
```

```python
class AnalysisRequestedConsumer:
    def __init__(self, repository: InMemoryExecutionJobRepository) -> None:
        self._repository = repository

    def handle(self, message: dict) -> ExecutionJob:
        return self._repository.create_or_reuse(
            message["idempotencyKey"],
            message["analysisType"],
            message["requestPayload"],
        )
```

- [x] **Step 5: Wire the platform into FastAPI startup without deleting the legacy route layer yet**

```python
from fastapi import FastAPI

from app.agent_platform.consumer import AnalysisRequestedConsumer
from app.agent_platform.repositories import InMemoryExecutionJobRepository


def create_app() -> FastAPI:
    app = FastAPI(title="review-agent")
    repository = InMemoryExecutionJobRepository()
    app.state.analysis_consumer = AnalysisRequestedConsumer(repository)
    return app
```

- [x] **Step 6: Run the focused agent tests**

Run: `cd services/agent && ./.venv/bin/python -m pytest tests/test_execution_job.py tests/test_message_consumer.py -q`
Expected: PASS

Run: `cd services/agent && ./.venv/bin/python -m pytest tests/test_health.py -q`
Expected: PASS

- [x] **Step 7: Commit the platform-core slice**

```bash
git add services/agent/app/main.py services/agent/app/agent_platform services/agent/tests/test_execution_job.py services/agent/tests/test_message_consumer.py
git commit -m "feat(agent): add durable execution job core"
```

**What changed:**

- Added the first `services/agent/app/agent_platform` package with focused platform-core units:
  - `ExecutionJob` domain object
  - `ExecutionStateMachine` with explicit runnable and terminal transition guards
  - `InMemoryExecutionJobRepository` for idempotent intake reuse
  - `AnalysisRequestedMessage`, `InMemoryExecutionOutbox`, and `AnalysisRequestedPublisher` as lightweight command/outbox scaffolding
  - `AgentPlatformConfig` for platform settings
  - `AnalysisRequestedConsumer` as the first thin intake adapter
- Wired the new platform core into `services/agent/app/main.py` via `app.state` while preserving the legacy `TaskStore` and `/agent/tasks` route layer unchanged.
- Added focused pytest coverage for:
  - retryable-failure to dead-letter transition behavior
  - duplicate idempotent intake reuse
  - terminal-state transition protection
  - repeated publish behavior creating distinct outbox rows
  - FastAPI app exposure of the new platform components
- Closed the code-review follow-up gaps by:
  - decoupling outbox `message_id` from `job_id`
  - rejecting duplicate explicit outbox message IDs instead of overwriting rows
  - making illegal terminal-state rewrites fail fast in the state machine

**Verification run:**

- Red/green cycle executed during implementation:
  - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_job.py tests/test_message_consumer.py -q`
    - First run failed as expected before `app.agent_platform` existed.
    - Later runs passed after the platform core was added.
- Focused agent verification after implementation and review fixes:
  - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_job.py tests/test_message_consumer.py -q`
    - Result: `7 passed, 1 warning`
  - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_health.py -q`
    - Result: `1 passed, 1 warning`
  - `git diff --check`
    - Result: no output
- Review closure:
  - spec review confirmed the Task 15 slice matched plan scope and kept the legacy route layer intact
  - code quality review initially raised two blocking issues: outbox-row overwrite risk and missing state-transition guards
  - follow-up commit `62747d5` fixed both issues, and final code quality re-review approved the slice
- Additional hardening completed on 2026-04-22 after a fresh engineering review found that immutable-looking platform objects still leaked mutable nested state and that typed intake validation still allowed semantically invalid construction at the wrong boundary:
  - commit `2047a65` made `ExecutionJob` and `ExecutionOutboxMessage` immutable snapshots, added repository `save()` semantics, introduced `AgentPlatformRuntime`, and rejected missing/null `requestPayload` at intake
  - a follow-up local fix recursively froze nested job/outbox JSON-like payloads and added explicit mutable-copy helpers so future handlers can work from copies without mutating stored state
  - focused verification:
    - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_job.py tests/test_message_consumer.py -q`
      - Result after local follow-up: `13 passed, 1 warning`
    - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_health.py -q`
      - Result: `1 passed, 1 warning`
    - `git diff --check`
      - Result: no output

### Task 16: Migrate `REVIEW_ASSIST_ANALYSIS` To The New Intent/Projection Flow

**Files:**

- Create: `apps/api/src/main/java/com/example/review/analysis/application/RequestReviewerAssistUseCase.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisIntentRepository.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisProjectionRepository.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisOutboxRepository.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisInboxRepository.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisOutboxPublisher.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisEventConsumer.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/interfaces/AnalysisController.java`
- Create: `apps/api/src/test/java/com/example/review/analysis/AnalysisIntentFlowTest.java`
- Create: `services/agent/app/agent_platform/handlers/base.py`
- Create: `services/agent/app/agent_platform/handlers/reviewer_assist.py`
- Create: `services/agent/app/agent_platform/handler_registry.py`
- Create: `services/agent/app/agent_platform/provider_executor.py`
- Create: `services/agent/tests/test_reviewer_assist_flow.py`
- Modify: `apps/api/src/main/java/com/example/review/review/AssignmentController.java`
- Modify: `apps/api/src/test/java/com/example/review/agent/AgentIntegrationServiceTest.java`
- Modify: `apps/web/src/lib/workflow-api.ts`
- Modify: `apps/web/src/components/reviewer/ReviewerAgentPanel.vue`
- Create: `apps/web/src/tests/agent-projection.spec.ts`

- [x] **Step 1: Write the failing API integration test for reviewer-assist intent and projection**

```java
@SpringBootTest
@AutoConfigureMockMvc
class AnalysisIntentFlowTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void reviewerAssistRequestCreatesIntentInsteadOfPollingTask() throws Exception {
        String reviewerToken = loginAndExtractToken("reviewer_demo", "demo123");

        mockMvc.perform(post("/api/review-assignments/{assignmentId}/agent-assist", 7001L)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.businessStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.taskStatus").doesNotExist());
    }
}
```

- [x] **Step 2: Run the new API test and verify it fails because the intent/projection controller path is not implemented**

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisIntentFlowTest test`
Expected: FAIL with missing controller/use case wiring or unexpected legacy payload.

- [x] **Step 3: Implement the reviewer-assist request use case and API response shape**

```java
public record AnalysisIntentResponse(long intentId, String analysisType, String businessStatus) {
}
```

```java
@RestController
@RequestMapping("/api")
public class AnalysisController {
    private final RequestReviewerAssistUseCase requestReviewerAssistUseCase;

    @PostMapping("/review-assignments/{assignmentId}/agent-assist")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AnalysisIntentResponse requestReviewerAssist(
            @AuthenticationPrincipal CurrentUserPrincipal principal,
            @PathVariable long assignmentId
    ) {
        return requestReviewerAssistUseCase.handle(principal, assignmentId);
    }
}
```

- [x] **Step 4: Implement the outbox-backed API path**

```java
public AnalysisIntentResponse handle(CurrentUserPrincipal principal, long assignmentId) {
    requestPolicy.requireReviewerAssistAllowed(principal, assignmentId);
    Map<String, Object> payload = contextAssembler.buildReviewerAssistPayload(assignmentId);
    String idempotencyKey = AnalysisIdempotencyKeyFactory.build(
            AnalysisType.REVIEWER_ASSIST,
            Map.of("assignmentId", assignmentId),
            payload,
            1
    );
    long intentId = intentRepository.createIntent(AnalysisType.REVIEWER_ASSIST, assignmentId, principal.userId(), idempotencyKey);
    outboxRepository.enqueueRequested(intentId, idempotencyKey, payload);
    return new AnalysisIntentResponse(intentId, "REVIEWER_ASSIST", "REQUESTED");
}
```

- [x] **Step 5: Implement the reviewer-assist handler inside the agent platform**

```python
class ReviewerAssistHandler(AnalysisTaskHandler):
    analysis_type = "REVIEWER_ASSIST"

    def execute(self, job: ExecutionJob, provider_executor: ProviderExecutor) -> dict:
        paper = build_paper_understanding({"request_payload": job.input_snapshot, "manuscript_id": "0", "version_id": "0"})["paper_understanding"]
        raw_result = provider_executor.run_reviewer_assist(paper)
        return {
            "raw_result": raw_result,
            "redacted_result": redact_result("REVIEW_ASSIST_ANALYSIS", raw_result),
            "summary_projection": {
                "businessStatus": "AVAILABLE",
                "summary": raw_result["paperSummary"],
            },
        }
```

- [x] **Step 6: Update the reviewer UI to read projection-oriented fields**

```ts
export interface AnalysisIntentResponse {
  intentId: number;
  analysisType: string;
  businessStatus: string;
}
```

```vue
<el-tag v-if="assist.intent" :type="statusTagType(assist.intent.businessStatus)">
  {{ workflowLabel(assist.intent.businessStatus) }}
</el-tag>
```

- [x] **Step 7: Run the reviewer-assist test slice**

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisIntentFlowTest test`
Expected: PASS

Run: `cd services/agent && ./.venv/bin/python -m pytest tests/test_reviewer_assist_flow.py -q`
Expected: PASS

Run: `cd apps/web && npm run test -- --run src/tests/agent-projection.spec.ts`
Expected: PASS

- [ ] **Step 8: Commit the reviewer-assist migration**

```bash
git add apps/api/src/main/java/com/example/review/analysis \
  apps/api/src/test/java/com/example/review/analysis/AnalysisIntentFlowTest.java \
  services/agent/app/agent_platform/handlers/base.py \
  services/agent/app/agent_platform/handlers/reviewer_assist.py \
  services/agent/app/agent_platform/handler_registry.py \
  services/agent/app/agent_platform/provider_executor.py \
  services/agent/tests/test_reviewer_assist_flow.py \
  apps/web/src/lib/workflow-api.ts apps/web/src/components/reviewer/ReviewerAgentPanel.vue apps/web/src/tests/agent-projection.spec.ts
git commit -m "feat: migrate reviewer assist to intent and projection flow"
```

**Task 16 execution notes, 2026-04-23:**

- Implemented the reviewer-assist migration onto the intent/projection boundary:
  - API now exposes `POST/GET /api/review-assignments/{assignmentId}/agent-assist` from `AnalysisController`.
  - API creates `ANALYSIS_INTENT` rows and `ANALYSIS_OUTBOX` messages instead of local `AGENT_ANALYSIS_TASK` rows for reviewer assist.
  - Reviewer assist remains assignment-scoped, reviewer-owned, and checklist-only.
  - Agent service added `ReviewerAssistHandler`, `ProviderExecutor`, and `AnalysisHandlerRegistry`.
  - Reviewer UI now renders `intent` and `projections` rather than legacy `task` and `results`.
- Additional design hardening was required during implementation:
  - Outbox messages are now wrapped in the cross-service command envelope expected by the agent consumer: `idempotencyKey`, `analysisType`, `intentReference`, and nested `requestPayload`.
  - Forced reviewer-assist reruns now compute the next request version from existing intents instead of reusing a fixed `REQUEST_VERSION + 1` key.
  - Stable lessons were recorded in `AGENTS.md` for command-envelope outbox contracts and repeated `force` request identity.
- Verification run:
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisOutboxPublisherTest,RequestReviewerAssistUseCaseTest test` passed.
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisDomainTest,AnalysisOutboxPublisherTest,RequestReviewerAssistUseCaseTest test` passed.
  - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_reviewer_assist_flow.py tests/test_message_consumer.py tests/test_execution_job.py tests/test_health.py -q` passed with the existing Python 3.14/Pydantic warning.
  - `cd apps/web && npm run test -- --run src/tests/agent-projection.spec.ts src/tests/workflow.spec.ts` passed.
  - `cd apps/web && npm run typecheck` passed.
  - `cd apps/web && npm run build` passed with the existing large chunk warning.
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisIntentFlowTest,AgentIntegrationServiceTest test` was attempted both inside the sandbox and with escalation; both attempts failed before product assertions while obtaining Oracle connections (`ORA-17820`/`SocketException: Operation not permitted` in sandbox, then `ORA-17800` outside sandbox). Java compile and test compilation completed before the Oracle connection failures.
- Current completion state:
  - Task 16 implementation and non-Oracle verification are complete and committed as `ad3c2b4`.
  - Oracle-backed API integration verification remains blocked by the local Oracle connectivity failure, not by a reached business assertion.

### Task 17: Migrate `DECISION_CONFLICT_ANALYSIS` To The New Flow

**Files:**

- Create: `apps/api/src/main/java/com/example/review/analysis/application/RequestConflictAnalysisUseCase.java`
- Create: `apps/api/src/main/java/com/example/review/analysis/infrastructure/ConflictAnalysisContextRepository.java`
- Create: `apps/api/src/test/java/com/example/review/analysis/RequestConflictAnalysisUseCaseTest.java`
- Create: `services/agent/app/agent_platform/handlers/conflict_analysis.py`
- Create: `services/agent/tests/test_conflict_analysis_flow.py`
- Modify: `apps/api/src/main/java/com/example/review/agent/AgentTaskController.java`
- Modify: `apps/api/src/main/java/com/example/review/analysis/interfaces/AnalysisController.java`
- Modify: `apps/api/src/main/java/com/example/review/analysis/interfaces/AnalysisDtos.java`
- Modify: `apps/api/src/main/java/com/example/review/decision/DecisionController.java`
- Modify: `apps/api/src/main/java/com/example/review/workflow/WorkflowQueryService.java`
- Modify: `apps/api/src/test/java/com/example/review/e2e/ReviewFlowE2eTest.java`
- Modify: `apps/web/src/views/chair/DecisionWorkbenchView.vue`
- Modify: `apps/web/src/lib/workflow-api.ts`

**Pre-execution check, 2026-04-23:**

- Confirmed Task 16 is committed as `ad3c2b4`, with follow-up plan-only commit `79aaa13`.
- Fresh non-Oracle Task 16 verification passed:
  - `mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisDomainTest,AnalysisOutboxPublisherTest,RequestReviewerAssistUseCaseTest test`
  - `../../.venv/bin/python -m pytest tests/test_reviewer_assist_flow.py tests/test_message_consumer.py tests/test_execution_job.py tests/test_health.py -q`
  - `npm run test -- --run src/tests/agent-projection.spec.ts src/tests/workflow.spec.ts`
  - `git diff --check`
- Found existing uncommitted Java formatting-only changes in `AgentTaskController.java` and `GlobalExceptionHandler.java`; they are not a Task 16 functional gap.
- Task 17 plan was adjusted before execution because the legacy conflict-analysis route currently lives in `AgentTaskController`; leaving it there would conflict with the new intent/projection endpoint.

- [x] **Step 1: Write the failing conflict-analysis e2e expectation against projections**

```java
mockMvc.perform(post("/api/review-rounds/{roundId}/conflict-analysis", roundId)
                .header("Authorization", "Bearer " + chairToken)
                .contentType(APPLICATION_JSON)
                .content("{\"force\":false}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.businessStatus").value("REQUESTED"));
```

- [x] **Step 2: Run the focused e2e test and verify it fails on the legacy task contract**

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ReviewFlowE2eTest test`
Expected: FAIL in the conflict-analysis request/assertion path until the new controller and projection flow are wired.

- [x] **Step 3: Implement the chair use case and conflict-analysis handler**

```java
public AnalysisIntentResponse handle(CurrentUserPrincipal principal, long roundId) {
    RoleGuard.requireChairOrAdmin(principal);
    Map<String, Object> payload = contextAssembler.buildConflictPayload(roundId);
    String key = AnalysisIdempotencyKeyFactory.build(
            AnalysisType.CONFLICT_ANALYSIS,
            Map.of("roundId", roundId),
            payload,
            1
    );
    long intentId = intentRepository.createIntent(AnalysisType.CONFLICT_ANALYSIS, roundId, principal.userId(), key);
    outboxRepository.enqueueRequested(intentId, key, payload);
    return new AnalysisIntentResponse(intentId, "CONFLICT_ANALYSIS", "REQUESTED");
}
```

```python
class ConflictAnalysisHandler(AnalysisTaskHandler):
    analysis_type = "CONFLICT_ANALYSIS"

    def execute(self, job: ExecutionJob, provider_executor: ProviderExecutor) -> dict:
        raw = provider_executor.run_conflict_analysis(job.input_snapshot)
        return {
            "raw_result": raw,
            "redacted_result": redact_result("DECISION_CONFLICT_ANALYSIS", raw),
            "summary_projection": {
                "businessStatus": "AVAILABLE",
                "summary": raw["decisionSummary"],
                "conflictPoints": raw["conflictPoints"],
            },
        }
```

- [x] **Step 4: Update the chair workbench to read projection summaries rather than local polled task rows**

```ts
export function triggerConflictAnalysis(roundId: number, force = false) {
  return apiRequest<AnalysisIntentResponse>(`/review-rounds/${roundId}/conflict-analysis`, {
    method: "POST",
    json: { force }
  });
}
```

- [x] **Step 5: Run the conflict-analysis slice**

Run: `cd services/agent && ./.venv/bin/python -m pytest tests/test_conflict_analysis_flow.py -q`
Expected: PASS

Run: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts`
Expected: PASS

- [x] **Step 6: Commit the conflict-analysis migration**

```bash
git add apps/api/src/main/java/com/example/review/analysis/application/RequestConflictAnalysisUseCase.java \
  apps/api/src/main/java/com/example/review/decision/DecisionController.java \
  apps/api/src/main/java/com/example/review/workflow/WorkflowQueryService.java \
  apps/api/src/test/java/com/example/review/e2e/ReviewFlowE2eTest.java \
  services/agent/app/agent_platform/handlers/conflict_analysis.py \
  services/agent/tests/test_conflict_analysis_flow.py \
  apps/web/src/views/chair/DecisionWorkbenchView.vue apps/web/src/lib/workflow-api.ts
git commit -m "feat: migrate conflict analysis to broker flow"
```

**Task 17 execution notes, 2026-04-23:**

- Implemented the conflict-analysis migration onto the intent/projection boundary:
  - API now exposes `POST /api/review-rounds/{roundId}/conflict-analysis` from `AnalysisController`.
  - API creates/reuses `CONFLICT_ANALYSIS` intents with round anchors and publishes command-envelope outbox messages with review-report context.
  - Removed the legacy conflict-analysis route from `AgentTaskController` so the endpoint no longer submits mirrored local/external agent tasks.
  - Chair decision workbench now receives `conflictIntent` and `conflictProjections` from the workflow query and renders projection summaries instead of fetching legacy raw agent results.
  - Agent service added `ConflictAnalysisHandler` and registered it under `CONFLICT_ANALYSIS`.
- Verification run:
  - Red checks first failed as expected:
    - `mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RequestConflictAnalysisUseCaseTest test` failed because the use case/repository did not exist.
    - `../../.venv/bin/python -m pytest tests/test_conflict_analysis_flow.py -q` failed because the conflict handler did not exist.
    - `npm run test -- --run src/tests/workflow.spec.ts` failed because the chair workbench still rendered legacy agent results.
  - Green verification passed:
    - `mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test-compile` passed.
    - `mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisDomainTest,AnalysisOutboxPublisherTest,RequestReviewerAssistUseCaseTest,RequestConflictAnalysisUseCaseTest test` passed with 11 tests.
    - `../../.venv/bin/python -m pytest tests/test_conflict_analysis_flow.py tests/test_reviewer_assist_flow.py tests/test_message_consumer.py tests/test_execution_job.py -q` passed with 17 tests and the existing Python 3.14/Pydantic warning.
    - `npm run test -- --run src/tests/workflow.spec.ts src/tests/agent-projection.spec.ts` passed with 24 tests.
    - `npm run typecheck` passed.
    - `npm run build` passed with the existing large chunk warning.
    - `git diff --check` passed.
  - Oracle-backed verification remains blocked by local Oracle connectivity:
    - Sandbox run of `mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest,ReviewFlowE2eTest test` failed at JDBC connection setup with `ORA-17820` and `SocketException: Operation not permitted`.
    - Escalated run of the same command reached the network but failed before business assertions with `ORA-17800` while obtaining Oracle connections.
- Current completion state:
  - Task 17 implementation and non-Oracle verification are complete and committed as `e9db243`.
  - Oracle-backed integration/e2e verification is still blocked by the local Oracle connectivity failure, not by a reached Task17 assertion.

### Task 18: Migrate `SCREENING_ANALYSIS` And Delete Legacy Mirrored Task Infrastructure

**Files:**

- Create: `apps/api/src/main/java/com/example/review/analysis/application/RequestScreeningAnalysisUseCase.java`
- Create: `services/agent/app/agent_platform/handlers/screening.py`
- Create: `services/agent/tests/test_screening_flow.py`
- Modify: `apps/api/src/main/java/com/example/review/workflow/WorkflowQueryController.java`
- Modify: `apps/web/src/views/chair/ScreeningQueueView.vue`
- Modify: `apps/api/src/test/java/com/example/review/agent/AgentIntegrationServiceTest.java`
- Delete: `apps/api/src/main/java/com/example/review/agent/AgentPollingScheduler.java`
- Delete: `apps/api/src/main/java/com/example/review/agent/HttpAgentServiceClient.java`
- Delete: `apps/api/src/main/java/com/example/review/agent/AgentServiceClient.java`
- Delete: `apps/api/src/main/java/com/example/review/agent/AgentServiceException.java`
- Delete: `services/agent/app/task_store.py`
- Delete: `services/agent/app/routes/tasks.py`
- Delete: `services/agent/tests/test_tasks_api.py`
- Delete: `services/agent/tests/test_multipart_tasks_api.py`

- [x] **Step 1: Write the failing screening-flow test and the failing absence test for legacy poller references**

```python
def test_screening_handler_builds_projection_summary():
    handler = ScreeningAnalysisHandler()
    result = handler.execute(
        ExecutionJob.new("job-1", "screening-key", "SCREENING", {"title": "Paper", "pdfText": "Introduction ..."}),
        FakeProviderExecutor(),
    )

    assert result["summary_projection"]["businessStatus"] == "AVAILABLE"
```

```java
@Test
void screeningRequestReturnsBusinessIntentResponse() throws Exception {
    String chairToken = loginAndExtractToken("chair_demo", "demo123");

    mockMvc.perform(post("/api/manuscripts/{id}/versions/{versionId}/screening-analysis", 2001L, 3001L)
                    .header("Authorization", "Bearer " + chairToken)
                    .contentType(APPLICATION_JSON)
                    .content("{\"force\":false}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.businessStatus").value("REQUESTED"))
            .andExpect(jsonPath("$.taskStatus").doesNotExist());
}
```

- [x] **Step 2: Run the new tests and verify they fail while the legacy infrastructure still exists**

Run: `cd services/agent && ./.venv/bin/python -m pytest tests/test_screening_flow.py -q`
Expected: FAIL because `ScreeningAnalysisHandler` does not exist.

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest test`
Expected: FAIL because the screening endpoint still returns the legacy task contract.

- [x] **Step 3: Implement the screening use case and handler, then delete the legacy API poller/client path**

```java
public AnalysisIntentResponse handle(CurrentUserPrincipal principal, long manuscriptId, long versionId) {
    RoleGuard.requireChairOrAdmin(principal);
    Map<String, Object> payload = contextAssembler.buildScreeningPayload(manuscriptId, versionId);
    String key = AnalysisIdempotencyKeyFactory.build(
            AnalysisType.SCREENING,
            Map.of("manuscriptId", manuscriptId, "versionId", versionId),
            payload,
            1
    );
    long intentId = intentRepository.createIntent(AnalysisType.SCREENING, manuscriptId, principal.userId(), key);
    outboxRepository.enqueueRequested(intentId, key, payload);
    return new AnalysisIntentResponse(intentId, "SCREENING", "REQUESTED");
}
```

```python
class ScreeningAnalysisHandler(AnalysisTaskHandler):
    analysis_type = "SCREENING"

    def execute(self, job: ExecutionJob, provider_executor: ProviderExecutor) -> dict:
        raw = provider_executor.run_screening(job.input_snapshot)
        return {
            "raw_result": raw,
            "redacted_result": redact_result("SCREENING_ANALYSIS", raw),
            "summary_projection": {
                "businessStatus": "AVAILABLE",
                "summary": raw["screeningSummary"],
            },
        }
```

- [x] **Step 4: Delete the first-generation FastAPI task API and in-memory task truth**

```python
from fastapi import FastAPI


def create_app() -> FastAPI:
    app = FastAPI(title="review-agent-platform")
    app.include_router(build_platform_admin_router())
    return app
```

- [x] **Step 5: Run the legacy-removal verification slice**

Run: `cd services/agent && ./.venv/bin/python -m pytest tests/test_screening_flow.py tests/test_health.py -q`
Expected: PASS

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest test`
Expected: PASS after tests are rewritten to the intent/projection architecture.

- [x] **Step 6: Commit the screening migration and legacy deletion**

```bash
git add apps/api/src/main/java/com/example/review/analysis/application/RequestScreeningAnalysisUseCase.java \
  apps/api/src/main/java/com/example/review/workflow/WorkflowQueryController.java \
  apps/web/src/views/chair/ScreeningQueueView.vue \
  services/agent/app/agent_platform/handlers/screening.py \
  services/agent/tests/test_screening_flow.py \
  apps/api/src/test/java/com/example/review/agent/AgentIntegrationServiceTest.java
git rm apps/api/src/main/java/com/example/review/agent/AgentPollingScheduler.java \
  apps/api/src/main/java/com/example/review/agent/HttpAgentServiceClient.java \
  apps/api/src/main/java/com/example/review/agent/AgentServiceClient.java \
  apps/api/src/main/java/com/example/review/agent/AgentServiceException.java \
  services/agent/app/task_store.py services/agent/app/routes/tasks.py \
  services/agent/tests/test_tasks_api.py services/agent/tests/test_multipart_tasks_api.py
git commit -m "refactor: remove mirrored agent task infrastructure"
```

**Task 18 execution notes, 2026-04-23:**

- Executed the TDD red step for screening migration:
  - Added `RequestScreeningAnalysisUseCaseTest`.
  - Added `services/agent/tests/test_screening_flow.py`.
  - Added a frontend workflow test proving `ScreeningQueueView` posts to `/screening-analysis` instead of `/agent-tasks`.
  - Replaced the old scheduling code-quality expectation with legacy file absence checks.
- Verified the red failures before implementation:
  - `/Users/hean/Agent_proj/.venv/bin/python -m pytest tests/test_screening_flow.py -q` failed because `app.agent_platform.handlers.screening` did not exist.
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RequestScreeningAnalysisUseCaseTest,CodeQualityTest test` failed because `RequestScreeningAnalysisUseCase` and `ScreeningAnalysisContextRepository` did not exist.
  - `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts -t "requests screening analysis"` failed because the UI still posted to `/agent-tasks`.
- Implemented the new screening intent flow:
  - Added `RequestScreeningAnalysisUseCase` and `ScreeningAnalysisContextRepository`.
  - Added `POST /api/manuscripts/{manuscriptId}/versions/{versionId}/screening-analysis` on `AnalysisController`.
  - Added the agent-platform `ScreeningAnalysisHandler` and `ProviderExecutor.run_screening`.
  - Updated the chair screening queue to call the new intent endpoint and show scoped loading/error handling.
- Deleted the legacy mirrored task infrastructure:
  - Removed the Java poller, HTTP client, client interface, exception, task controller, integration service, repository, DTOs, and obsolete exception advice.
  - Removed `review.agent.*` client/poller configuration and `@EnableScheduling`.
  - Removed the FastAPI task router, in-memory task store, legacy coordinator, and first-generation task API tests.
  - Rewrote the old `AgentIntegrationServiceTest` around the new screening intent/outbox behavior.
  - Retired the frontend admin mirrored-task monitor placeholder until Task 19 adds the new governance view.
- Verification run:
  - `cd services/agent && /Users/hean/Agent_proj/.venv/bin/python -m pytest tests/test_screening_flow.py tests/test_conflict_analysis_flow.py tests/test_reviewer_assist_flow.py tests/test_message_consumer.py tests/test_execution_job.py tests/test_health.py -q` passed: 20 tests.
  - `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts src/tests/agent-projection.spec.ts` passed: 25 tests.
  - `cd apps/web && npm run typecheck` passed.
  - `cd apps/web && npm run build` passed, with the existing Vite chunk-size warning.
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test-compile` passed.
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisDomainTest,AnalysisOutboxPublisherTest,RequestReviewerAssistUseCaseTest,RequestConflictAnalysisUseCaseTest,RequestScreeningAnalysisUseCaseTest,CodeQualityTest test` passed: 15 tests.
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository clean -Dtest=com.example.review.agent.AgentIntegrationServiceTest test` was rerun with network permission to download the missing Maven clean plugin, then failed on Oracle connectivity (`ORA-17800`) while obtaining a JDBC connection. The earlier stale deleted-class failure was cleared by `clean`.
- Completion state:
  - Task 18 implementation and non-Oracle verification are complete and committed as `820232f`.
  - The Oracle-backed `AgentIntegrationServiceTest` remains blocked by local Oracle connectivity, consistent with the previous Task 16/17 verification limitation.
  - Task 19 is now the next active implementation slice.

### Task 19: Remediation Program For Analysis Flow Closure, Governance, And Durable Runtime

**Files:**

- Modify: `docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md`
- Modify: `AGENTS.md`
- Modify: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisEventConsumer.java`
- Modify: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisInboxRepository.java`
- Modify: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisIntentRepository.java`
- Modify: `apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisProjectionRepository.java`
- Create: `apps/api/src/test/java/com/example/review/analysis/AnalysisEventConsumerTest.java`
- Modify: `services/agent/app/agent_platform/messages.py`
- Modify: `services/agent/app/agent_platform/publisher.py`
- Modify: `services/agent/app/agent_platform/runtime.py`
- Modify: `services/agent/app/main.py`
- Create: `services/agent/tests/test_execution_runtime.py`
- Modify: `apps/web/src/views/admin/AgentMonitorView.vue`
- Modify: `apps/web/src/lib/workflow-format.ts`
- Modify: `apps/api/src/test/java/com/example/review/e2e/ReviewFlowE2eTest.java`
- Modify: `scripts/test-all.sh`
- Modify: `README.md`
- Modify: `docs/ARCHITECTURE.md`

**Current completion state, corrected on 2026-04-23:**

- Task 15-18 request-side migration work is present and the focused non-Oracle test slices pass.
- The execution-completion half of the new architecture is not complete yet:
  - API-side event consumption is still placeholder-only.
  - Agent runtime still uses in-memory runtime state and does not execute handlers from the new intake path.
  - Admin governance UX regressed to a placeholder page when the mirrored monitor was removed.
- This remediation program replaces the over-optimistic interpretation of Task 15-18 with a stricter delivery sequence:
  - Phase 1: close the request-to-projection execution loop.
  - Phase 2: restore admin governance visibility and remove lingering legacy references.
  - Phase 3: replace in-memory execution state with durable infrastructure.

- [x] **Phase 1 / Step 1: Write the failing closure tests for agent execution and API projection updates**

```python
def test_runtime_executes_reviewer_assist_and_emits_completed_event():
    runtime = build_runtime()
    requested = AnalysisRequestedMessage(
        idempotency_key="key-1",
        analysis_type="REVIEWER_ASSIST",
        intent_reference="101",
        request_payload={"title": "Boundary Paper", "reviewerAssist": {"manuscriptId": 9, "versionId": 10}},
    )
    job = runtime.analysis_requested_consumer.handle(requested)

    event = runtime.execute_requested_job(job)

    assert event["eventType"] == "analysis.completed"
    assert event["intentId"] == 101
    assert event["jobId"] == job.job_id
    assert event["analysisType"] == "REVIEWER_ASSIST"
    assert event["businessStatus"] == "AVAILABLE"
```

```java
@Test
void consumeCompletedEventMarksIntentAvailableAndCreatesProjection() {
    consumer.consume(Map.of(
            "messageKey", "completed:key-1",
            "eventType", "analysis.completed",
            "intentId", 101L,
            "jobId", "job-1",
            "analysisType", "REVIEWER_ASSIST",
            "businessStatus", "AVAILABLE",
            "summaryProjection", Map.of("businessStatus", "AVAILABLE", "summary", "Checklist ready."),
            "redactedResult", Map.of("checklist", List.of("Verify claims"))
    ));

    assertThat(intentRepository.updatedIntentId).isEqualTo(101L);
    assertThat(intentRepository.updatedBusinessStatus).isEqualTo("AVAILABLE");
    assertThat(projectionRepository.savedIntentId).isEqualTo(101L);
    assertThat(inboxRepository.processedMessageKey).isEqualTo("completed:key-1");
}
```

- [x] **Phase 1 / Step 2: Run the focused closure tests and verify they fail for the current placeholders**

Run: `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_runtime.py -q`
Expected: FAIL because `AgentPlatformRuntime` cannot execute handlers or emit completion events.

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisEventConsumerTest test`
Expected: FAIL because `AnalysisEventConsumer` cannot consume completion events or update projections.

- [x] **Phase 1 / Step 3: Implement minimal agent runtime execution and event message types**

```python
return {
    "messageKey": f"analysis.completed:{job.idempotency_key}",
    "eventType": "analysis.completed",
    "intentId": intent_id,
    "jobId": job.job_id,
    "analysisType": job.analysis_type,
    "businessStatus": "AVAILABLE",
    "summaryProjection": result["summary_projection"],
    "redactedResult": result["redacted_result"],
}
```

- [x] **Phase 1 / Step 4: Implement minimal API-side completion consumption, inbox recording, and projection upsert**

```java
public void consume(Map<String, Object> message) {
    String messageKey = requireString(message, "messageKey");
    if (inboxRepository.alreadyProcessed(messageKey)) {
        return;
    }
    long intentId = requireLong(message, "intentId");
    String analysisType = requireString(message, "analysisType");
    String businessStatus = requireString(message, "businessStatus");
    Map<String, Object> summaryProjection = requireMap(message, "summaryProjection");
    Map<String, Object> redactedResult = requireMap(message, "redactedResult");

    intentRepository.updateBusinessStatus(intentId, businessStatus);
    projectionRepository.saveProjection(intentId, analysisType, businessStatus, summaryProjection, redactedResult);
    inboxRepository.recordProcessed(messageKey, "analysis.completed", intentId, message);
}
```

- [x] **Phase 1 / Step 5: Run the focused Phase 1 test slice**

Run: `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_runtime.py tests/test_reviewer_assist_flow.py tests/test_conflict_analysis_flow.py tests/test_screening_flow.py tests/test_message_consumer.py tests/test_execution_job.py -q`
Expected: PASS

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisEventConsumerTest,AnalysisDomainTest,AnalysisOutboxPublisherTest,RequestReviewerAssistUseCaseTest,RequestConflictAnalysisUseCaseTest,RequestScreeningAnalysisUseCaseTest,CodeQualityTest test`
Expected: PASS

- [ ] **Phase 1 / Step 6: Write the execution result back into this plan and commit the closure slice**

```bash
git add docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md AGENTS.md \
  apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisEventConsumer.java \
  apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisInboxRepository.java \
  apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisIntentRepository.java \
  apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisProjectionRepository.java \
  apps/api/src/test/java/com/example/review/analysis/AnalysisEventConsumerTest.java \
  services/agent/app/agent_platform/messages.py services/agent/app/agent_platform/publisher.py \
  services/agent/app/agent_platform/runtime.py services/agent/app/main.py \
  services/agent/tests/test_execution_runtime.py
git commit -m "fix: close analysis execution to projection loop"
```

**Phase 1 execution notes, 2026-04-23:**

- Executed the red phase first:
  - Added `services/agent/tests/test_execution_runtime.py` to prove the new runtime can consume a queued request, execute the registered reviewer-assist handler, and emit an `analysis.completed` event carrying `intentId`, `jobId`, `analysisType`, `businessStatus`, `summaryProjection`, and `redactedResult`.
  - Added `apps/api/src/test/java/com/example/review/analysis/AnalysisEventConsumerTest.java` to prove API-side event consumption updates the intent status, upserts the projection, and records the inbox message.
  - Verified the intended failures:
    - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_runtime.py -q` failed with `AttributeError: 'AgentPlatformRuntime' object has no attribute 'execute_requested_job'`.
    - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisEventConsumerTest test` failed in test compilation because `AnalysisEventConsumer` did not accept intent/projection repositories and had no `consume(...)` API.
- Implemented the minimal green slice for closure:
  - `ExecutionJob` now carries `intent_reference`, and the request consumer/repository preserve it when queuing jobs.
  - Added `AnalysisCompletedMessage` and `AgentPlatformRuntime.execute_requested_job(...)` so the agent platform can move a queued job through `RUNNING -> SUCCEEDED`, execute the registered handler, and emit a deterministic completion event payload.
  - `create_app()` now wires the runtime with the job repository, handler registry, and provider executor instead of exposing only disconnected platform fragments.
  - API-side `AnalysisEventConsumer` now supports `consume(Map<String, Object>)` for `analysis.completed` events, updates `ANALYSIS_INTENT.BUSINESS_STATUS`, upserts `ANALYSIS_PROJECTION`, and records the processed event in `ANALYSIS_INBOX`.
  - Added repository helpers required by the closure path:
    - `AnalysisInboxRepository.recordProcessed(...)`
    - `AnalysisIntentRepository.updateBusinessStatus(...)`
    - `AnalysisProjectionRepository.saveProjection(...)`
- Verification run:
  - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_runtime.py tests/test_reviewer_assist_flow.py tests/test_conflict_analysis_flow.py tests/test_screening_flow.py tests/test_message_consumer.py tests/test_execution_job.py -q`
    - Result: `20 passed in 0.13s`
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisEventConsumerTest,AnalysisDomainTest,AnalysisOutboxPublisherTest,RequestReviewerAssistUseCaseTest,RequestConflictAnalysisUseCaseTest,RequestScreeningAnalysisUseCaseTest,CodeQualityTest test`
    - Result: `BUILD SUCCESS`, `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`
  - `git diff --check`
    - Result: no output
- Current completion state:
  - Phase 1 closure is implemented locally and verified in focused non-Oracle slices.
  - Phase 1 commit is intentionally still pending because the workspace already contains unrelated user-side modifications (`AGENTS.md`, `scripts/dev-up.sh`, `scripts/oracle-schema-apply.sh`, `scripts/rabbitmq-up.sh`) that should not be bundled accidentally.
  - Phase 2 governance recovery is now the next active Task 19 slice.

- [x] **Phase 2 / Step 1: Restore admin governance visibility with a real intent/projection monitor**

```vue
<el-table-column prop="intentId" label="Intent" width="110" />
<el-table-column prop="jobId" label="Job" min-width="180" />
<el-table-column prop="businessStatus" label="Status" width="140" />
<el-table-column prop="analysisType" label="Analysis" width="180" />
```

- [x] **Phase 2 / Step 2: Remove remaining legacy mirrored-task references from tests and quality checks**

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest,ReviewFlowE2eTest,CodeQualityTest test`
Expected: Oracle-backed tests may still be blocked by local connectivity, but no remaining assertion or setup path should depend on `AGENT_ANALYSIS_TASK` or `AGENT_ANALYSIS_RESULT`.

- [x] **Phase 2 / Step 3: Run frontend verification after the governance UI is restored**

Run: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts src/tests/agent-projection.spec.ts`
Expected: PASS

Run: `cd apps/web && npm run typecheck && npm run build`
Expected: PASS

**Phase 2 execution notes, 2026-04-23:**

- Added a minimal but real admin governance read model instead of the placeholder monitor page:
  - API now exposes `GET /api/admin/analysis-monitor` from `WorkflowQueryController`.
  - `WorkflowQueryService.listAdminAnalysisMonitor(...)` returns the latest 50 analysis intents with anchor labels, business status, execution job id when available, projection summary text, and projection update time.
  - Frontend `AgentMonitorView` now fetches and renders the governance list instead of showing a disabled placeholder card.
  - Frontend `workflow-api.ts` and `workflow-format.ts` now model and render intent/projection statuses such as `REQUESTED`, `AVAILABLE`, and `FAILED_VISIBLE`.
- Verification run:
  - Red checks first failed as expected:
    - `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts -t "loads admin analysis monitor rows from the governance endpoint"` failed because the placeholder `AgentMonitorView` never fetched data.
    - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AdminAnalysisMonitorQueryTest test` failed because `WorkflowQueryService` had no admin monitor query and no `AdminAnalysisMonitorItem` shape.
  - Green verification passed:
    - `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts -t "loads admin analysis monitor rows from the governance endpoint"`
      - Result: `1 passed | 23 skipped`
    - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AdminAnalysisMonitorQueryTest test`
      - Result: `BUILD SUCCESS`, `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
    - `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts src/tests/agent-projection.spec.ts`
      - Result: `26 passed`
    - `cd apps/web && npm run typecheck`
      - Result: passed
    - `cd apps/web && npm run build`
      - Result: passed, with the existing Vite chunk-size warning
    - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AdminAnalysisMonitorQueryTest,AnalysisEventConsumerTest,AnalysisDomainTest,AnalysisOutboxPublisherTest,RequestReviewerAssistUseCaseTest,RequestConflictAnalysisUseCaseTest,RequestScreeningAnalysisUseCaseTest,CodeQualityTest test`
      - Result: `BUILD SUCCESS`, `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`
    - `rg -n "AGENT_ANALYSIS_TASK|AGENT_ANALYSIS_RESULT|/agent-results" apps/api/src/test apps/web/src/tests -g '!apps/api/src/test/java/com/example/review/CodeQualityTest.java'`
      - Result: no matches outside `CodeQualityTest`, confirming the remaining legacy strings are only used by the quality guard itself.
    - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=CodeQualityTest,AdminAnalysisMonitorQueryTest,AnalysisEventConsumerTest test`
      - Result: `BUILD SUCCESS`, `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`
    - `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts src/tests/agent-projection.spec.ts`
      - Result: `26 passed`
    - `git diff --check`
      - Result: no output
- Current completion state:
  - Phase 2 governance visibility is restored with a minimal read-only admin monitor.
  - Phase 2 cleanup is complete in the non-Oracle slices: migrated tests and frontend workflow coverage no longer depend on `AGENT_ANALYSIS_TASK`, `AGENT_ANALYSIS_RESULT`, or `/agent-results`, while `CodeQualityTest` intentionally retains those literals as a regression guard.
  - Phase 3 durable runtime replacement is now the next active Task 19 slice.

- [x] **Phase 3 / Step 1: Replace in-memory execution repositories with durable implementations over `EXECUTION_*` tables**

Run: `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_runtime.py tests/test_execution_job.py tests/test_message_consumer.py -q`
Expected: PASS against the durable repository abstractions and persistence adapters.

- [x] **Phase 3 / Step 2: Run full verification commands**

Run: `./.venv/bin/python -m pytest services/agent/tests -q`
Expected: PASS

Run: `cd apps/web && npm run test -- --run && npm run typecheck && npm run build`
Expected: PASS

Run: `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`
Expected: PASS when Oracle and RabbitMQ are available locally.

Run: `git diff --check`
Expected: no output

- [x] **Phase 3 / Step 3: Commit the durable-runtime and governance remediation program**

```bash
git add docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md AGENTS.md \
  apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisEventConsumer.java \
  apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisInboxRepository.java \
  apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisIntentRepository.java \
  apps/api/src/main/java/com/example/review/analysis/infrastructure/AnalysisProjectionRepository.java \
  services/agent/app/agent_platform apps/web/src/views/admin/AgentMonitorView.vue apps/web/src/lib/workflow-format.ts \
  apps/api/src/test/java/com/example/review/analysis/AnalysisEventConsumerTest.java \
  apps/api/src/test/java/com/example/review/e2e/ReviewFlowE2eTest.java \
  scripts/test-all.sh README.md docs/ARCHITECTURE.md
git commit -m "fix: complete analysis platform remediation program"
```

**Phase 3 execution notes, 2026-04-23:**

- Added the first durable agent-platform persistence slice:
  - `AgentPlatformConfig` now accepts optional Oracle connection settings via `AGENT_PLATFORM_DB_USER`, `AGENT_PLATFORM_DB_PASSWORD`, and `AGENT_PLATFORM_DB_DSN`.
  - `create_app()` now switches between in-memory adapters and Oracle-backed adapters based on that config, while still allowing explicit connection-factory injection for deterministic tests.
  - Added `OracleExecutionJobRepository` to persist and reload `EXECUTION_JOB` rows, preserving `jobId`, `intentReference`, `idempotencyKey`, `analysisType`, `executionState`, `inputSnapshot`, `failureReason`, `attemptCount`, and `createdAt`.
  - Added `OracleExecutionOutbox` to persist and reload `EXECUTION_OUTBOX` rows, including publish-state transitions.
  - Added `database/oracle/009_execution_job_attempt_count.sql` because the runtime state machine already depended on `attemptCount`, but the durable schema did not yet persist that field. `verify_schema.sql` now checks for `EXECUTION_JOB.ATTEMPT_COUNT`.
  - Added focused red-green tests to prove:
    - Oracle-backed repositories round-trip persisted jobs and outbox rows.
    - `create_app()` wires durable adapters when DB config is present.
    - runtime execution still reaches `analysis.completed` when backed by the durable repository path.
- Verification run:
  - Red verification first failed as expected:
    - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_runtime.py tests/test_execution_job.py tests/test_message_consumer.py -q`
      - Result: collection failed because `OracleExecutionOutbox`, `OracleExecutionJobRepository`, and durable `create_app(..., db_connection_factory=...)` wiring did not exist yet.
  - Green verification after implementation:
    - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_runtime.py tests/test_execution_job.py tests/test_message_consumer.py -q`
      - Result: `19 passed in 0.13s`
    - `cd services/agent && ../../.venv/bin/python -m pytest tests -q`
      - Result: `34 passed in 0.26s`, with existing `langgraph` deprecation warnings only
    - `cd apps/web && npm run test -- --run && npm run typecheck && npm run build`
      - Result: all passed; Vite retained the pre-existing chunk-size warning
    - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=CodeQualityTest,AdminAnalysisMonitorQueryTest,AnalysisEventConsumerTest test`
      - Result: `BUILD SUCCESS`, `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`
    - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`
      - Initial sandbox result: blocked by `ORA-17820` / `SocketException: Operation not permitted`
      - Oracle-available rerun result on 2026-04-23 after local bootstrap and cleanup-order fixes: `BUILD SUCCESS`, `Tests run: 59, Failures: 0, Errors: 0, Skipped: 0`
    - `git diff --check`
      - Result: no output
- Additional Oracle-backed remediation required during full verification:
  - Restored FK-safe cleanup for Oracle integration tests by introducing `LegacyAgentArtifactsCleanup` and invoking it before deleting `REVIEW_ROUND` in workflow/manuscript/decision/e2e test setup paths.
  - Corrected `AnalysisIntentFlowTest` seed data to match the real Oracle schema (`SUBMITTER_ID`, `SUBMITTED_BY`, and legal `CURRENT_VERSION_ID` insertion order).
  - Reworked nullable version-id binds in `AnalysisIntentRepository` and `AnalysisProjectionRepository` to use explicit typed `PreparedStatement` setters, resolving the Oracle-only `ORA-17004` failure on `? IS NULL` predicates.
- Current completion state:
  - Phase 3 durable runtime implementation is complete in local code and verified through focused agent tests, full agent pytest, frontend regression, and Oracle-backed full API verification.
  - Task 19 remediation program is complete and ready to exist as an isolated commit.
  - Commit separation is handled by staging only remediation files so unrelated workspace changes remain out of the commit.
- Post-completion documentation sync completed on 2026-04-27:
  - Executed a repository-to-docs drift pass focused on `docs/ARCHITECTURE.md`, `docs/CODE_STRUCTURE.md`, `docs/DESIGN_STRUCTURE.md`, `docs/WORKFLOW.md`, `docs/TESTING.md`, and `docs/PROJECT_GUIDE.md`.
  - Updated those docs to match the live `analysis/*` API boundary, `agent_platform/*` execution runtime, current workflow endpoints, the `008_agent_platform_refactor.sql` schema, and the actual `scripts/test-all.sh` behavior.
  - Verification: targeted residue scan for legacy task-type and old endpoint text returned no matches in the touched docs; follow-up repository diff check remained clean.
  - Current completion state: Task 19 remains complete, and the core documentation drift called out in the backlog is now closed for the primary docs set.
- Agent LLM provider configuration completed on 2026-04-27:
  - Updated `services/agent/.env` to use `MODEL=Qwen/Qwen3-VL-32B-Thinking`.
  - Added `.env` loading for the current `API`, `URL`, and `MODEL` keys while retaining compatibility with previous provider key names.
  - Normalized full chat-completions URLs such as `https://api.siliconflow.cn/v1/chat/completions` to the OpenAI-compatible base URL expected by the SDK.
  - Wired `ProviderExecutor` to call the configured OpenAI-compatible provider with strict `json_schema` response format for screening, reviewer assist, and conflict analysis while preserving deterministic offline fallback when no provider config is present.
  - Added explicit provider-executor injection to `create_app()` so ordinary unit tests do not consume local `.env` credentials or require network access.
  - Verification:
    - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_provider_config.py tests/test_provider_executor.py -q` passed.
    - `cd services/agent && ../../.venv/bin/python -m pytest tests -q` passed with the existing LangGraph deprecation warnings.
    - Live SiliconFlow smoke test against `Qwen/Qwen3-VL-32B-Thinking` passed after network approval and returned a `SCREENING_ANALYSIS` payload.
  - Current completion state: Agent LLM calls now use the `.env` provider configuration, and local tests remain offline by default.
- Author workflow feedback hardening completed on 2026-04-27:
  - Rechecked the Author manuscript list actions against the live manuscript endpoints and confirmed the frontend route-to-API mapping already matched the backend for upload, download, and submit.
  - Fixed `apps/web/src/views/author/ManuscriptListView.vue` so upload, download, submit, and revision-creation actions no longer use bare awaited calls; they now route failures through the shared API error presenter and expose per-row loading states.
  - Added a focused regression in `apps/web/src/tests/workflow.spec.ts` that proves manuscript download and submit failures surface user-facing error messages instead of failing silently.
  - Verification:
    - `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts` passed.
    - Editor diagnostics reported no errors in the touched Vue and test files.
  - Current completion state: Author manuscript actions now provide explicit feedback when backend state or file prerequisites reject the request, so missing-PDF and missing-file cases no longer appear as no-ops.

## Plan Self-Review

- Spec coverage check:
  - split sovereignty: covered by Tasks 14-18
  - RabbitMQ command/event path: covered by Tasks 14-16
  - outbox/inbox consistency: covered by Tasks 14 and 16
  - durable execution job model: covered by Task 15
  - reviewer/chair/admin projection flow: covered by Tasks 16-19
  - legacy mirrored task deletion: covered by Task 18
  - observability and governance: covered by Task 19
- Placeholder scan:
  - no `TBD`, `implement later`, or deferred code steps remain in the refactor task section
- Type consistency:
  - the plan uses `AnalysisIntent`, `AnalysisProjection`, `ExecutionJob`, `AnalysisType`, and projection-oriented response contracts consistently across all tasks

- [ ] **Phase 4 / Step 1: Split WorkflowQueryService into DecisionWorkbenchQueryService**
  - Create `DecisionWorkbenchQueryService` and `DecisionWorkbenchReadRepository`.
  - Migrate `listDecisionWorkbench` to the new service.
  - Implement bulk read methods in the repository to eliminate N+1 queries.
  - Leave existing `WorkflowQueryService` controller and tests as regression guards.

### Task 20: Agent Runtime Hardening After Live LLM Provider Wiring

**Status:** Completed on 2026-04-27.

**Scope:**

- Add the latest Agent workflow review items to `TODO.md`.
- Keep ordinary tests offline while preserving the live provider path.
- Harden the immediate execution path before adding the larger RabbitMQ worker slice:
  - failed handler/provider/schema execution must not leave jobs in `RUNNING`
  - successful execution must enqueue an `analysis.completed` event in `EXECUTION_OUTBOX`
  - LLM prompts must apply a bounded input budget before serializing payloads
  - screening/conflict/reviewer schemas must apply consistent extra-field rejection

**Deferred from this slice:**

- Real RabbitMQ lifecycle workers for API outbox dispatch, Agent request consumption, Agent completion publishing, and API completion-event intake remain a separate P0 runtime slice because they require cross-service process wiring and integration verification.

**Execution notes, 2026-04-27:**

- Added the reviewed Agent workflow optimization items to `TODO.md`, marking the items completed in this slice after implementation:
  - runtime failure-state closure
  - completion-event persistence into `EXECUTION_OUTBOX`
  - LLM input budgeting
  - consistent extra-field rejection across Agent output schemas
- Added focused red tests first:
  - runtime writes `analysis.completed` to an outbox publisher
  - provider failures persist `FAILED_RETRYABLE` instead of leaving jobs in `RUNNING`
  - long `pdfText` and section payloads are truncated before prompting
  - screening/conflict schemas reject extra model output
- Implemented the green slice:
  - added `analysis_completed_topic` to `AgentPlatformConfig`
  - added `AnalysisCompletedPublisher`
  - wired `create_app()` and `AgentPlatformRuntime` with a completion-event publisher
  - `execute_requested_job(...)` now persists retryable failure state before re-raising provider/handler/schema exceptions
  - successful execution publishes `AnalysisCompletedMessage` into the execution outbox before returning the event dict
  - `ProviderExecutor` now serializes a budgeted prompt payload, truncating long `pdfText` and major section fields
  - `ScreeningAnalysisResult` and `ConflictAnalysisResult` now use `extra="forbid"` like `ReviewAssistResult`
- Verification:
  - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_execution_runtime.py tests/test_provider_executor.py tests/test_workflow_schemas.py -q` passed.
  - `cd services/agent && ../../.venv/bin/python -m pytest tests -q` passed: 40 tests, with existing LangGraph deprecation warnings.
  - Live SiliconFlow smoke test against `Qwen/Qwen3-VL-32B-Thinking` passed with a long `pdfText` payload after network approval, returning a strict `SCREENING_ANALYSIS` key set.
- Current completion state:
  - The focused Agent hardening slice is complete in local code.
  - Committed as `31d689f fix(agent): harden execution runtime and llm output handling`.
  - The larger RabbitMQ lifecycle worker slice remains open in `TODO.md` as the next P0 runtime closure item.

### Task 21: Message-Driven Runtime Lifecycle Wiring

**Status:** Completed as a focused lifecycle slice on 2026-04-27.

**Scope:**

- Execute the next P0 plan item after committing Task 20.
- Add runnable but opt-in message lifecycle wiring around the existing split-sovereignty analysis platform:
  - API outbox dispatch from `ANALYSIS_OUTBOX` to RabbitMQ
  - API completion listener that delegates broker payloads to `AnalysisEventConsumer`
  - RabbitMQ exchange, request queue, completion queue, and bindings
  - deterministic scheduler entrypoint for API outbox polling
  - Agent RabbitMQ consumer lifecycle that turns `analysis.requested` messages into execution jobs
  - Agent completion publisher loop that flushes `EXECUTION_OUTBOX` to RabbitMQ

**Execution notes, 2026-04-27:**

- Step 1 executed first: reran the Agent hardening verification and committed the completed slice as `31d689f`.
- Added red tests for the runtime lifecycle slice:
  - `AnalysisBrokerDispatchTest` covers API outbox dispatch, completion listener delegation, and deterministic scheduler polling.
  - `test_broker_worker.py` covers Agent broker request handling, completion outbox publishing, async broker publishing, and broker environment configuration.
- Implemented the green slice:
  - added `AnalysisOutboxDispatcher`, `AnalysisOutboxDispatchScheduler`, `AnalysisCompletionListener`, and `AnalysisRabbitConfiguration`
  - enabled scheduling in the Spring Boot application and added opt-in broker properties under `review.analysis`
  - changed `AnalysisOutboxRepository.pendingRequested(...)` to use an Oracle-safe `ROWNUM <= ?` ordered subquery
  - added Agent broker lifecycle settings to `AgentPlatformConfig`
  - added `AioPikaAnalysisBroker` and `AgentPlatformBrokerLifecycle`
  - wired `create_app()` startup/shutdown hooks only when `AGENT_PLATFORM_BROKER_ENABLED=true`
  - kept broker integration disabled by default so ordinary tests and local health checks do not require RabbitMQ
- Verification:
  - `cd services/agent && ../../.venv/bin/python -m pytest tests/test_broker_worker.py -q` passed.
  - `cd services/agent && ../../.venv/bin/python -m pytest tests -q` passed: 44 tests, with existing LangGraph deprecation warnings.
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisBrokerDispatchTest test` passed: 3 tests.
  - `cd apps/api && mvn -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AnalysisBrokerDispatchTest,AnalysisOutboxPublisherTest,AnalysisEventConsumerTest test` passed: 5 tests.
- Current completion state:
  - API and Agent now have opt-in runnable message lifecycle wiring.
  - The remaining open cross-cutting item is repository-level verification against real Oracle + RabbitMQ and inclusion of that path in `scripts/test-all.sh`; this remains tracked in `TODO.md`.

### Task 22: Author PDF Upload Limit Alignment

**Status:** Completed as a focused UX/runtime alignment slice on 2026-04-27.

**Scope:**

- Resolve author-facing `413 Payload Too Large` uploads by aligning the framework multipart ceiling with the manuscript service PDF limit.
- Reassess the PDF cap against mainstream conference systems and expose the chosen limit directly in the author UI.

**Execution notes, 2026-04-27:**

- Verified the local mismatch first: `ManuscriptService` enforced a 50MB business rule, but `application.yml` did not set `spring.servlet.multipart` limits, leaving uploads vulnerable to framework-level rejection before service validation.
- Checked public platform guidance before changing the cap:
  - OpenReview publicly documents a 100MB upload maximum.
  - HotCRP exposes submission upload sizing as an admin-controlled setting rather than a fixed tiny default.
- Implemented the green slice:
  - raised the manuscript PDF business limit from 50MB to 100MB
  - added Spring multipart `max-file-size: 100MB` and `max-request-size: 110MB`
  - added shared frontend upload-limit constants
  - surfaced the 100MB cap on both author upload screens
  - added client-side oversize checks so authors get an immediate message before the request is sent
- Verification:
  - `cd apps/api && mvn -Dtest=ManuscriptServiceTest test` passed: 11 tests, 0 failures, 0 errors.
  - `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts` passed: 1 file, 26 tests.
- Current completion state:
  - The upload limit is now aligned across UI hinting, client-side validation, Spring multipart handling, and backend business validation in code.

### Task 23: Empty Success Body Handling In Frontend API Client

**Status:** Completed as a focused frontend correctness slice on 2026-04-27.

**Scope:**

- Resolve the browser-side `Failed to execute 'json' on 'Response': Unexpected end of JSON input` error triggered after successful mutation requests that return an empty body.
- Keep shared API parsing logic aligned with backend endpoints such as manuscript PDF upload that intentionally return `ResponseEntity<Void>`.

**Execution notes, 2026-04-27:**

- Reproduced the local root cause from code inspection:
  - backend manuscript PDF upload returns `200 OK` with an empty response body
  - frontend `apiRequest(...)` unconditionally called `response.json()` for all non-`204` success responses
- Implemented the green slice:
  - taught `apiRequest(...)` to return `undefined` for `Content-Length: 0` success responses
  - added a defensive fallback for empty-body JSON parse `SyntaxError`
  - added a focused frontend test proving successful empty responses resolve as `undefined`
- Verification:
  - `cd apps/web && npm run test -- --run src/tests/login.spec.ts` passed: 1 file, 10 tests.
- Current completion state:
  - successful empty-body mutation responses no longer surface client-side JSON parse errors in the shared frontend API layer.

### Task 24: Reviewer Assist Progress Feedback And Review Editor Layout

**Status:** Completed as a focused reviewer UX slice on 2026-04-27.

**Design reference:** `docs/superpowers/specs/2026-04-27-reviewer-assist-progress-and-editor-layout-design.md`

**Scope:**

- Add immediate "request submitted / analysis in progress" feedback after reviewer assist is started.
- Poll the assignment-scoped reviewer assist API until a projection is available or a failed status is returned.
- Show a lightweight animated in-progress treatment and explicit error/Retry states.
- Rebalance the Review editor layout toward the paper reader, make the right panel collapsible, and collapse Assignment details by default.

**Files:**

- Modify: `apps/web/src/components/reviewer/ReviewerAgentPanel.vue`
- Modify: `apps/web/src/views/reviewer/ReviewEditorView.vue`
- Modify: `apps/web/src/style.css`
- Modify: `apps/web/src/tests/workflow.spec.ts`
- Modify: `docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md`

- [x] **Step 1: Write failing frontend tests for reviewer assist progress and editor layout**

Run: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts`

Expected before implementation: focused assertions fail because the in-progress status, polling behavior, failed Retry state, default-collapsed Assignment panel, and collapsible right panel are not implemented.

- [x] **Step 2: Implement reviewer assist polling and progress UI**

Update `ReviewerAgentPanel.vue` to keep the returned intent visible, start a bounded polling loop after POST success, show an animated progress block while status is pending, stop timers on unmount/assignment changes, and show Retry for failed visible states.

- [x] **Step 3: Implement the Review editor layout changes**

Update `ReviewEditorView.vue` and `style.css` so Assignment details are collapsed by default, the side panel can be collapsed, the reader receives the dominant desktop width, and the workspace stays aligned to the header width.

- [x] **Step 4: Run focused frontend verification**

Run: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts`

Expected after implementation: workflow spec passes.

- [x] **Step 5: Run frontend typecheck and build**

Run:

```bash
cd apps/web && npm run typecheck
cd apps/web && npm run build
```

Expected after implementation: both commands pass.

**Execution notes, 2026-04-27:**

- Root cause confirmed from the red test run:
  - POST `/review-assignments/{assignmentId}/agent-assist` accepted the request, but the frontend performed only one follow-up GET and rendered the empty-state alert while asynchronous execution was still pending.
  - `FAILED_VISIBLE` states exposed Retry but did not include an explicit failure explanation.
  - Review editor layout used a broad right column and always-open Assignment descriptions, leaving the reader visually compressed.
- Implemented the green slice:
  - `ReviewerAgentPanel.vue` now preserves the returned intent, shows "Analysis in progress" immediately, renders a shimmer/pulse progress animation, polls every three seconds until projection availability or failure, and clears polling on assignment changes or unmount.
  - Reviewer assist projections now show `summaryText` before the structured redacted JSON.
  - `ReviewEditorView.vue` now provides a collapsible right panel and default-collapsed Assignment details with a compact summary.
  - `style.css` now gives the reader the dominant desktop width, keeps the workspace aligned to the page header, and styles the progress animation and collapsed assignment block.
  - `workflow-format.ts` now treats `FAILED_VISIBLE` as a danger status.
  - `AGENTS.md` now captures the reusable lesson for actor-facing asynchronous agent actions.
- Verification:
  - Red run before implementation: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts` failed with three expected assertions for missing progress, failure feedback, and layout controls.
  - Green run after implementation: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts` passed: 1 file, 29 tests.
  - `cd apps/web && npm run typecheck` passed.
  - `cd apps/web && npm run build` passed; Vite reported the existing large chunk warning.
  - API use-case check: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RequestReviewerAssistUseCaseTest test` passed.
  - Oracle-backed API flow check attempted with `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RequestReviewerAssistUseCaseTest,AnalysisIntentFlowTest test`; `AnalysisIntentFlowTest` was blocked by local Oracle connectivity in the sandbox with `ORA-17820` and `SocketException: Operation not permitted`.
  - Current completion state:
  - Reviewer assist request acceptance is now visible, pending execution is animated and auto-refreshed, failed states have explicit Retry feedback, and the Review editor prioritizes paper reading space with a collapsible auxiliary column.

### Task 25: Conference Onboarding And Reviewer Assignment Design

**Status:** Designed on 2026-05-06; review feedback incorporated and awaiting final user review before implementation planning.

**Design reference:** `docs/superpowers/specs/2026-05-06-conference-onboarding-and-assignment-design.md`

**Scope:**

- Define a simplified mature conference-management upgrade path without adding a full venue-workflow configuration engine.
- Add three public registration types: Author, Reviewer, and Organizer, while keeping Admin internal.
- Add conference/CFP publishing, conference-scoped submission, reviewer pool, conflicts, bidding, guided reviewer assignment, and Agent-assisted assignment recommendations.
- Reuse the current AnalysisIntent/outbox/RabbitMQ/Agent projection platform for a new `REVIEWER_ASSIGNMENT_ASSIST` type.
- Keep final assignment authority in the Spring API; Agent output remains advisory and chair-confirmed.

**Execution notes, 2026-05-06:**

- Reviewed the current repository context first, including auth, manuscript, review assignment, Oracle schema, frontend routes, and the active plan file.
- Compared current gaps against mature conference systems:
  - EasyChair for CFP, submission, reviewer management, preference-based assignment, communication, and monitoring.
  - OpenReview for venue workflow stages, user groups, bidding, assignment, decision, and visibility concepts.
  - HotCRP and CMT for bidding, conflict checks, and assignment workflows.
- User approved the simplified integrated direction:
  - public registration is limited to Author, Reviewer, and Organizer
  - conference phases are fixed lifecycle states instead of arbitrary custom workflow configuration
  - reviewer assignment uses guided recommendations with deterministic constraints
  - Agent assignment assist is a new analysis type, not a repurposed reviewer-assist endpoint
- Verification:
  - Spec self-review checked for placeholder markers and scope contradictions.
  - No implementation code was changed in this design cycle.
- Review remediation, 2026-05-06:
  - Clarified that public Organizer registration grants the existing global `CHAIR` role after Admin approval; no durable `ORGANIZER` role is introduced.
  - Moved Reviewer global approval to Admin only, with Chair/Admin responsible only for conference-scoped `CONFERENCE_REVIEWER` membership.
  - Added migration rules for `010_conference_onboarding.sql`, including nullable `MANUSCRIPT.CONFERENCE_ID`, legacy/default conference backfill, and later optional `NOT NULL` tightening.
  - Declared `CONFERENCE.BLIND_MODE` authoritative for new submissions and `MANUSCRIPT.BLIND_MODE` an immutable snapshot.
  - Required de-identified bidding previews for double-blind workflows.
  - Changed `REVIEWER_ASSIGNMENT_ASSIST` to one manuscript version per request, with `roundId` only as workflow context.
  - Specified manual/idempotent phase transitions with timestamp-based boundary checks, runtime reviewer-load aggregation, reuse of `CONFLICT_CHECK_RECORD`, public-registration abuse controls, Admin provisioning, multi-conference isolation tests, and reviewer approval evidence.
  - Updated `AGENTS.md` with reusable design rules from the review.
- Second review remediation, 2026-05-06:
  - Split registration/profile storage into `SYS_USER`, `USER_ACADEMIC_PROFILE`, existing `USER_RESEARCH_AREA`, `ROLE_APPLICATION`, and `EMAIL_VERIFICATION_TOKEN`, with independent multi-role applications per user.
  - Clarified `USER_RESEARCH_AREA` as the platform-level source and `CONFERENCE_REVIEWER` as the conference-scoped snapshot owner.
  - Added the minimal email-delivery boundary for verification, approval, rejection, and reviewer invitation, with SMTP production config and fake/logging local/test adapters.
  - Specified `ASSIGNMENT_DRAFT` as one row per proposed `(ROUND_ID, REVIEWER_ID)` candidate, not an opaque JSON bundle.
  - Required assignment confirmation to lock selected `CONFERENCE_REVIEWER` rows and recompute confirmed load in the transaction before inserting `REVIEW_ASSIGNMENT`.
  - Expanded phase-boundary semantics for submission close, bidding close, review deadline, and decision release.
  - Added backend test requirements for multi-role users, profile persistence, fake-email verification, phase boundaries, and concurrent assignment confirmation.
  - Updated `AGENTS.md` with reusable rules for profile ownership, email verification boundaries, draft granularity, assignment locking, and phase deadline semantics.
- Current completion state:
  - Revised written design is ready for user review.
  - Next step after approval is to invoke the writing-plans workflow and create an implementation plan anchored to this authoritative plan file.

**Planned implementation slices after spec approval:**

- [x] **Task 25.1: Registration and approval foundation**
  - Add public registration APIs/UI for Author, Reviewer, and Organizer-to-Chair approval.
  - Include `SYS_USER`, `USER_ACADEMIC_PROFILE`, `ROLE_APPLICATION`, `EMAIL_VERIFICATION_TOKEN`, email verification, token expiry, fake/logging and SMTP email adapters, rate-limit hooks, Admin-only reviewer approval, multi-role users, and Admin provisioning rules.
  - Backend execution slice:
    - Add `012_registration_foundation.sql` with `USER_ACADEMIC_PROFILE`, `ROLE_APPLICATION`, and `EMAIL_VERIFICATION_TOKEN`; extend `SYS_USER.STATUS` with `PENDING_EMAIL_VERIFICATION`; add indexes and schema verification coverage.
    - Add registration domain/application classes under `apps/api/src/main/java/com/example/review/registration`.
    - Add public endpoints under `/api/auth/register` and `/api/auth/verify-email`.
    - Add admin endpoints under `/api/admin/role-applications` for listing pending applications and approving/rejecting Reviewer and Organizer applications.
    - Use a fake/logging email adapter that records verification delivery locally without storing raw tokens in Oracle.
    - TDD target: `RegistrationServiceTest`, `RegistrationSchemaTest`, and focused auth/security route checks.
    - First completion point: authors can self-register and become `AUTHOR` after email verification; reviewers and organizers can register, verify email, and wait for Admin approval; Admin approval grants `REVIEWER` or existing `CHAIR`.
  - Execution notes, 2026-05-06:
    - Added the backend registration service, controller, JDBC repository, fake/logging verification email gateway, and UTC clock bean.
    - Added public `/api/auth/register` and `/api/auth/verify-email` endpoints.
    - Added Admin-only `/api/admin/role-applications` list/approve/reject endpoints.
    - Added `012_registration_foundation.sql` with `USER_ACADEMIC_PROFILE`, `ROLE_APPLICATION`, `EMAIL_VERIFICATION_TOKEN`, `PENDING_EMAIL_VERIFICATION`, role-application status/type constraints, and non-redundant lookup indexes.
    - Wired `012` into `oracle-schema-apply.sh`, `dev-up.sh`, and `verify_schema.sql`.
    - Registration behavior now supports Author email self-approval, Reviewer admin approval, Organizer approval to existing `CHAIR`, reviewer evidence validation, hashed single-use email tokens, and multi-role-friendly `(USER_ID, REGISTRATION_TYPE)` application uniqueness.
    - Verification:
      - Red run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RegistrationServiceTest test` failed with missing registration classes.
      - Red run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RegistrationSchemaTest test` failed with missing `012_registration_foundation.sql`.
      - Green run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RegistrationServiceTest,RegistrationSchemaTest,CodeQualityTest test` passed.
      - `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -DskipTests test` passed.
      - `bash -n scripts/oracle-schema-apply.sh` and `bash -n scripts/dev-up.sh` passed.
    - Current completion state:
      - 25.1 backend registration foundation is implemented and locally verified without Oracle connectivity.
      - Frontend registration tabs and Admin approval workbench remain for the next frontend slice.
  - Frontend execution notes, 2026-05-06:
    - Added `/register` as a public route with Author, Reviewer, and Organizer tabs.
    - Added a registration form that posts to `/api/auth/register`, includes academic evidence fields for Reviewer/Organizer, and shows the email-verification next step after success.
    - Added `/admin/role-applications` as an Admin-only route and workbench for pending role applications with approve/reject actions.
    - Added a Dashboard entry for Admin role applications and a Login page link to account creation.
    - Verification:
      - Red run: `cd apps/web && npm run test -- --run src/tests/registration.spec.ts` failed because `RegisterView.vue` did not exist.
      - Green run: `cd apps/web && npm run test -- --run src/tests/registration.spec.ts` passed.
      - `cd apps/web && npm run typecheck` passed.
      - `cd apps/web && npm run build` passed with the existing large chunk warning.
    - Current completion state:
      - 25.1 now has both backend and frontend foundations for public registration and Admin approval.
      - Full browser/API e2e verification remains dependent on an Oracle-backed local stack.
  - Continuation notes, 2026-05-06:
    - Checked the in-progress Admin payload-summary work after user reported registration `Internal Server Error`.
    - Found backend work partly complete: `GET /api/admin/role-applications` had been moved toward `RoleApplicationDetail` and `JdbcRegistrationRepository` was parsing `SUBMITTED_PAYLOAD_JSON`, but frontend `RoleApplicationsView.vue` still rendered only application id, user id, type, and status.
    - Root-cause evidence for the registration `Internal Server Error` could not be read from live API logs because no API process was listening on `localhost:8080` in this environment. The most likely runtime cause remains an unapplied registration migration: registration writes `SYS_USER.STATUS = 'PENDING_EMAIL_VERIFICATION'` and inserts into `ROLE_APPLICATION` and `EMAIL_VERIFICATION_TOKEN`, so local Oracle must have `012_registration_foundation.sql` applied.
    - Fixed the in-progress schema follow-up so newly added email-token indexes are delivered by `013_registration_token_indexes.sql` rather than mutating already-committed `012_registration_foundation.sql`; wired `013` into full apply and incremental `dev-up.sh`.
    - Changed `USER_ACADEMIC_PROFILE` persistence from insert-only to `MERGE` so a rejected Reviewer/Organizer can resubmit with the same account without hitting the profile unique key and surfacing a 500.
    - Added frontend test coverage that fails unless the Admin role-application workbench renders applicant identity, profile links, representative works, conflict domains, planned conference title, and research-area summary.
    - Updated `RoleApplicationsView.vue` to display the payload summary fields returned by `RoleApplicationDetail`.
    - Verification:
      - Red run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RegistrationSchemaTest test` failed because `013_registration_token_indexes.sql` was missing.
      - Red run: `cd apps/web && npm run test -- --run src/tests/registration.spec.ts` failed because the Admin workbench did not render `New Reviewer` from the payload summary.
      - Green run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RegistrationServiceTest,RegistrationSchemaTest,CodeQualityTest test` passed.
      - Green run: `cd apps/web && npm run test -- --run src/tests/registration.spec.ts` passed.
      - `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -DskipTests test` passed.
      - `cd apps/web && npm run typecheck` passed.
      - `cd apps/web && npm run build` passed with the existing large chunk warning.
      - `.venv/bin/python -m pytest services/agent/tests/test_health.py -q` passed.
      - `bash -n scripts/oracle-schema-apply.sh && bash -n scripts/dev-up.sh` passed.
      - `git diff --check` passed.
      - `bash scripts/test-all.sh` was attempted but did not enter test execution because the local Docker engine was unreachable and the Oracle container `review-oracle` was not running.
    - Current completion state:
      - Task 25.1 is closed in this plan ledger after baseline verification on the Task 25.2 worktree.
      - Admin workbench payload-summary support is implemented at backend DTO/query and frontend rendering levels.
      - Focused baseline verification passed with `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=RegistrationServiceTest,RegistrationSchemaTest,CodeQualityTest test`.
      - Full browser/API e2e verification remains dependent on an Oracle-backed local stack where migrations `012` and `013` are applied.
- [x] **Task 25.2: Conference and CFP schema/lifecycle**
  - Add `014_conference_cfp_lifecycle.sql`, `CONFERENCE`, `CONFERENCE_PHASE`, lifecycle services, public CFP reads, Admin conference approval, and schema verification.
  - Execution notes, 2026-05-06:
    - Corrected the originally planned migration number from `010_conference_onboarding.sql` to `014_conference_cfp_lifecycle.sql` because migrations `010` through `013` already exist and may have been applied locally.
    - Added the `conference` API package with DTO/domain records, repository boundary, JDBC repository, service, controller, and service-layer exceptions.
    - Added Chair/Admin draft creation, organizer/admin approval submission, Admin approval to `OPEN_FOR_SUBMISSION`, ordered idempotent lifecycle advancement, pending Admin approval reads, and public CFP list/detail reads.
    - Kept public CFP reads open before authentication by adding explicit GET permit rules for `/api/conferences/cfp` and `/api/conferences/cfp/**`; Chair/Admin mutation endpoints remain behind the existing authenticated `/api/**` and admin/chair service guards.
    - Added `CONFERENCE` and `CONFERENCE_PHASE` Oracle objects, fixed lifecycle/status constraints, blind-mode constraints, phase ordering constraints, public slug uniqueness, non-redundant lookup indexes, sequences, triggers, and foreign keys to `SYS_USER`.
    - Wired `014_conference_cfp_lifecycle.sql` into `scripts/oracle-schema-apply.sh`, incremental `scripts/dev-up.sh`, and `verify_schema.sql`, raising expected schema counts to 28 tables, 27 sequences, 30 triggers, and 42 custom indexes.
    - Updated registration/schema and code-quality guards to reflect the new schema totals and prevent migration-number regression to the already-used `010` slot.
    - Verification:
      - Red run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ConferenceServiceTest,ConferenceSchemaTest test` failed with missing conference classes and missing `014_conference_cfp_lifecycle.sql`.
      - Green run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ConferenceServiceTest,ConferenceSchemaTest test` passed.
      - Focused guard run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ConferenceServiceTest,ConferenceSchemaTest,RegistrationSchemaTest,CodeQualityTest test` passed.
      - API compile check: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -DskipTests test` passed.
      - Script checks: `bash -n scripts/oracle-schema-apply.sh` and `bash -n scripts/dev-up.sh` passed.
      - Whitespace check: `git diff --check` passed.
    - Current completion state:
      - 25.2 backend/schema lifecycle slice is implemented and locally verified.
      - Real Oracle migration verification was completed after user enabled Docker/Oracle access:
        - `docker exec review-oracle bash -lc "sqlplus -s review_app/ReviewApp12345@localhost/FREEPDB1 @/tmp/014_conference_cfp_lifecycle.sql"` created the conference tables, sequences, constraints, indexes, and triggers.
        - The first `verify_schema.sql` run failed with `Expected 28 tables, found 25`; investigation showed the local database was missing prior registration objects from `012_registration_foundation.sql`.
        - Applied missing prior migrations `012_registration_foundation.sql` and `013_registration_token_indexes.sql`.
        - The next `verify_schema.sql` run failed with `Expected 42 indexes, found 35`; investigation showed the local database was missing the seven hotspot indexes from `010_database_query_optimization.sql`.
        - Applied missing prior migration `010_database_query_optimization.sql`.
        - Final real Oracle verification passed with `Schema verification passed.`
- [x] **Task 25.3: Conference-scoped submission migration**
  - Add nullable/backfilled `MANUSCRIPT.CONFERENCE_ID`, legacy/default conference support, conference blind-mode snapshot behavior, and author conference selection.
  - Planned execution scope:
    - Add migration `015_conference_scoped_submission.sql` after the completed conference lifecycle migration.
    - Add nullable `MANUSCRIPT.CONFERENCE_ID`, a foreign key to `CONFERENCE`, and a lookup index; backfill existing manuscripts to an idempotent `legacy-platform-default` conference.
    - Extend schema verification and both bootstrap paths for migration `015`.
    - Update manuscript API request/response/list rows to carry `conferenceId`.
    - Add a manuscript-side conference lookup repository that returns only valid author submission targets and exposes the conference blind-mode snapshot.
    - Change initial manuscript creation so callers must choose an open conference, the manuscript snapshot copies `CONFERENCE.BLIND_MODE`, and submissions/PDF replacement reject after `CONFERENCE_PHASE.SUBMISSION_CLOSE_AT`.
    - Add frontend author submission selection from public CFPs; hide manual blind-mode selection because blind mode is now conference-owned.
    - Verification target: focused backend manuscript/schema/code-quality tests, focused frontend workflow test, typecheck/build if frontend changes compile cleanly, script syntax checks, and real Oracle `verify_schema.sql` after applying `015`.
  - Execution notes, 2026-05-06:
    - Added `015_conference_scoped_submission.sql` with nullable `MANUSCRIPT.CONFERENCE_ID`, idempotent `legacy-platform-default` conference and phase backfill, `FK_MANUSCRIPT_CONFERENCE`, and `IDX_MANUSCRIPT_CONFERENCE_STATUS`.
    - Wired `015_conference_scoped_submission.sql` into full schema apply, incremental `dev-up.sh`, and `verify_schema.sql`; schema verification now checks `MANUSCRIPT.CONFERENCE_ID`, `FK_MANUSCRIPT_CONFERENCE`, and 43 custom indexes.
    - Updated manuscript create/list/detail DTOs and repository rows to carry `conferenceId`.
    - Added `ManuscriptConferenceRepository` for manuscript-side conference submission policy reads without coupling manuscript service to conference controller DTOs.
    - Changed initial manuscript creation so `conferenceId` is required, the target conference must be `OPEN_FOR_SUBMISSION`, the submission close timestamp must still be in the future, and `MANUSCRIPT.BLIND_MODE` is copied from `CONFERENCE.BLIND_MODE` instead of trusting the client payload.
    - Added submission-deadline checks to PDF upload/replacement and final version submission.
    - Updated author submission UI to load public CFPs, auto-select the first available conference, display the conference-owned blind mode/deadline, and submit `conferenceId` with the manuscript payload.
    - Verification:
      - Red run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ConferenceSubmissionSchemaTest,ManuscriptServiceTest test` first failed in the sandbox due to Oracle socket restrictions, then outside the sandbox failed with missing `015_conference_scoped_submission.sql`, missing `conferenceId` response data, and missing deadline guards.
      - Applied real Oracle migration with `docker exec review-oracle bash -lc "sqlplus -s review_app/ReviewApp12345@localhost/FREEPDB1 @/tmp/015_conference_scoped_submission.sql"`, creating the column, default conference backfill, FK, and index.
      - Green backend run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ConferenceSubmissionSchemaTest,ManuscriptServiceTest,ConferenceSchemaTest,RegistrationSchemaTest,CodeQualityTest test` passed.
      - API compile check: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -DskipTests test` passed.
      - Frontend red run: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts` initially could not start because the new worktree lacked `node_modules`; a temporary ignored symlink to the main worktree's `apps/web/node_modules` was used for verification only.
      - Frontend green run: `cd apps/web && npm run test -- --run src/tests/workflow.spec.ts` passed with 27 tests.
      - `cd apps/web && npm run typecheck` passed.
      - `cd apps/web && npm run build` passed with the existing Vite large chunk warning.
      - `bash -n scripts/oracle-schema-apply.sh` and `bash -n scripts/dev-up.sh` passed.
      - Real Oracle `verify_schema.sql` passed after updating the verification SQL in the container.
    - Current completion state:
      - 25.3 conference-scoped submission migration is implemented, frontend selection is wired, and the local Oracle schema has been migrated through `015`.
- [x] **Task 25.4: Reviewer pool, conflicts, and bidding**
  - Add platform-approved reviewer pool membership, `CONFERENCE_REVIEWER` research-area snapshots from `USER_RESEARCH_AREA`, de-identified bidding views, bid persistence, conflict reuse through `CONFLICT_CHECK_RECORD`, phase boundary checks, and multi-conference isolation checks.
  - Execution notes, 2026-05-07:
    - Added `016_reviewer_pool_bidding.sql` with `CONFERENCE_REVIEWER`, `REVIEWER_BID`, sequences, triggers, lookup indexes, and nullable `CONFLICT_CHECK_RECORD.ASSIGNMENT_ID` so bidding-stage self-declared conflicts can reuse the existing conflict table before assignments exist.
    - Wired `016_reviewer_pool_bidding.sql` into full Oracle apply, incremental `dev-up.sh`, and `verify_schema.sql`; schema verification now expects 30 tables, 29 sequences, 32 triggers, and 47 custom indexes.
    - Added chair/admin reviewer-pool APIs under `/api/chair/conferences/{conferenceId}/reviewers`; the service only admits active platform `REVIEWER` users and snapshots their current `USER_RESEARCH_AREA` rows into `CONFERENCE_REVIEWER.RESEARCH_AREAS_JSON`.
    - Added reviewer bidding APIs under `/api/reviewer/conferences/{conferenceId}/bids/open` and `/api/reviewer/conferences/{conferenceId}/bids`; bidding is restricted to active conference-pool reviewers, `BIDDING_OPEN` conferences, and `BIDDING_CLOSE_AT` in the future.
    - Bidding reads are conference-scoped, exclude other conferences' manuscripts, return only title/abstract/keywords/bid state, and redact submitted author names, emails, and institutions from those fields.
    - Bid submission upserts `REVIEWER_BID`; self-declared conflict bids create one `CONFLICT_CHECK_RECORD` row with `ASSIGNMENT_ID = NULL` and `SOURCE = SELF_DECLARED`.
    - Verification:
      - Red run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ConferenceReviewerBiddingSchemaTest,ConferenceReviewerBiddingServiceTest test` failed because `016_reviewer_pool_bidding.sql` was missing; the service integration path was also blocked inside the normal sandbox by Oracle socket permissions.
      - Real Oracle migration: `docker cp database/oracle/016_reviewer_pool_bidding.sql review-oracle:/tmp/016_reviewer_pool_bidding.sql && docker exec review-oracle bash -lc "sqlplus -s review_app/ReviewApp12345@localhost/FREEPDB1 @/tmp/016_reviewer_pool_bidding.sql"` created the reviewer-pool and bidding objects.
      - First real Oracle green attempt exposed a test seed order bug against `FK_MANUSCRIPT_CURRENT_VERSION`; fixed the test to insert manuscript, insert version, then update current version.
      - Green run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ConferenceReviewerBiddingServiceTest test` passed with Oracle access.
      - Focused guard run: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ConferenceReviewerBiddingSchemaTest,ConferenceReviewerBiddingServiceTest,ConferenceSchemaTest,ConferenceSubmissionSchemaTest,RegistrationSchemaTest,CodeQualityTest test` passed with Oracle access.
      - Real Oracle `verify_schema.sql` passed after copying the updated verification SQL into the container.
      - API compile check: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -DskipTests test` passed.
      - Script checks: `bash -n scripts/oracle-schema-apply.sh && bash -n scripts/dev-up.sh` passed.
      - Whitespace check: `git diff --check` passed.
    - Current completion state:
      - 25.4 backend/schema slice is implemented and verified against the local Oracle container.
      - Frontend bidding screens are not included in 25.4 and remain part of the later integrated frontend workflow slice.
- [ ] **Task 25.5: Guided deterministic assignment**
  - Add ranked candidate queries, hard constraint filtering, runtime load calculation, per-candidate assignment drafts, bulk chair confirmation with selected reviewer row locks, and final `REVIEW_ASSIGNMENT` validation.
- [ ] **Task 25.6: Reviewer assignment Agent assist**
  - Add `REVIEWER_ASSIGNMENT_ASSIST` across API analysis type, intent use case, Agent handler, projection, and chair-facing progress/error UI.
- [ ] **Task 25.7: Integrated frontend workflows**
  - Add Public CFP, Register tabs, Author conference submission path, Chair conference console, Admin approval workbench, and route-guard coverage.
- [ ] **Task 25.8: Operational docs and verification**
  - Update README, architecture, workflow, code-structure, testing, demo docs, seed scripts, and repository verification commands for the new conference flow.

### Task 26: Database Query Optimization And Read Model Cleanup

**Status:** Completed as a focused database/query optimization slice on 2026-05-06.

**Scope:**

- Add indexes for current workflow and analysis query hotspots identified from repository code.
- Make `verify_schema.sql` validate both base business indexes and analysis/execution indexes.
- Wire the latest schema migrations into full schema apply and incremental local dev bootstrap.
- Route the chair decision workbench endpoint through the existing bulk read model instead of the old per-round N+1 path.

**Files:**

- Modify: `apps/api/src/main/java/com/example/review/workflow/WorkflowQueryController.java`
- Modify: `apps/api/src/main/java/com/example/review/workflow/WorkflowQueryService.java`
- Modify: `apps/api/src/test/java/com/example/review/CodeQualityTest.java`
- Create: `database/oracle/010_database_query_optimization.sql`
- Modify: `database/oracle/verify_schema.sql`
- Modify: `scripts/oracle-schema-apply.sh`
- Modify: `scripts/dev-up.sh`
- Modify: `TODO.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md`

**Execution notes, 2026-05-06:**

- Reviewed current Oracle schema, index declarations, verification SQL, and repository query paths.
- Confirmed the main schema dependencies remain reasonable, but found real query hotspots without matching verification coverage:
  - decision workbench round status scan
  - assignment lookup by round
  - reviewer paper access lookup by manuscript/version/reviewer
  - review report counts and lists by round
  - conflict lookup by manuscript/reviewer
  - screening queue status/date ordering
  - analysis projection ordering
- Red tests:
  - Added `CodeQualityTest.databaseQueryHotspotsHaveSchemaIndexesAndVerificationCoverage`, which failed because the new hotspot indexes and migration wiring were absent.
  - Added `CodeQualityTest.decisionWorkbenchEndpointUsesBulkReadModel`, which failed because the endpoint still delegated to the old `WorkflowQueryService` N+1 implementation.
- Implemented the green slice:
  - Added `database/oracle/010_database_query_optimization.sql` with seven hotspot indexes.
  - Expanded `verify_schema.sql` index verification from the 14 analysis/execution indexes to all 38 custom indexes.
  - Updated `scripts/oracle-schema-apply.sh` to apply `009_execution_job_attempt_count.sql` and `010_database_query_optimization.sql`.
  - Updated `scripts/dev-up.sh` to detect and apply missing 009/010 incremental schema pieces on existing local Oracle databases.
  - Changed `WorkflowQueryController` so `/api/chair/decision-workbench` uses `DecisionWorkbenchQueryService`.
  - Removed the old `WorkflowQueryService` per-round decision workbench assembly helpers.
  - Marked the TODO item for decision-workbench N+1 cleanup complete.
  - Updated `AGENTS.md` with reusable rules about page-shaped read models, composite query indexes, schema verification coverage, and migration bootstrap wiring.
- Verification:
  - Red run before implementation: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=CodeQualityTest test` failed with the expected missing index/migration and bulk-read assertions.
  - Green run after implementation: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=CodeQualityTest test` passed.
  - `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=DecisionWorkbenchQueryTest,AdminAnalysisMonitorQueryTest test` passed.
  - `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=WorkflowQueryServiceTest test` was attempted but could not reach Oracle in the current sandbox (`ORA-17820`, `java.net.SocketException: Operation not permitted`), so endpoint-level Oracle integration verification remains blocked by environment access rather than a failed assertion.
- Current completion state:
  - Query hotspot indexes are declared and covered by schema verification.
  - Local schema bootstrap now knows about 009 and 010.
  - The chair decision workbench endpoint now uses the bulk read service.
  - Legacy `AGENT_*` table retirement was completed later in Task 27.

### Task 27: Legacy AGENT Table Retirement

**Status:** Completed as a focused schema retirement slice on 2026-05-06.

**Scope:**

- Retire the old mirrored `AGENT_ANALYSIS_TASK`, `AGENT_ANALYSIS_RESULT`, and `AGENT_FEEDBACK` tables now that analysis intent/projection and execution job tables own the live Agent path.
- Remove active schema, seed, trigger, procedure, index, verification, and test-cleanup dependencies on those tables.
- Add one idempotent drop migration for existing local Oracle databases.

**Files:**

- Modify: `database/oracle/001_init.sql`
- Modify: `database/oracle/003_indexes.sql`
- Modify: `database/oracle/004_procedures.sql`
- Modify: `database/oracle/005_triggers.sql`
- Modify: `database/oracle/007_seed_demo_workflow.sql`
- Modify: `database/oracle/verify_schema.sql`
- Create: `database/oracle/011_retire_legacy_agent_tables.sql`
- Modify: `scripts/oracle-schema-apply.sh`
- Modify: `scripts/dev-up.sh`
- Modify: `apps/api/src/test/java/com/example/review/CodeQualityTest.java`
- Delete: `apps/api/src/test/java/com/example/review/support/LegacyAgentArtifactsCleanup.java`
- Modify: Oracle-backed API tests that previously imported the legacy cleanup helper
- Modify: `TODO.md`
- Modify: `AGENTS.md`

**Execution notes, 2026-05-06:**

- Committed the previous database optimization slice first as `8cf8529 perf(api): optimize database query hotspots`.
- Audited current references with `rg`; live references were limited to Oracle schema/seed/verification files, migration/bootstrap scripts, and test cleanup code.
- Red test:
  - Added `CodeQualityTest.legacyAgentTablesAreRetiredFromActiveSchemaAndTestCleanup`.
  - The first run failed on `database/oracle/001_init.sql` still containing `AGENT_ANALYSIS_TASK`, proving the guard caught the active-schema dependency.
- Implemented the green slice:
  - Removed legacy Agent table definitions, sequences, foreign keys, triggers, indexes, procedure, and demo seed rows from active schema files.
  - Updated `verify_schema.sql` expected counts to 23 tables, 22 sequences, 25 triggers, 2 procedures, and 34 indexes.
  - Added `011_retire_legacy_agent_tables.sql` to drop old triggers, procedure, tables, and sequences idempotently for existing databases.
  - Wired `011` into full schema apply and incremental `dev-up.sh` bootstrap when `AGENT_ANALYSIS_TASK` is still present.
  - Deleted `LegacyAgentArtifactsCleanup` and removed its imports/calls from Oracle-backed tests so tests no longer hide a dependency on retired tables.
  - Marked the legacy `AGENT_*` TODO item complete.
  - Added an `AGENTS.md` rule requiring legacy retirement slices to remove active dependencies and keep only one idempotent drop migration.
- Verification:
  - Red run before implementation: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=CodeQualityTest test` failed with the expected active-schema legacy table assertion.
  - Green run after implementation: `cd apps/api && mvn -q -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=CodeQualityTest test` passed.
  - `bash -n scripts/oracle-schema-apply.sh` passed.
  - `bash -n scripts/dev-up.sh` passed.
  - `rg` over active schema and API tests found no remaining legacy table/procedure/trigger/helper references outside the intentional guard test and `011` drop migration.
- Current completion state:
  - The legacy mirrored Agent tables are retired from active schema and test cleanup paths.
  - Existing databases have an idempotent migration path to drop the old objects.
  - Full Oracle execution of the migration is not verified in this sandbox because Oracle connectivity is blocked here.
