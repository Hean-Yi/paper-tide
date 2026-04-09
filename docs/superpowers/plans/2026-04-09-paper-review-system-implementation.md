# Paper Review System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved intelligent paper review system described in the design spec, including the Spring Boot main system, Oracle schema, FastAPI/LangGraph agent service, and Vue frontend.

**Architecture:** Use a small monorepo with `apps/web`, `apps/api`, `services/agent`, and `database/oracle`. Build the core workflow first: authentication, manuscript/version management, review state machines, review submission, chair decisions, then add async agent integration and UI polish.

**Tech Stack:** Vue 3, Element Plus, Vite, Java 21, Spring Boot, Spring Security, JWT, MyBatis or Spring Data JDBC, Oracle, Python 3.11+, FastAPI, LangGraph, pytest, Vitest, Playwright.

---

## Target Repository Structure

This plan assumes the implementation will use the following file layout:

- `apps/api/`
- `apps/api/pom.xml`
- `apps/api/src/main/java/com/example/review/`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/test/java/com/example/review/`
- `apps/web/package.json`
- `apps/web/src/`
- `apps/web/src/views/`
- `services/agent/pyproject.toml`
- `services/agent/app/`
- `services/agent/tests/`
- `database/oracle/001_init.sql`
- `database/oracle/002_seed_roles.sql`
- `database/oracle/003_indexes.sql`
- `database/oracle/004_procedures.sql`
- `database/oracle/005_triggers.sql`
- `scripts/dev-up.sh`
- `scripts/test-all.sh`

## Milestones

1. Repository and runtime scaffolding
2. Oracle schema and backend authentication
3. Manuscript/version/review workflow backend
4. Agent service and integration
5. Frontend workflow screens
6. End-to-end verification and demo preparation

## Current Execution Status

- Task 1 completed on 2026-04-09
- Task 2 completed on 2026-04-09
- Task 3 completed on 2026-04-09
- Local environment bootstrap completed on 2026-04-09. Java, Maven, Node/npm, project `.venv`, frontend dependencies, Colima/Docker, and a local Oracle Free container are ready. Oracle schema import and verification passed inside the container.

## Task 1: Scaffold the Monorepo

**Status:** Completed on 2026-04-09 after spec-compliance review and code-quality review. Full local verification was completed later on 2026-04-09 after environment bootstrap, including Maven tests, pytest, Vitest, Vue TypeScript type-checking, and Vite production build. A follow-up review pass also added a root `.gitignore`, switched the frontend entry to TypeScript, and declared the selected frontend stack dependencies (`Element Plus`, `vue-router`).

**Files:**
- Create: `apps/api/pom.xml`
- Create: `apps/api/src/main/java/com/example/review/ReviewApplication.java`
- Create: `apps/api/src/main/resources/application.yml`
- Create: `apps/web/package.json`
- Create: `apps/web/vite.config.ts`
- Create: `services/agent/pyproject.toml`
- Create: `services/agent/app/main.py`
- Create: `scripts/dev-up.sh`
- Create: `scripts/test-all.sh`

- [ ] **Step 1: Create the backend skeleton**

```java
package com.example.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewApplication.class, args);
    }
}
```

- [ ] **Step 2: Add a minimal backend health endpoint**

```java
package com.example.review.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping
    public Map<String, String> getHealth() {
        return Map.of("status", "ok");
    }
}
```

- [ ] **Step 3: Add a minimal FastAPI health endpoint**

```python
from fastapi import FastAPI

app = FastAPI()

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
```

- [ ] **Step 4: Add a minimal Vue application entry**

```ts
import { createApp } from "vue";
import App from "./App.vue";
createApp(App).mount("#app");
```

- [ ] **Step 5: Verify scaffolding boots**

Run: `bash scripts/dev-up.sh`  
Expected: Spring Boot starts, Vite starts, FastAPI responds on `/health`.

- [ ] **Step 6: Add a first commit**

```bash
git add apps/api apps/web services/agent scripts
git commit -m "chore: scaffold monorepo services"
```

## Task 2: Create the Oracle Schema

**Status:** Completed on 2026-04-09 after spec-compliance review and code-quality review. Initial verification was static-only, then upgraded to full runtime verification on 2026-04-09 by importing the schema into a local Oracle Free container and running `verify_schema.sql` successfully. A follow-up review pass renamed `AGENT_FEEDBACK.COMMENT` to `FEEDBACK_COMMENT` and added an index on `AGENT_ANALYSIS_RESULT (MANUSCRIPT_ID, VERSION_ID)`.

**Files:**
- Create: `database/oracle/001_init.sql`
- Create: `database/oracle/002_seed_roles.sql`
- Create: `database/oracle/003_indexes.sql`
- Create: `database/oracle/004_procedures.sql`
- Create: `database/oracle/005_triggers.sql`
- Test: `database/oracle/verify_schema.sql`

- [ ] **Step 1: Write the base table definitions**

Include tables from the spec:

```sql
CREATE TABLE SYS_USER (
  USER_ID NUMBER PRIMARY KEY,
  USERNAME VARCHAR2(100) NOT NULL UNIQUE,
  PASSWORD_HASH VARCHAR2(255) NOT NULL,
  REAL_NAME VARCHAR2(100) NOT NULL,
  EMAIL VARCHAR2(150) NOT NULL,
  INSTITUTION VARCHAR2(200),
  STATUS VARCHAR2(20) NOT NULL,
  CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE MANUSCRIPT (
  MANUSCRIPT_ID NUMBER PRIMARY KEY,
  SUBMITTER_ID NUMBER NOT NULL,
  CURRENT_VERSION_ID NUMBER,
  CURRENT_STATUS VARCHAR2(40) NOT NULL,
  CURRENT_ROUND_NO NUMBER DEFAULT 0 NOT NULL,
  BLIND_MODE VARCHAR2(30) NOT NULL,
  SUBMITTED_AT TIMESTAMP,
  LAST_DECISION_CODE VARCHAR2(40)
);
```

- [ ] **Step 2: Add the workflow tables**

Include:

- `MANUSCRIPT_VERSION`
- `MANUSCRIPT_AUTHOR`
- `REVIEW_ROUND`
- `REVIEW_ASSIGNMENT`
- `CONFLICT_CHECK_RECORD`
- `REVIEW_REPORT`
- `DECISION_RECORD`
- `AGENT_ANALYSIS_TASK`
- `AGENT_ANALYSIS_RESULT`
- `AGENT_FEEDBACK`
- `SYS_NOTIFICATION`
- `AUDIT_LOG`

- [ ] **Step 3: Add sequences**

```sql
CREATE SEQUENCE SEQ_SYS_USER START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SEQ_MANUSCRIPT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SEQ_MANUSCRIPT_VERSION START WITH 1 INCREMENT BY 1;
```

- [ ] **Step 4: Add role seed data**

```sql
INSERT INTO SYS_ROLE (ROLE_ID, ROLE_CODE, ROLE_NAME) VALUES (1, 'AUTHOR', 'Author');
INSERT INTO SYS_ROLE (ROLE_ID, ROLE_CODE, ROLE_NAME) VALUES (2, 'REVIEWER', 'Reviewer');
INSERT INTO SYS_ROLE (ROLE_ID, ROLE_CODE, ROLE_NAME) VALUES (3, 'CHAIR', 'Chair');
INSERT INTO SYS_ROLE (ROLE_ID, ROLE_CODE, ROLE_NAME) VALUES (4, 'ADMIN', 'Admin');
```

- [ ] **Step 5: Add trigger/procedure examples required by the course**

Use triggers for audit timestamp maintenance and procedures for reporting:

```sql
CREATE OR REPLACE PROCEDURE PRC_REVIEW_COMPLETION_STATS AS
BEGIN
  NULL;
END;
/
```

- [ ] **Step 6: Verify schema creation**

Run: `sqlplus < database/oracle/001_init.sql`  
Expected: tables and sequences created with no Oracle errors.

- [ ] **Step 7: Add a schema commit**

```bash
git add database/oracle
git commit -m "feat: add oracle schema for review workflow"
```

## Task 3: Implement Authentication and Role Control

**Status:** Completed on 2026-04-09. Implemented Spring Security + JWT + JdbcTemplate authentication against the real Oracle `SYS_USER` / `SYS_USER_ROLE` / `SYS_ROLE` tables, added separate demo-user seed data plus protected-route placeholders, and completed Oracle-backed integration verification. A test-environment fix was also required for Java 25 by forcing Mockito to use the subclass mock maker instead of inline attachment.

**Design Note:** Detailed Task 3 design is documented in `docs/superpowers/specs/2026-04-09-task3-auth-role-control-design.md`.

**Execution Summary:** Executed red-green auth integration development with `AuthControllerTest`, added Oracle datasource and JWT configuration, created the auth repository/service/filter/principal/security layers, seeded demo users in `006_seed_demo_users.sql`, updated `scripts/test-all.sh` to apply the demo seed before API verification, and wrote verification evidence to `docs/verification/2026-04-09-task3-auth-role-control.md`.

**Verification Run:** `bash scripts/oracle-demo-seed.sh`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AuthControllerTest test`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`; `bash scripts/test-all.sh`. All completed successfully when run with local Oracle access.

**Repository Constraint:** This workspace snapshot is not a git repository, so the plan's commit step could not be executed here.

**Files:**
- Create: `apps/api/src/main/java/com/example/review/auth/AuthController.java`
- Create: `apps/api/src/main/java/com/example/review/auth/AuthService.java`
- Create: `apps/api/src/main/java/com/example/review/auth/JwtService.java`
- Create: `apps/api/src/main/java/com/example/review/auth/JwtAuthenticationFilter.java`
- Create: `apps/api/src/main/java/com/example/review/auth/CurrentUserPrincipal.java`
- Create: `apps/api/src/main/java/com/example/review/auth/AuthUserRepository.java`
- Create: `apps/api/src/main/java/com/example/review/auth/AuthUserRecord.java`
- Create: `apps/api/src/main/java/com/example/review/config/SecurityConfig.java`
- Create: `apps/api/src/main/java/com/example/review/placeholder/ProtectedResourcePlaceholderController.java`
- Create: `apps/api/src/test/java/com/example/review/auth/AuthControllerTest.java`
- Create: `apps/api/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- Create: `database/oracle/006_seed_demo_users.sql`
- Create: `scripts/oracle-demo-seed.sh`
- Create: `docs/verification/2026-04-09-task3-auth-role-control.md`
- Modify: `apps/api/pom.xml`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `scripts/test-all.sh`

- [ ] **Step 1: Write the failing auth tests**

```java
@Test
void loginReturnsJwtWhenCredentialsAreValid() {}

@Test
void manuscriptEndpointRejectsAnonymousRequests() {}

@Test
void loginRejectsWrongPassword() {}

@Test
void loginRejectsDisabledUser() {}

@Test
void manuscriptEndpointAcceptsValidJwt() {}

@Test
void decisionEndpointRejectsNonChairRoles() {}

@Test
void decisionEndpointAcceptsChairRole() {}

@Test
void auditLogEndpointAcceptsOnlyAdminRole() {}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `mvn -f apps/api/pom.xml -Dtest=AuthControllerTest test`  
Expected: FAIL because auth classes do not exist yet.

- [ ] **Step 3: Implement Oracle-backed auth and JWT login**

```java
public record LoginRequest(String username, String password) {}
public record LoginResponse(String token) {}
```

- [ ] **Step 4: Add Spring Security route protection**

Allow anonymous:

- `/api/auth/login`
- `/api/health`

Require authentication for:

- `/api/**`

Restrict by role for:

- `/api/decisions/**` -> `CHAIR`, `ADMIN`
- `/api/audit-logs/**` -> `ADMIN`

- [ ] **Step 5: Add demo seed data and protected-route placeholders**

Add:

- `database/oracle/006_seed_demo_users.sql`
- `scripts/oracle-demo-seed.sh`
- placeholder controllers for protected route integration tests

- [ ] **Step 6: Re-run auth tests against Oracle**

Run:

- `bash scripts/oracle-demo-seed.sh`
- `mvn -f apps/api/pom.xml -Dtest=AuthControllerTest test`

Expected: PASS.

- [ ] **Step 7: Run API regression suite**

Run: `mvn -f apps/api/pom.xml test`
Expected: PASS.

- [ ] **Step 8: Record verification results**

Write:

- commands run
- Oracle assumptions
- demo accounts seeded
- observed test results

to `docs/verification/2026-04-09-task3-auth-role-control.md`.

- [ ] **Step 9: Update Task 3 execution status in this plan**

Record:

- what was executed
- what changed
- what verification ran
- current completion state

- [ ] **Step 10: Add commit**

```bash
git add apps/api/pom.xml apps/api/src/main/java/com/example/review/auth apps/api/src/main/java/com/example/review/config apps/api/src/main/resources/application.yml apps/api/src/test/java/com/example/review/auth database/oracle/006_seed_demo_users.sql scripts/oracle-demo-seed.sh docs/verification/2026-04-09-task3-auth-role-control.md docs/superpowers/specs/2026-04-09-task3-auth-role-control-design.md docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md
git commit -m "feat: add jwt authentication and role protection"
```

## Task 4: Build Manuscript, Version, and Author Management

**Status:** Completed in the working tree on 2026-04-09. Implemented a stable manuscript aggregate with explicit draft-version submission, version-scoped author snapshots, separate PDF upload/download flows, submitter-owned reads, and a "My Manuscripts" collection endpoint for the author home screen. A review-driven follow-up also aligned the Task 4 step list with the approved spec and wrote the reusable lessons back into `AGENTS.md`.

**Design Note:** Detailed Task 4 design is documented in `docs/superpowers/specs/2026-04-09-task4-manuscript-version-author-design.md`.

**Execution Summary:** Executed red-green manuscript workflow development with Oracle-backed `MockMvc` integration tests, added the `com.example.review.manuscript` controller/service/repository/DTO slice, replaced the placeholder `/api/manuscripts` GET mapping with real manuscript routes, implemented manuscript creation, revision creation, PDF upload/download, explicit submit transitions, submitter-only list/detail/version reads, and added a secondary seeded author inside the test fixture for ownership assertions.

**Verification Run:** `bash scripts/oracle-demo-seed.sh`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ManuscriptServiceTest test`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`; `bash scripts/test-all.sh`. All completed successfully with local Oracle access.

**Commit Note:** This repository snapshot supports a normal git commit flow for Task 4 once the user requests integration.

**Files:**
- Create: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptController.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptService.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptRepository.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/VersionRepository.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/AuthorRepository.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptDtos.java`
- Create: `apps/api/src/test/java/com/example/review/manuscript/ManuscriptServiceTest.java`
- [ ] **Step 1: Write the failing manuscript integration tests**

Add tests for:

```java
@Test
void createDraftManuscriptPersistsAggregate() {}

@Test
void submitDraftVersionMovesStatusToSubmitted() {}

@Test
void submitDraftWithoutPdfIsRejected() {}

@Test
void createRevisionRequiresRevisionRequiredStatus() {}

@Test
void submitRevisionMovesStatusToRevisedSubmitted() {}

@Test
void uploadPdfRejectsNonPdfFile() {}

@Test
void downloadPdfReturnsStoredFileForSubmitter() {}

@Test
void onlySubmitterCanAccessManuscript() {}

@Test
void listManuscriptsAndVersionsReturnAuthorOwnedData() {}
```

- [ ] **Step 2: Run manuscript tests to verify failure**

Run: `mvn -f apps/api/pom.xml -Dtest=ManuscriptServiceTest test`  
Expected: FAIL because the manuscript slice does not exist yet.

- [ ] **Step 3: Implement manuscript DTOs and controller endpoints**

Add endpoints for:

- `POST /api/manuscripts`
- `GET /api/manuscripts`
- `GET /api/manuscripts/{id}`
- `POST /api/manuscripts/{id}/versions`
- `GET /api/manuscripts/{id}/versions`
- `POST /api/manuscripts/{id}/versions/{versionId}/pdf`
- `GET /api/manuscripts/{id}/versions/{versionId}/pdf`
- `POST /api/manuscripts/{id}/versions/{versionId}/submit`

Use request/response DTOs that carry:

- manuscript summary fields for the "My Manuscripts" list
- manuscript detail fields
- version history fields
- create-manuscript and create-version payloads with author snapshots

- [ ] **Step 4: Implement repositories for manuscript, version, and author persistence**

The persistence layer must support:

- insert and lock reads for `MANUSCRIPT`
- insert, list, and BLOB updates for `MANUSCRIPT_VERSION`
- batch insert and ordered reads for `MANUSCRIPT_AUTHOR`
- author-owned manuscript list queries joined to the current version title

- [ ] **Step 5: Implement service validation, ownership checks, and transactions**

Implement:

- `AUTHOR` role and submitter ownership enforcement
- `blindMode` validation
- exactly one corresponding author validation
- `DRAFT -> SUBMITTED` submit transition
- `REVISION_REQUIRED -> REVISED_SUBMITTED` submit transition
- `SELECT ... FOR UPDATE` locking for revision creation and submission
- non-current-version and post-submit PDF overwrite guards

- [ ] **Step 6: Re-run manuscript tests**

Run: `mvn -f apps/api/pom.xml -Dtest=ManuscriptServiceTest test`  
Expected: PASS.

- [ ] **Step 7: Run API regression suite**

Run: `mvn -f apps/api/pom.xml test`  
Expected: PASS.

- [ ] **Step 8: Update Task 4 execution status in this plan**

Record:

- what was executed
- what changed
- what verification ran
- current completion state

- [ ] **Step 9: Add commit**

```bash
git add apps/api/src/main/java/com/example/review/manuscript apps/api/src/test/java/com/example/review/manuscript AGENTS.md docs/superpowers/specs/2026-04-09-task4-manuscript-version-author-design.md docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md
git commit -m "feat: add manuscript version and author management"
```

## Task 5: Implement Review Rounds, Assignments, and Conflict Checks

**Files:**
- Create: `apps/api/src/main/java/com/example/review/review/ReviewRoundController.java`
- Create: `apps/api/src/main/java/com/example/review/review/AssignmentController.java`
- Create: `apps/api/src/main/java/com/example/review/review/ReviewWorkflowService.java`
- Create: `apps/api/src/main/java/com/example/review/review/ConflictCheckService.java`
- Create: `apps/api/src/test/java/com/example/review/review/ReviewWorkflowServiceTest.java`

- [ ] **Step 1: Write failing assignment tests**

```java
@Test
void assignReviewerCreatesAssignedTask() {}

@Test
void selfDeclaredConflictMovesTaskToDeclined() {}

@Test
void overdueTaskCanBeReassigned() {}
```

- [ ] **Step 2: Implement round creation**

Create `REVIEW_ROUND` with:

- `ROUND_STATUS`
- `ASSIGNMENT_STRATEGY`
- `SCREENING_REQUIRED`
- `DEADLINE_AT`

- [ ] **Step 3: Implement assignment state transitions**

Support:

- `ASSIGNED -> ACCEPTED`
- `ASSIGNED -> DECLINED`
- `ASSIGNED -> OVERDUE`
- `OVERDUE -> REASSIGNED`

- [ ] **Step 4: Implement conflict check recording**

At minimum detect same institution:

```java
if (Objects.equals(authorInstitution, reviewerInstitution)) {
    // write CONFLICT_CHECK_RECORD with SYSTEM_DETECTED
}
```

- [ ] **Step 5: Add conflict check query endpoint**

Endpoint: `GET /api/review-rounds/{roundId}/conflict-checks`

- [ ] **Step 6: Re-run review workflow tests**

Run: `mvn -f apps/api/pom.xml -Dtest=ReviewWorkflowServiceTest test`  
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/api/src/main/java/com/example/review/review apps/api/src/test/java/com/example/review/review
git commit -m "feat: add review rounds assignments and conflict checks"
```

## Task 6: Implement Review Reports, Decisions, Notifications, and Audit

**Files:**
- Create: `apps/api/src/main/java/com/example/review/decision/DecisionController.java`
- Create: `apps/api/src/main/java/com/example/review/review/ReviewReportController.java`
- Create: `apps/api/src/main/java/com/example/review/notification/NotificationService.java`
- Create: `apps/api/src/main/java/com/example/review/audit/AuditLogService.java`
- Create: `apps/api/src/test/java/com/example/review/decision/DecisionServiceTest.java`

- [ ] **Step 1: Write failing decision tests**

```java
@Test
void chairDecisionUpdatesManuscriptAndRoundWithinSingleTransaction() {}

@Test
void deskRejectPersistsDecisionCode() {}
```

- [ ] **Step 2: Implement review submission**

Persist:

- scores `1-5`
- confidence level
- comments to author
- comments to chair

- [ ] **Step 3: Implement chair decision transaction**

Single transaction must:

- update manuscript state
- update round state
- close current assignments
- insert `DECISION_RECORD`

- [ ] **Step 4: Push notifications asynchronously**

Use a service boundary so failure does not roll back the decision.

- [ ] **Step 5: Re-run decision tests**

Run: `mvn -f apps/api/pom.xml -Dtest=DecisionServiceTest test`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/api/src/main/java/com/example/review/decision apps/api/src/main/java/com/example/review/notification apps/api/src/main/java/com/example/review/audit apps/api/src/test/java/com/example/review/decision
git commit -m "feat: add review submission decisions and notifications"
```

## Task 7: Scaffold the Agent Service API and Task Store

**Files:**
- Create: `services/agent/app/routes/tasks.py`
- Create: `services/agent/app/models.py`
- Create: `services/agent/app/task_store.py`
- Create: `services/agent/tests/test_tasks_api.py`

- [ ] **Step 1: Write failing agent task API tests**

```python
def test_create_task_returns_pending_status(): ...

def test_get_unknown_task_returns_404(): ...
```

- [ ] **Step 2: Implement the in-memory task store**

```python
from dataclasses import dataclass

@dataclass
class TaskRecord:
    task_id: str
    status: str
    step: str
    error: str | None = None
```

- [ ] **Step 3: Implement the API endpoints**

Expose:

- `POST /agent/tasks`
- `GET /agent/tasks/{task_id}`
- `GET /agent/tasks/{task_id}/result`

- [ ] **Step 4: Re-run agent API tests**

Run: `pytest services/agent/tests/test_tasks_api.py -q`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/agent/app services/agent/tests
git commit -m "feat: add agent task api and in-memory task store"
```

## Task 8: Implement LangGraph Workflows and Result Schemas

**Files:**
- Create: `services/agent/app/workflows/router.py`
- Create: `services/agent/app/workflows/schemas.py`
- Create: `services/agent/app/workflows/coordinator.py`
- Create: `services/agent/app/workflows/paper_understanding.py`
- Create: `services/agent/app/workflows/review_assist.py`
- Create: `services/agent/app/workflows/conflict_analysis.py`
- Create: `services/agent/app/redaction.py`
- Create: `services/agent/tests/test_workflow_schemas.py`

- [ ] **Step 1: Write failing schema tests**

```python
def test_review_assist_schema_requires_integer_scores(): ...

def test_screening_schema_has_scope_fit(): ...

def test_conflict_schema_has_consensus_and_conflicts(): ...
```

- [ ] **Step 2: Define the Paper Understanding intermediate representation**

```python
class PaperUnderstanding(TypedDict):
    manuscriptId: str
    versionId: str
    title: str
    abstractSummary: str
    claimedContributions: list[str]
    methodSummary: str
    experimentSummary: str
    possibleBlindnessRisks: list[str]
```

- [ ] **Step 3: Implement workflow selection by `TASK_TYPE`**

Support:

- `SCREENING_ANALYSIS`
- `REVIEW_ASSIST_ANALYSIS`
- `DECISION_CONFLICT_ANALYSIS`

- [ ] **Step 4: Implement redaction and validation**

The final result path must:

- validate required fields
- enforce integer score `1-5`
- generate redacted output for reviewer-facing result

- [ ] **Step 5: Re-run workflow tests**

Run: `pytest services/agent/tests/test_workflow_schemas.py -q`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add services/agent/app/workflows services/agent/app/redaction.py services/agent/tests
git commit -m "feat: add agent workflows schemas and redaction"
```

## Task 9: Integrate the Main System with Agent Service

**Files:**
- Create: `apps/api/src/main/java/com/example/review/agent/AgentTaskController.java`
- Create: `apps/api/src/main/java/com/example/review/agent/AgentIntegrationService.java`
- Create: `apps/api/src/main/java/com/example/review/agent/AgentPollingScheduler.java`
- Create: `apps/api/src/test/java/com/example/review/agent/AgentIntegrationServiceTest.java`

- [ ] **Step 1: Write failing integration tests**

```java
@Test
void reviewAssistTaskUploadsPdfAndStoresPendingTask() {}

@Test
void pollingCompletedTaskPersistsRawAndRedactedResults() {}
```

- [ ] **Step 2: Implement multipart upload from Oracle BLOB**

The integration service must:

- read `PDF_FILE` from Oracle
- send `metadata` JSON
- send PDF bytes as multipart file

- [ ] **Step 3: Implement polling and timeout failure**

If polling exceeds 10 minutes:

- mark task `FAILED`
- allow manual retry later

- [ ] **Step 4: Implement result query and feedback endpoints**

Expose:

- `GET /api/manuscripts/{id}/versions/{versionId}/agent-results`
- `POST /api/agent-results/{resultId}/feedback`
- `GET /api/manuscripts/{id}/agent-feedback`
- `POST /api/review-rounds/{roundId}/conflict-analysis`

- [ ] **Step 5: Re-run integration tests**

Run: `mvn -f apps/api/pom.xml -Dtest=AgentIntegrationServiceTest test`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/api/src/main/java/com/example/review/agent apps/api/src/test/java/com/example/review/agent
git commit -m "feat: integrate main system with agent service"
```

## Task 10: Build the Frontend Authentication and Shell

**Files:**
- Create: `apps/web/src/router/index.ts`
- Create: `apps/web/src/stores/auth.ts`
- Create: `apps/web/src/views/LoginView.vue`
- Create: `apps/web/src/layouts/AppShell.vue`
- Create: `apps/web/src/tests/login.spec.ts`

- [ ] **Step 1: Write failing frontend auth tests**

```ts
it("redirects anonymous users to login", () => {});
it("stores token after successful login", () => {});
```

- [ ] **Step 2: Implement login and route guards**

Use JWT-bearing requests from the store to API endpoints.

- [ ] **Step 3: Implement the role-based shell**

Provide navigation items for:

- author
- reviewer
- chair
- admin

- [ ] **Step 4: Re-run frontend auth tests**

Run: `npm --prefix apps/web run test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/web/src/router apps/web/src/stores apps/web/src/views apps/web/src/layouts apps/web/src/tests
git commit -m "feat: add frontend auth and application shell"
```

## Task 11: Build the Workflow Screens

**Files:**
- Create: `apps/web/src/views/author/ManuscriptListView.vue`
- Create: `apps/web/src/views/author/SubmitManuscriptView.vue`
- Create: `apps/web/src/views/reviewer/AssignmentListView.vue`
- Create: `apps/web/src/views/reviewer/ReviewEditorView.vue`
- Create: `apps/web/src/views/chair/ScreeningQueueView.vue`
- Create: `apps/web/src/views/chair/DecisionWorkbenchView.vue`
- Create: `apps/web/src/views/admin/AgentMonitorView.vue`
- Create: `apps/web/src/tests/workflow.spec.ts`

- [ ] **Step 1: Write failing workflow UI tests**

```ts
it("shows desk reject action in chair screening queue", () => {});
it("shows redacted agent results for reviewer", () => {});
it("shows raw agent results for chair", () => {});
```

- [ ] **Step 2: Implement author screens**

Support:

- create manuscript
- create version
- upload PDF
- submit revision

- [ ] **Step 3: Implement reviewer screens**

Support:

- accept or decline assignment
- declare conflict
- submit review
- view assignment history

- [ ] **Step 4: Implement chair screens**

Support:

- start screening
- assign reviewers
- view conflict checks
- trigger conflict analysis
- submit decisions
- reassign overdue tasks

- [ ] **Step 5: Re-run workflow UI tests**

Run: `npm --prefix apps/web run test`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/web/src/views apps/web/src/tests
git commit -m "feat: add manuscript review and chair workflow screens"
```

## Task 12: End-to-End Verification and Demo Hardening

**Files:**
- Create: `apps/web/tests/e2e/review-flow.spec.ts`
- Create: `scripts/demo-seed.sh`
- Modify: `scripts/test-all.sh`
- Modify: `docs/superpowers/specs/2026-04-09-paper-review-system-design.md`

- [ ] **Step 1: Create an end-to-end scenario test**

The scenario must cover:

- author submits manuscript
- chair screens manuscript
- chair assigns reviewer
- reviewer submits review
- chair triggers conflict analysis
- chair makes decision
- author sees result

- [ ] **Step 2: Add demo seed data**

Seed:

- one author
- two reviewers
- one chair
- one admin
- one manuscript with sample PDF

- [ ] **Step 3: Run the full verification script**

Run: `bash scripts/test-all.sh`  
Expected:

- backend tests pass
- agent tests pass
- frontend tests pass
- e2e happy path passes

- [ ] **Step 4: Update the design doc with implementation notes if needed**

Only record deviations if implementation differs from spec.

- [ ] **Step 5: Commit**

```bash
git add apps/web/tests/e2e scripts docs/superpowers/specs/2026-04-09-paper-review-system-design.md
git commit -m "test: add end-to-end verification and demo seed"
```

## Delivery Order

Implement in this order:

1. Task 1
2. Task 2
3. Task 3
4. Task 4
5. Task 5
6. Task 6
7. Task 7
8. Task 8
9. Task 9
10. Task 10
11. Task 11
12. Task 12

## Risks and Controls

- Oracle setup risk
  Control: complete Task 2 and schema verification before any workflow code
- State machine drift
  Control: backend tests in Tasks 4-6 must assert state transitions explicitly
- Agent workflow instability
  Control: schema validation and timeout handling in Tasks 8-9
- Double-blind leakage risk
  Control: redaction tests in Task 8 and reviewer/chair UI separation in Task 11
- Demo fragility
  Control: seed script and end-to-end test in Task 12

## Spec Coverage Check

- Architecture and stack: Tasks 1, 7, 8, 9, 10
- Authentication and roles: Task 3
- Manuscript/version/author model: Task 4
- Review rounds, assignments, conflicts, deadlines: Task 5
- Reviews, decisions, notifications, audit: Task 6
- Agent task types, schemas, redaction, feedback: Tasks 8 and 9
- API surface and frontend views: Tasks 9, 10, 11
- Deployment/demo readiness: Task 12

## Placeholder Scan

This plan intentionally defines:

- exact target paths
- explicit task order
- concrete commands for verification
- concrete workflow and schema examples

Avoid adding new endpoints or tables not already in the approved spec unless implementation exposes a concrete gap.
