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
