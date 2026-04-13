# Paper Review System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved intelligent paper review system described in the design spec, including the Spring Boot main system, Oracle schema, FastAPI/LangGraph agent service, and Vue frontend.

**Architecture:** Use a small monorepo with `apps/web`, `apps/api`, `services/agent`, and `database/oracle`. Build the core workflow first: authentication, manuscript/version management, review state machines, review submission, chair decisions, then add async agent integration and UI polish.

**Tech Stack:** Vue 3, Element Plus, Vite, Java 21, Spring Boot, Spring Security, JWT, MyBatis or Spring Data JDBC, Oracle, Python 3.11+, FastAPI, LangGraph, pytest, Vitest. Playwright was considered for Task 12 but explicitly deferred to avoid adding browser orchestration risk late in the project.

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
- Task 4 completed on 2026-04-09
- Task 5 completed on 2026-04-09
- Task 6 completed on 2026-04-09
- Task 7 completed and committed on 2026-04-13 as part of commit `250b841`; it does not have a standalone Task 7 commit.
- Task 8 completed and committed on 2026-04-13 as commit `250b841` (`feat: add agent workflows schemas and redaction`).
- Task 9 completed and committed on 2026-04-13 as commit `44f9481` (`feat: integrate main system with agent service`), with post-review optimization commit `2209067` (`fix: align agent task reuse semantics`).
- Local environment bootstrap completed on 2026-04-09. Java, Maven, Node/npm, project `.venv`, frontend dependencies, Colima/Docker, and a local Oracle Free container are ready. Oracle schema import and verification passed inside the container.
- Current status check on 2026-04-13 found a clean git working tree before the Task 10 design update.
- Fresh status verification on 2026-04-13 ran `./.venv/bin/python -m pytest services/agent/tests -q`: 20 passed, 23 warnings. The warnings are from LangGraph/LangChain dependencies on Python 3.14 Pydantic v1 compatibility and deprecated `asyncio.iscoroutinefunction`; no test failures were reported.
- Task 9 implemented and verified on 2026-04-13. The main Spring Boot system can create agent tasks from Oracle PDF BLOBs, poll the FastAPI agent service, persist raw/redacted results, expose result lookup endpoints, and assemble conflict-analysis payloads from Oracle review reports. The FastAPI agent service now accepts both JSON and multipart/PDF task creation and enriches paper-understanding input with extracted PDF text and coarse sections.
- Task 1 through Task 9 audit on 2026-04-13 found no unchecked implementation steps before Task 10. Remaining items are explicit deferrals: screening-start entry point for later chair workflow work, agent feedback endpoints for a later feature slice, and durable agent queues/retries/provider failover for future hardening.
- Task 10 completed on 2026-04-13. The Vue frontend now has login, JWT session persistence, route guards, an API helper with bearer-token injection, a role-aware app shell, and an authenticated dashboard landing page.
- Task 11 completed on 2026-04-13. The frontend now has role-specific workflow pages for authors, reviewers, chairs, and admins; the API now exposes the minimal workflow query/action endpoints needed by those pages, including reviewer/chair PDF access and admin agent task listing.
- Task 11 post-review fix completed on 2026-04-13. Chair workflow routes now allow both `CHAIR` and `ADMIN`, matching the backend `chair-or-admin` authorization model.
- Task 12 design drafted on 2026-04-13 in `docs/superpowers/specs/2026-04-13-task12-e2e-demo-and-visual-hardening-design.md`. The approved direction uses Oracle-backed API e2e verification instead of Playwright, idempotent demo seed data, and scoped frontend polish using Editorial Dossier surfaces plus Agent Trace panels.

## Task 1: Scaffold the Monorepo

**Status:** Completed on 2026-04-09 after spec-compliance review and code-quality review. Full local verification was completed later on 2026-04-09 after environment bootstrap, including Maven tests, pytest, Vitest, Vue TypeScript type-checking, and Vite production build. A follow-up review pass also added a root `.gitignore`, switched the frontend entry to TypeScript, and declared the selected frontend stack dependencies (`Element Plus`, `vue-router`).

**Commit Note:** The current git history is too sparse to reconstruct an isolated Task 1 commit with confidence, but the scaffolded repository state and later verification evidence confirm Task 1 is complete.

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

- [x] **Step 1: Create the backend skeleton**

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

- [x] **Step 2: Add a minimal backend health endpoint**

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

- [x] **Step 3: Add a minimal FastAPI health endpoint**

```python
from fastapi import FastAPI

app = FastAPI()

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
```

- [x] **Step 4: Add a minimal Vue application entry**

```ts
import { createApp } from "vue";
import App from "./App.vue";
createApp(App).mount("#app");
```

- [x] **Step 5: Verify scaffolding boots**

Run: `bash scripts/dev-up.sh`  
Expected: Spring Boot starts, Vite starts, FastAPI responds on `/health`.

- [x] **Step 6: Add a first commit**

```bash
git add apps/api apps/web services/agent scripts
git commit -m "chore: scaffold monorepo services"
```

## Task 2: Create the Oracle Schema

**Status:** Completed on 2026-04-09 after spec-compliance review and code-quality review. Initial verification was static-only, then upgraded to full runtime verification on 2026-04-09 by importing the schema into a local Oracle Free container and running `verify_schema.sql` successfully. A follow-up review pass renamed `AGENT_FEEDBACK.COMMENT` to `FEEDBACK_COMMENT` and added an index on `AGENT_ANALYSIS_RESULT (MANUSCRIPT_ID, VERSION_ID)`.

**Commit Note:** The current git history does not expose a dedicated Task 2 commit, but the Oracle schema files and successful runtime verification show the schema task is complete.

**Files:**
- Create: `database/oracle/001_init.sql`
- Create: `database/oracle/002_seed_roles.sql`
- Create: `database/oracle/003_indexes.sql`
- Create: `database/oracle/004_procedures.sql`
- Create: `database/oracle/005_triggers.sql`
- Test: `database/oracle/verify_schema.sql`

- [x] **Step 1: Write the base table definitions**

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

- [x] **Step 2: Add the workflow tables**

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

- [x] **Step 3: Add sequences**

```sql
CREATE SEQUENCE SEQ_SYS_USER START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SEQ_MANUSCRIPT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SEQ_MANUSCRIPT_VERSION START WITH 1 INCREMENT BY 1;
```

- [x] **Step 4: Add role seed data**

```sql
INSERT INTO SYS_ROLE (ROLE_ID, ROLE_CODE, ROLE_NAME) VALUES (1, 'AUTHOR', 'Author');
INSERT INTO SYS_ROLE (ROLE_ID, ROLE_CODE, ROLE_NAME) VALUES (2, 'REVIEWER', 'Reviewer');
INSERT INTO SYS_ROLE (ROLE_ID, ROLE_CODE, ROLE_NAME) VALUES (3, 'CHAIR', 'Chair');
INSERT INTO SYS_ROLE (ROLE_ID, ROLE_CODE, ROLE_NAME) VALUES (4, 'ADMIN', 'Admin');
```

- [x] **Step 5: Add trigger/procedure examples required by the course**

Use triggers for audit timestamp maintenance and procedures for reporting:

```sql
CREATE OR REPLACE PROCEDURE PRC_REVIEW_COMPLETION_STATS AS
BEGIN
  NULL;
END;
/
```

- [x] **Step 6: Verify schema creation**

Run: `sqlplus < database/oracle/001_init.sql`  
Expected: tables and sequences created with no Oracle errors.

- [x] **Step 7: Add a schema commit**

```bash
git add database/oracle
git commit -m "feat: add oracle schema for review workflow"
```

## Task 3: Implement Authentication and Role Control

**Status:** Completed on 2026-04-09. Implemented Spring Security + JWT + JdbcTemplate authentication against the real Oracle `SYS_USER` / `SYS_USER_ROLE` / `SYS_ROLE` tables, added separate demo-user seed data plus protected-route placeholders, and completed Oracle-backed integration verification. A test-environment fix was also required for Java 25 by forcing Mockito to use the subclass mock maker instead of inline attachment.

**Design Note:** Detailed Task 3 design is documented in `docs/superpowers/specs/2026-04-09-task3-auth-role-control-design.md`.

**Execution Summary:** Executed red-green auth integration development with `AuthControllerTest`, added Oracle datasource and JWT configuration, created the auth repository/service/filter/principal/security layers, seeded demo users in `006_seed_demo_users.sql`, updated `scripts/test-all.sh` to apply the demo seed before API verification, and wrote verification evidence to `docs/verification/2026-04-09-task3-auth-role-control.md`.

**Verification Run:** `bash scripts/oracle-demo-seed.sh`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AuthControllerTest test`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`; `bash scripts/test-all.sh`. All completed successfully when run with local Oracle access.

**Commit Note:** The current git history does not preserve a dedicated Task 3 feature commit, but the Task 3 code, review follow-up commit `04eed24`, and fresh verification evidence confirm the authentication task is complete.

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

- [x] **Step 1: Write the failing auth tests**

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

- [x] **Step 2: Run tests to verify failure**

Run: `mvn -f apps/api/pom.xml -Dtest=AuthControllerTest test`  
Expected: FAIL because auth classes do not exist yet.

- [x] **Step 3: Implement Oracle-backed auth and JWT login**

```java
public record LoginRequest(String username, String password) {}
public record LoginResponse(String token) {}
```

- [x] **Step 4: Add Spring Security route protection**

Allow anonymous:

- `/api/auth/login`
- `/api/health`

Require authentication for:

- `/api/**`

Restrict by role for:

- `/api/decisions/**` -> `CHAIR`, `ADMIN`
- `/api/audit-logs/**` -> `ADMIN`

- [x] **Step 5: Add demo seed data and protected-route placeholders**

Add:

- `database/oracle/006_seed_demo_users.sql`
- `scripts/oracle-demo-seed.sh`
- placeholder controllers for protected route integration tests

- [x] **Step 6: Re-run auth tests against Oracle**

Run:

- `bash scripts/oracle-demo-seed.sh`
- `mvn -f apps/api/pom.xml -Dtest=AuthControllerTest test`

Expected: PASS.

- [x] **Step 7: Run API regression suite**

Run: `mvn -f apps/api/pom.xml test`
Expected: PASS.

- [x] **Step 8: Record verification results**

Write:

- commands run
- Oracle assumptions
- demo accounts seeded
- observed test results

to `docs/verification/2026-04-09-task3-auth-role-control.md`.

- [x] **Step 9: Update Task 3 execution status in this plan**

Record:

- what was executed
- what changed
- what verification ran
- current completion state

- [x] **Step 10: Add commit**

```bash
git add apps/api/pom.xml apps/api/src/main/java/com/example/review/auth apps/api/src/main/java/com/example/review/config apps/api/src/main/resources/application.yml apps/api/src/test/java/com/example/review/auth database/oracle/006_seed_demo_users.sql scripts/oracle-demo-seed.sh docs/verification/2026-04-09-task3-auth-role-control.md docs/superpowers/specs/2026-04-09-task3-auth-role-control-design.md docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md
git commit -m "feat: add jwt authentication and role protection"
```

## Task 4: Build Manuscript, Version, and Author Management

**Status:** Completed in the working tree on 2026-04-09. Implemented a stable manuscript aggregate with explicit draft-version submission, version-scoped author snapshots, separate PDF upload/download flows, submitter-owned reads, and a "My Manuscripts" collection endpoint for the author home screen. A review-driven follow-up also aligned the Task 4 step list with the approved spec and wrote the reusable lessons back into `AGENTS.md`.

**Design Note:** Detailed Task 4 design is documented in `docs/superpowers/specs/2026-04-09-task4-manuscript-version-author-design.md`.

**Execution Summary:** Executed red-green manuscript workflow development with Oracle-backed `MockMvc` integration tests, added the `com.example.review.manuscript` controller/service/repository/DTO slice, replaced the placeholder `/api/manuscripts` GET mapping with real manuscript routes, implemented manuscript creation, revision creation, PDF upload/download, explicit submit transitions, submitter-only list/detail/version reads, and added a secondary seeded author inside the test fixture for ownership assertions.

**Verification Run:** `bash scripts/oracle-demo-seed.sh`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ManuscriptServiceTest test`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`; `bash scripts/test-all.sh`. All completed successfully with local Oracle access.

**Commit Note:** Task 4 was committed on 2026-04-09 as `40c12dd` with message `feat(api): add manuscript version and author management`.

**Files:**
- Create: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptController.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptService.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptRepository.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/VersionRepository.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/AuthorRepository.java`
- Create: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptDtos.java`
- Create: `apps/api/src/test/java/com/example/review/manuscript/ManuscriptServiceTest.java`
- [x] **Step 1: Write the failing manuscript integration tests**

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

- [x] **Step 2: Run manuscript tests to verify failure**

Run: `mvn -f apps/api/pom.xml -Dtest=ManuscriptServiceTest test`  
Expected: FAIL because the manuscript slice does not exist yet.

- [x] **Step 3: Implement manuscript DTOs and controller endpoints**

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

- [x] **Step 4: Implement repositories for manuscript, version, and author persistence**

The persistence layer must support:

- insert and lock reads for `MANUSCRIPT`
- insert, list, and BLOB updates for `MANUSCRIPT_VERSION`
- batch insert and ordered reads for `MANUSCRIPT_AUTHOR`
- author-owned manuscript list queries joined to the current version title

- [x] **Step 5: Implement service validation, ownership checks, and transactions**

Implement:

- `AUTHOR` role and submitter ownership enforcement
- `blindMode` validation
- exactly one corresponding author validation
- `DRAFT -> SUBMITTED` submit transition
- `REVISION_REQUIRED -> REVISED_SUBMITTED` submit transition
- `SELECT ... FOR UPDATE` locking for revision creation and submission
- non-current-version and post-submit PDF overwrite guards

- [x] **Step 6: Re-run manuscript tests**

Run: `mvn -f apps/api/pom.xml -Dtest=ManuscriptServiceTest test`  
Expected: PASS.

- [x] **Step 7: Run API regression suite**

Run: `mvn -f apps/api/pom.xml test`  
Expected: PASS.

- [x] **Step 8: Update Task 4 execution status in this plan**

Record:

- what was executed
- what changed
- what verification ran
- current completion state

- [x] **Step 9: Add commit**

```bash
git add apps/api/src/main/java/com/example/review/manuscript apps/api/src/test/java/com/example/review/manuscript AGENTS.md docs/superpowers/specs/2026-04-09-task4-manuscript-version-author-design.md docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md
git commit -m "feat: add manuscript version and author management"
```

## Task 5: Implement Review Rounds, Assignments, and Conflict Checks

**Status:** Completed in the working tree on 2026-04-09. Implemented chair-driven round creation, reviewer assignment lifecycle management, reviewer accept/decline actions, overdue reassignment, and round-scoped conflict check recording/querying on top of the Task 4 manuscript/version data.

**Execution Summary:** Executed a short design pass against the existing Oracle schema, wrote Oracle-backed `MockMvc` integration tests for round creation and assignment lifecycle transitions, added the `com.example.review.review` controller/service/repository/DTO slice, enforced manuscript preconditions (`SUBMITTED` or `REVISED_SUBMITTED`) before round creation, promoted manuscripts to `UNDER_REVIEW` when a round is opened, implemented `ASSIGNED -> ACCEPTED`, `ASSIGNED/ACCEPTED -> DECLINED`, `ASSIGNED/ACCEPTED -> OVERDUE`, and `OVERDUE -> REASSIGNED`, recorded same-institution system conflicts plus optional self-declared conflicts, and finished with a follow-up test-isolation fix so full API regression clears review tables before manuscript rows. Task 5 intentionally skips the intermediate `UNDER_SCREENING` state; the screening entry and desk-reject path remain deferred to Task 6 and the later screening workflow.

**Verification Run:** `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ReviewWorkflowServiceTest test`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ManuscriptServiceTest test`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`; `bash scripts/test-all.sh`. All completed successfully with local Oracle access.

**Commit Note:** Task 5 was committed on 2026-04-09 as `776244d` with message `feat(api): add review rounds and conflict checks`.

**Files:**
- Create: `apps/api/src/main/java/com/example/review/review/ReviewRoundController.java`
- Create: `apps/api/src/main/java/com/example/review/review/AssignmentController.java`
- Create: `apps/api/src/main/java/com/example/review/review/ReviewDtos.java`
- Create: `apps/api/src/main/java/com/example/review/review/ReviewWorkflowService.java`
- Create: `apps/api/src/main/java/com/example/review/review/ReviewRoundRepository.java`
- Create: `apps/api/src/main/java/com/example/review/review/ReviewAssignmentRepository.java`
- Create: `apps/api/src/main/java/com/example/review/review/ConflictCheckService.java`
- Create: `apps/api/src/main/java/com/example/review/review/ConflictCheckRepository.java`
- Create: `apps/api/src/test/java/com/example/review/review/ReviewWorkflowServiceTest.java`
- Modify: `apps/api/src/test/java/com/example/review/manuscript/ManuscriptServiceTest.java`

- [x] **Step 1: Write failing review workflow integration tests**

```java
@Test
void assignReviewerCreatesAssignedTask() {}

@Test
void reviewerAcceptMovesAssignmentToAccepted() {}

@Test
void selfDeclaredConflictMovesTaskToDeclined() {}

@Test
void overdueTaskCanBeReassigned() {}

@Test
void systemConflictCheckIsReturnedForRound() {}

@Test
void roundCreationRejectsUnsupportedManuscriptState() {}
```

- [x] **Step 2: Implement round creation and chair-facing endpoints**

Add endpoints for:

- `POST /api/review-rounds`
- `POST /api/review-rounds/{roundId}/assignments`
- `GET /api/review-rounds/{roundId}/conflict-checks`

Round creation must:

- require `CHAIR` or `ADMIN`
- allow only `SUBMITTED` or `REVISED_SUBMITTED` manuscripts
- create `REVIEW_ROUND` with `ROUND_STATUS = PENDING`
- move the manuscript to `UNDER_REVIEW`

- [x] **Step 3: Implement assignment repositories, reviewer actions, and state transitions**

Support:

- `ASSIGNED -> ACCEPTED`
- `ASSIGNED/ACCEPTED -> DECLINED`
- `ASSIGNED/ACCEPTED -> OVERDUE`
- `OVERDUE -> REASSIGNED`

Add reviewer/chair endpoints for:

- `POST /api/review-assignments/{assignmentId}/accept`
- `POST /api/review-assignments/{assignmentId}/decline`
- `POST /api/review-assignments/{assignmentId}/mark-overdue`
- `POST /api/review-assignments/{assignmentId}/reassign`

- [x] **Step 4: Implement conflict check detection and recording**

At minimum detect same institution:

```java
if (Objects.equals(authorInstitution, reviewerInstitution)) {
    // write CONFLICT_CHECK_RECORD with SYSTEM_DETECTED
}
```

- [x] **Step 5: Enforce reviewer/chair permissions and optional self-declared conflict writes**

Implement:

- `CHAIR` / `ADMIN` access for round creation, assignment, overdue marking, reassignment, and conflict queries
- reviewer-owner enforcement for `accept` and `decline`
- optional `conflictDeclared` handling on decline

- [x] **Step 6: Re-run focused Task 5 tests**

Run: `mvn -f apps/api/pom.xml -Dtest=ReviewWorkflowServiceTest test`  
Expected: PASS.

- [x] **Step 7: Re-run manuscript and full API regression, then fix any integration-level cleanup regressions**

Run:

- `mvn -f apps/api/pom.xml -Dtest=ManuscriptServiceTest test`
- `mvn -f apps/api/pom.xml test`

Expected: PASS. If full regression exposes cross-test table cleanup failures, update dependent-table cleanup order before closing the task.

- [x] **Step 8: Run repository-level verification**

Run: `bash scripts/test-all.sh`  
Expected: PASS.

- [x] **Step 9: Commit**

```bash
git add apps/api/src/main/java/com/example/review/review apps/api/src/test/java/com/example/review/review apps/api/src/test/java/com/example/review/manuscript/ManuscriptServiceTest.java docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md
git commit -m "feat: add review rounds assignments and conflict checks"
```

## Task 6: Implement Review Reports, Decisions, Notifications, and Audit

**Status:** Completed in the working tree on 2026-04-09. Implemented reviewer review-report submission, chair decision transactions, decision notifications, and audit-log writes on top of the Task 5 round/assignment workflow.

**Execution Summary:** Wrote Oracle-backed `MockMvc` integration tests for reviewer report submission, chair round decision, and desk rejection. Added the `com.example.review.decision` slice plus review-report persistence and submission endpoints, inserted `REVIEW_REPORT` rows with assignment state transitions to `SUBMITTED`, implemented chair decision transactions that complete the round, persist `DECISION_RECORD`, update manuscript status and `LAST_DECISION_CODE`, cancel still-open assignments, and write notification and audit rows through dedicated service boundaries. Notification writes are handled as best-effort so notification persistence failures do not roll back the primary review or decision transaction. A follow-up regression pass also updated older integration tests to delete `REVIEW_REPORT` before `REVIEW_ASSIGNMENT` so the expanded workflow dependency graph remains isolated across test classes. Desk reject is implemented for manuscripts already in `UNDER_SCREENING`; an explicit screening-start entry point remains deferred.

**Verification Run:** `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=DecisionServiceTest test`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`; `bash scripts/test-all.sh`. All completed successfully with local Oracle access.

**Commit Note:** Task 6 is verified and committed in this work cycle with message `feat(api): add review submission decisions and notifications`.

**Files:**
- Create: `apps/api/src/main/java/com/example/review/decision/DecisionController.java`
- Create: `apps/api/src/main/java/com/example/review/decision/DecisionService.java`
- Create: `apps/api/src/main/java/com/example/review/decision/DecisionRepository.java`
- Create: `apps/api/src/main/java/com/example/review/review/ReviewReportController.java`
- Create: `apps/api/src/main/java/com/example/review/review/ReviewReportService.java`
- Create: `apps/api/src/main/java/com/example/review/review/ReviewReportRepository.java`
- Create: `apps/api/src/main/java/com/example/review/notification/NotificationService.java`
- Create: `apps/api/src/main/java/com/example/review/audit/AuditLogService.java`
- Create: `apps/api/src/test/java/com/example/review/decision/DecisionServiceTest.java`
- Modify: `apps/api/src/main/java/com/example/review/review/ReviewAssignmentRepository.java`
- Modify: `apps/api/src/main/java/com/example/review/placeholder/ProtectedResourcePlaceholderController.java`
- Modify: `apps/api/src/test/java/com/example/review/manuscript/ManuscriptServiceTest.java`
- Modify: `apps/api/src/test/java/com/example/review/review/ReviewWorkflowServiceTest.java`

- [x] **Step 1: Write failing decision tests**

```java
@Test
void reviewerSubmitReviewReportPersistsScoresAndMarksAssignmentSubmitted() {}

@Test
void chairDecisionUpdatesManuscriptAndRoundWithinSingleTransaction() {}

@Test
void deskRejectPersistsDecisionCode() {}
```

- [x] **Step 2: Implement review submission**

Persist:

- scores `1-5`
- confidence level
- comments to author
- comments to chair

- [x] **Step 3: Implement chair decision transaction**

Single transaction must:

- update manuscript state
- update round state
- close current assignments
- insert `DECISION_RECORD`

- [x] **Step 4: Push notifications through a separate service boundary**

Use a service boundary so failure does not roll back the decision.

- [x] **Step 5: Re-run decision tests and full API regression**

Run:

- `mvn -f apps/api/pom.xml -Dtest=DecisionServiceTest test`
- `mvn -f apps/api/pom.xml test`

Expected: PASS.

- [x] **Step 6: Run repository-level verification**

Run: `bash scripts/test-all.sh`  
Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add apps/api/src/main/java/com/example/review/decision apps/api/src/main/java/com/example/review/review apps/api/src/main/java/com/example/review/notification apps/api/src/main/java/com/example/review/audit apps/api/src/test/java/com/example/review/decision apps/api/src/test/java/com/example/review/manuscript/ManuscriptServiceTest.java apps/api/src/test/java/com/example/review/review/ReviewWorkflowServiceTest.java docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md
git commit -m "feat: add review submission decisions and notifications"
```

## Task 7: Scaffold the Agent Service API and Task Store

**Status:** Completed and committed on 2026-04-13 as part of commit `250b841`. Implemented the FastAPI task API skeleton and an in-memory task store that is ready for Task 8 workflow execution to drive status transitions.

**Execution Summary:** Wrote failing FastAPI tests for task creation, task lookup, unknown-task handling, and result-not-ready behavior. Added `app.models`, `app.task_store`, and `app.routes.tasks`, mounted the new router in `app.main`, and implemented a lock-protected in-memory `TaskStore` keyed by UUID task IDs. Task creation now persists `task_type`, manuscript/version/round identifiers, and `request_payload` so Task 8 can consume the original analysis input without changing the store contract. Task 7 intentionally keeps the API JSON-only; the future main-system integration that sends PDF files remains deferred to the later multipart upgrade task.

**Verification Run:** `./.venv/bin/python -m pytest services/agent/tests/test_tasks_api.py -q`; `./.venv/bin/python -m pytest services/agent/tests -q`. Both completed successfully.

**Commit Note:** Task 7 did not receive a standalone commit; its task API and task-store files were committed together with Task 8 in `250b841` (`feat: add agent workflows schemas and redaction`).

**Files:**
- Create: `services/agent/app/routes/tasks.py`
- Create: `services/agent/app/routes/__init__.py`
- Create: `services/agent/app/models.py`
- Create: `services/agent/app/task_store.py`
- Create: `services/agent/tests/test_tasks_api.py`
- Modify: `services/agent/app/main.py`

- [x] **Step 1: Write failing agent task API tests**

```python
def test_create_task_returns_pending_status(): ...

def test_get_unknown_task_returns_404(): ...
```

- [x] **Step 2: Implement the in-memory task store**

```python
from dataclasses import dataclass

@dataclass
class TaskRecord:
    task_id: str
    task_type: str
    status: str
    step: str
    error: str | None = None
    request_payload: dict | None = None
```

- [x] **Step 3: Implement the API endpoints**

Expose:

- `POST /agent/tasks`
- `GET /agent/tasks/{task_id}`
- `GET /agent/tasks/{task_id}/result`

- [x] **Step 4: Re-run agent API tests**

Run: `pytest services/agent/tests/test_tasks_api.py -q`  
Expected: PASS.

- [x] **Step 5: Re-run the full agent test directory**

Run: `pytest services/agent/tests -q`  
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add services/agent/app services/agent/tests
git commit -m "feat: add agent task api and in-memory task store"
```

## Task 8: Implement LangGraph Workflows and Result Schemas

**Status:** Completed and committed on 2026-04-13 as commit `250b841` (`feat: add agent workflows schemas and redaction`). Implemented the minimal safe live-agent slice approved in the revised Task 8 spec.

**Design Note:** Task 8 design is drafted in `docs/superpowers/specs/2026-04-09-task8-agent-workflows-design.md` and targets the smallest safe live-agent slice: real LangGraph execution through OpenRouter, internal API-key protection, input-fingerprint cache reuse with `force` override, bounded in-process concurrency, authoritative schema validation, and content-aware redacted result generation. Multipart PDF upload, Oracle persistence, durable queues, retries, and Java polling integration remain deferred.

**Execution Summary:** Wrote red tests for authoritative result schemas, router selection, conflict `roundId` validation, content redaction, internal API-key enforcement, unset-key `503`, request-payload fingerprinting, cache reuse, `force=true`, single-start behavior for cached pending tasks, and stable `PENDING/queued` create responses. Added `langgraph`, `openai`, and `python-dotenv`; implemented TaskRecord cache metadata, deterministic input fingerprints, in-memory cache reuse, internal API-key checks, bounded in-process background execution, real LangGraph workflows, OpenRouter JSON-call hooks, Pydantic validation, and deterministic reviewer-facing redaction.

**Verification Run:** `./.venv/bin/python -m pytest services/agent/tests/test_workflow_schemas.py -q`; `./.venv/bin/python -m pytest services/agent/tests/test_tasks_api.py -q`; `./.venv/bin/python -m pytest services/agent/tests -q`. All completed successfully. Fresh status verification on 2026-04-13 ran `./.venv/bin/python -m pytest services/agent/tests -q` and reported 20 passed, 23 warnings. The warnings are from LangGraph/LangChain dependencies on Python 3.14 Pydantic v1 compatibility and deprecated `asyncio.iscoroutinefunction`, but no test failures.

**Files:**
- Modify: `services/agent/app/main.py`
- Modify: `services/agent/app/routes/tasks.py`
- Modify: `services/agent/app/models.py`
- Modify: `services/agent/app/task_store.py`
- Modify: `services/agent/pyproject.toml`
- Create: `services/agent/app/workflows/router.py`
- Create: `services/agent/app/workflows/schemas.py`
- Create: `services/agent/app/workflows/coordinator.py`
- Create: `services/agent/app/workflows/llm.py`
- Create: `services/agent/app/workflows/paper_understanding.py`
- Create: `services/agent/app/workflows/review_assist.py`
- Create: `services/agent/app/workflows/conflict_analysis.py`
- Create: `services/agent/app/redaction.py`
- Create: `services/agent/tests/test_workflow_schemas.py`
- Modify: `services/agent/tests/test_tasks_api.py`

- [x] **Step 1: Write failing schema tests**

```python
def test_review_assist_schema_requires_integer_scores(): ...

def test_screening_schema_has_scope_fit(): ...

def test_conflict_schema_has_consensus_and_conflicts(): ...

def test_decision_conflict_requires_round_id(): ...

def test_redaction_sanitizes_identity_clues(): ...

def test_changed_request_payload_changes_cache_key(): ...

def test_tasks_require_internal_api_key(): ...

def test_missing_internal_api_key_config_returns_503(): ...

def test_duplicate_task_reuses_existing_cache_entry(): ...

def test_force_creates_fresh_task(): ...
```

- [x] **Step 2: Define the Paper Understanding intermediate representation**

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

- [x] **Step 3: Implement workflow selection by `TASK_TYPE`**

Support:

- `SCREENING_ANALYSIS`
- `REVIEW_ASSIST_ANALYSIS`
- `DECISION_CONFLICT_ANALYSIS`

- [x] **Step 4: Add service authentication, cache reuse, bounded concurrency, and background execution hooks**

Implement:

- `X-Agent-Api-Key` protection for `/agent/tasks*`
- `503` for `/agent/tasks*` when auth is required but `AGENT_INTERNAL_API_KEY` is unset
- cache-aware task reuse keyed by task type + manuscript/version/round + workflow revision + request payload fingerprint
- `force=true` to bypass cache reuse and create a fresh task
- bounded in-process live execution using `AGENT_MAX_CONCURRENT_TASKS`
- immediate background workflow execution after authenticated task creation
- stable `step` enum updates

- [x] **Step 5: Implement redaction and validation**

The final result path must:

- validate required fields against the authoritative schema names
- enforce integer score `1-5` in nested review-assist score objects
- require `roundId` for `DECISION_CONFLICT_ANALYSIS`
- generate content-sanitized redacted output for reviewer-facing result

- [x] **Step 6: Re-run workflow and agent API tests**

Run:

- `pytest services/agent/tests/test_workflow_schemas.py -q`
- `pytest services/agent/tests/test_tasks_api.py -q`
- `pytest services/agent/tests -q`

Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add services/agent/app services/agent/tests services/agent/pyproject.toml AGENTS.md docs/superpowers/specs/2026-04-09-task8-agent-workflows-design.md docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md
git commit -m "feat: add agent workflows schemas and redaction"
```

## Task 9: Integrate the Main System with Agent Service

**Status:** Completed on 2026-04-13 after agent-service and API integration verification.

**Design Note:** Detailed Task 9 design is documented in `docs/superpowers/specs/2026-04-13-task9-agent-integration-design.md`.

**Start Note (2026-04-13):** Begin with failing integration tests and use the multipart/PDF contract from the overall design. Task 8 intentionally kept `/agent/tasks` JSON-only, so Task 9 must extend the FastAPI agent service to accept `multipart/form-data` before the Java API uploads Oracle BLOB bytes. Do not implement Java-side multipart upload against the current agent API without adding the matching FastAPI endpoint and tests in the same task.

**Task 9 Scope Decision (2026-04-13):** Implement only the required Tool-layer subset as deterministic Python helpers called directly by workflow-adjacent code. Keep `PdfExtractTool` and `SectionSplitTool`. Do not add a general autonomous tool-calling framework. Do not implement `MetadataFetchTool`; route-level multipart parsing must normalize metadata before creating the task. Do not implement `ConflictCompareTool`; Java must aggregate Oracle review reports into `requestPayload.reviewReports` before calling the agent service. Feedback endpoints are deferred out of Task 9.

**Execution Summary:** Added FastAPI multipart parsing for `/agent/tasks`, deterministic PDF extraction and section splitting helpers, richer `PaperUnderstanding` fields, and JSON compatibility coverage. Added the Spring Boot agent integration slice with controller/service/repository, multipart HTTP client, polling scheduler, Oracle result persistence, role-scoped result visibility, conflict-analysis review-report aggregation, timeout handling, and idempotent `EXTERNAL_TASK_ID` attachment. Updated legacy Oracle integration-test cleanup to delete agent child rows before manuscript/version parent rows.

**Verification Run:** `./.venv/bin/python -m pytest services/agent/tests/test_multipart_tasks_api.py -q`; `./.venv/bin/python -m pytest services/agent/tests -q`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest test`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`; `bash scripts/test-all.sh`. All completed successfully with local Oracle access.

**Post-Review Optimization (2026-04-13):** Aligned Java task reuse with agent-service cache semantics by reusing existing `FAILED` tasks when `force=false`; manual rerun after failure remains `force=true`. Added a regression test for this behavior, removed a no-op multipart test assertion, and refactored pollable/reusable task queries to map full rows directly instead of issuing N+1 task lookups.

**Post-Review Verification:** `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest#failedTaskIsReusedWhenForceIsFalse test`; `./.venv/bin/python -m pytest services/agent/tests/test_multipart_tasks_api.py -q`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest test`; `./.venv/bin/python -m pytest services/agent/tests -q`; `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`; `bash scripts/test-all.sh`. All completed successfully with local Oracle access.

**Files:**
- Create: `apps/api/src/main/java/com/example/review/agent/AgentTaskController.java`
- Create: `apps/api/src/main/java/com/example/review/agent/AgentIntegrationService.java`
- Create: `apps/api/src/main/java/com/example/review/agent/AgentPollingScheduler.java`
- Create: `apps/api/src/main/java/com/example/review/agent/AgentRepository.java`
- Create: `apps/api/src/test/java/com/example/review/agent/AgentIntegrationServiceTest.java`
- Create: `services/agent/app/pdf_tools.py`
- Create: `services/agent/tests/test_multipart_tasks_api.py`
- Modify: `apps/api/src/main/resources/application.yml`
- Modify: `apps/api/src/test/java/com/example/review/decision/DecisionServiceTest.java`
- Modify: `apps/api/src/test/java/com/example/review/manuscript/ManuscriptServiceTest.java`
- Modify: `apps/api/src/test/java/com/example/review/review/ReviewWorkflowServiceTest.java`
- Modify: `services/agent/pyproject.toml`
- Modify: `services/agent/app/routes/tasks.py`
- Modify: `services/agent/app/workflows/paper_understanding.py`

- [x] **Step 1: Write failing integration tests**

```java
@Test
void reviewAssistTaskUploadsPdfAndStoresPendingTask() {}

@Test
void pollingCompletedTaskPersistsRawAndRedactedResults() {}
```

Also add focused FastAPI tests for `multipart/form-data` task creation with:

- `metadata` JSON field
- `file` PDF part
- internal API-key enforcement
- extracted text and section data flowing into the task `requestPayload`
- JSON task creation compatibility

Also add Java tests for:

- missing PDF rejection
- completed polling result persistence
- timeout-to-failed transition
- external task idempotency
- reviewer redacted-only result visibility
- conflict-analysis payload assembly from Oracle review reports

- [x] **Step 2: Implement multipart upload from Oracle BLOB**

The integration service must:

- read `PDF_FILE` from Oracle
- send `metadata` JSON
- send PDF bytes as multipart file

The matching FastAPI endpoint must parse the multipart request, run the scoped PDF/tool ingestion helpers, and store the normalized request payload before background workflow execution starts.

- [x] **Step 3: Implement PDF ingestion helpers and richer Paper Understanding**

Implement:

- `PdfExtractTool` as deterministic PDF text extraction
- `SectionSplitTool` as deterministic coarse section splitting
- route-level metadata normalization, not `MetadataFetchTool`
- richer `PaperUnderstanding` fields for keywords, sections, `pdfText`, research problem, main results, limitations, and citation signals

- [x] **Step 4: Implement polling and timeout failure**

If polling exceeds 10 minutes:

- mark task `FAILED`
- allow manual retry later

- [x] **Step 5: Implement result query endpoints**

Expose:

- `GET /api/manuscripts/{id}/versions/{versionId}/agent-results`
- `POST /api/review-rounds/{roundId}/conflict-analysis`

Task 9 intentionally defers:

- `POST /api/agent-results/{resultId}/feedback`
- `GET /api/manuscripts/{id}/agent-feedback`

- [x] **Step 6: Re-run integration tests**

Run:

- `./.venv/bin/python -m pytest services/agent/tests/test_multipart_tasks_api.py -q`
- `./.venv/bin/python -m pytest services/agent/tests -q`
- `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AgentIntegrationServiceTest test`
- `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`
- `bash scripts/test-all.sh`

Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add apps/api/src/main/java/com/example/review/agent apps/api/src/test/java/com/example/review/agent apps/api/src/main/resources/application.yml apps/api/src/test/java/com/example/review/decision/DecisionServiceTest.java apps/api/src/test/java/com/example/review/manuscript/ManuscriptServiceTest.java apps/api/src/test/java/com/example/review/review/ReviewWorkflowServiceTest.java services/agent/app services/agent/tests services/agent/pyproject.toml AGENTS.md docs/superpowers/specs/2026-04-13-task9-agent-integration-design.md docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md
git commit -m "feat: integrate main system with agent service"
```

## Task 10: Build the Frontend Authentication and Shell

**Status:** Completed on 2026-04-13 after frontend auth and shell verification.

**Design Note:** Detailed Task 10 design is documented in `docs/superpowers/specs/2026-04-13-task10-frontend-auth-shell-design.md`.

**Scope Decision (2026-04-13):** Use a lightweight Vue reactive auth store rather than adding Pinia. Task 10 should implement login, JWT persistence, route guards, API helper, and a role-aware app shell only. Do not implement Task 11 workflow screens, screening-start backend work, or agent feedback in this slice.

**Execution Summary:** Replaced the scaffold `App.vue` with a router outlet, added a lightweight auth store that decodes JWT claims and clears expired or malformed tokens, added an API helper with configurable base URL and bearer-token injection, added Vite `/api` dev proxying, implemented login/dashboard/shell views, and rewrote the old scaffold `App.spec.ts` to assert anonymous users land on login. Added focused Vitest coverage for route guards, successful and failed login, stored-token restoration, authenticated API headers, role-aware navigation, and logout.

**Verification Run:** `npm --prefix apps/web run test -- --run`; `npm --prefix apps/web run typecheck`; `npm --prefix apps/web run build`; `bash scripts/test-all.sh`. All completed successfully. The production build still reports Vite's large chunk warning after Element Plus bundling; it is not a build failure.

**Post-Review Optimization (2026-04-13):** Removed the module-level router singleton from `apps/web/src/router/index.ts`; the module now exports only `createAppRouter()`, and `apps/web/src/main.ts` creates the production router instance explicitly. Added a regression test proving that importing the router module does not call `createRouter`.

**Post-Review Verification:** `npm --prefix apps/web run test -- --run src/tests/login.spec.ts`; `npm --prefix apps/web run test -- --run`; `npm --prefix apps/web run typecheck`; `npm --prefix apps/web run build`. All completed successfully.

**Files:**
- Create: `apps/web/src/router/index.ts`
- Create: `apps/web/src/stores/auth.ts`
- Create: `apps/web/src/lib/api.ts`
- Create: `apps/web/src/views/LoginView.vue`
- Create: `apps/web/src/views/DashboardView.vue`
- Create: `apps/web/src/layouts/AppShell.vue`
- Create: `apps/web/src/tests/login.spec.ts`
- Modify: `apps/web/src/main.ts`
- Modify: `apps/web/src/App.vue`
- Modify: `apps/web/src/App.spec.ts`
- Modify: `apps/web/src/style.css`
- Modify: `apps/web/vite.config.ts`

- [x] **Step 1: Write failing frontend auth tests**

```ts
it("redirects anonymous users to login", () => {});
it("stores token after successful login", () => {});
```

- [x] **Step 2: Implement login and route guards**

Use JWT-bearing requests from the store to API endpoints.

- [x] **Step 3: Implement the role-based shell**

Provide navigation items for:

- author
- reviewer
- chair
- admin

- [x] **Step 4: Re-run frontend auth tests**

Run: `npm --prefix apps/web run test -- --run`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add apps/web/src/router apps/web/src/stores apps/web/src/lib apps/web/src/views apps/web/src/layouts apps/web/src/tests apps/web/src/main.ts apps/web/src/App.vue apps/web/src/App.spec.ts apps/web/src/style.css apps/web/vite.config.ts docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md docs/superpowers/specs/2026-04-13-task10-frontend-auth-shell-design.md
git commit -m "feat: add frontend auth and application shell"
```

## Task 11: Build the Workflow Screens

**Status:** Completed on 2026-04-13 after backend/frontend implementation and repository-level verification.

**Design Note:** Detailed Task 11 design is documented in `docs/superpowers/specs/2026-04-13-task11-workflow-screens-design.md`.

**Scope Decision (2026-04-13):** Implement real workflow pages with Element Plus `table`, `form`, `card`, and `descriptions` primitives. Before implementing each page, write a short page brief in this plan listing information elements, user actions, Element Plus components, endpoints, and deferred behavior. Do not use Figma or a separate visual design phase. Add only the minimal missing backend list/query/action endpoints required to render the actor-facing screens honestly. Extend the existing PDF download authorization so assigned reviewers and chair/admin users can access manuscript PDFs; do not add a parallel PDF endpoint.

**Files:**
- Create: `apps/api/src/main/java/com/example/review/workflow/WorkflowQueryController.java`
- Create: `apps/api/src/main/java/com/example/review/workflow/WorkflowQueryService.java`
- Create: `apps/api/src/test/java/com/example/review/workflow/WorkflowQueryServiceTest.java`
- Modify: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptController.java`
- Modify: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptService.java`
- Modify: `apps/api/src/main/java/com/example/review/manuscript/ManuscriptRepository.java`
- Modify: `apps/api/src/main/java/com/example/review/agent/AgentTaskController.java`
- Modify: `apps/api/src/main/java/com/example/review/agent/AgentDtos.java`
- Modify: `apps/api/src/main/java/com/example/review/agent/AgentIntegrationService.java`
- Modify: `apps/api/src/main/java/com/example/review/agent/AgentRepository.java`
- Modify: `apps/api/src/main/java/com/example/review/review/ReviewWorkflowService.java`
- Modify: `apps/api/src/test/java/com/example/review/review/ReviewWorkflowServiceTest.java`
- Create: `apps/web/src/views/author/ManuscriptListView.vue`
- Create: `apps/web/src/views/author/SubmitManuscriptView.vue`
- Create: `apps/web/src/views/reviewer/AssignmentListView.vue`
- Create: `apps/web/src/views/reviewer/ReviewEditorView.vue`
- Create: `apps/web/src/views/chair/ScreeningQueueView.vue`
- Create: `apps/web/src/views/chair/DecisionWorkbenchView.vue`
- Create: `apps/web/src/views/admin/AgentMonitorView.vue`
- Create: `apps/web/src/lib/workflow-api.ts`
- Create: `apps/web/src/tests/workflow.spec.ts`
- Modify: `apps/web/src/lib/api.ts`
- Modify: `apps/web/src/tests/login.spec.ts`
- Modify: `apps/web/src/router/index.ts`
- Modify: `apps/web/src/layouts/AppShell.vue`
- Modify: `apps/web/src/views/DashboardView.vue`
- Modify: `apps/web/src/style.css`
- Modify: `AGENTS.md`

**Page Briefs (2026-04-13):**

Author manuscript list:
- Information elements: manuscript title, manuscript id, current version id/no, current status, blind mode, submitted date, last decision, PDF/version indicators.
- User actions: create manuscript, create revision when `REVISION_REQUIRED`, upload PDF for current draft/revision, submit current version, download PDF.
- Element Plus components: `el-table`, `el-tag`, `el-button`, `el-upload`, `el-empty`, `el-dialog`, `el-descriptions`.
- Endpoints: `GET /api/manuscripts`, `GET /api/manuscripts/{id}/versions`, `POST /api/manuscripts/{id}/versions`, `POST /api/manuscripts/{id}/versions/{versionId}/pdf`, `GET /api/manuscripts/{id}/versions/{versionId}/pdf`, `POST /api/manuscripts/{id}/versions/{versionId}/submit`.
- Deferred behavior: rich manuscript detail timeline and decision letter rendering remain out of scope.

Author submit manuscript:
- Information elements: title, abstract, keywords, blind mode, author rows, PDF file, create/submit state.
- User actions: create initial manuscript, add/remove author rows, upload PDF after creation, submit version.
- Element Plus components: `el-form`, `el-input`, `el-select`, `el-upload`, `el-button`, `el-alert`, repeated author row controls.
- Endpoints: `POST /api/manuscripts`, `POST /api/manuscripts/{id}/versions/{versionId}/pdf`, `POST /api/manuscripts/{id}/versions/{versionId}/submit`.
- Deferred behavior: co-author lookup/autocomplete and draft autosave remain out of scope.

Reviewer assignment list:
- Information elements: assignment id, manuscript title, round id, version id/no, task status, deadline, accepted date, submitted date, recommendation.
- User actions: accept assigned task, decline with reason/conflict declaration, open review editor.
- Element Plus components: `el-table`, `el-tag`, `el-button`, `el-dialog`, `el-form`, `el-input`.
- Endpoints: `GET /api/review-assignments`, `POST /api/review-assignments/{assignmentId}/accept`, `POST /api/review-assignments/{assignmentId}/decline`.
- Deferred behavior: reviewer calendar and bulk actions remain out of scope.

Reviewer review editor:
- Information elements: assignment detail, title, abstract, keywords, PDF file name, five scores, confidence, recommendation, strengths/weaknesses/comments, redacted agent results.
- User actions: download PDF, submit review report, return to assignment list.
- Element Plus components: `el-descriptions`, `el-form`, `el-select`, `el-input`, `el-button`, `el-alert`, `el-card`.
- Endpoints: `GET /api/review-assignments/{assignmentId}`, `GET /api/manuscripts/{id}/versions/{versionId}/pdf`, `POST /api/review-assignments/{assignmentId}/review-report`, `GET /api/manuscripts/{id}/versions/{versionId}/agent-results`.
- Deferred behavior: autosave drafts and rich PDF preview remain out of scope.

Chair screening queue:
- Information elements: manuscript id, version id, title, status, blind mode, submitted date, PDF status/size, current round number.
- User actions: start screening, trigger screening agent analysis, desk reject, create review round.
- Element Plus components: `el-table`, `el-tag`, `el-button`, `el-dialog`, `el-form`, `el-descriptions`.
- Endpoints: `GET /api/chair/screening-queue`, `POST /api/manuscripts/{manuscriptId}/versions/{versionId}/start-screening`, `POST /api/manuscripts/{manuscriptId}/versions/{versionId}/agent-tasks`, `POST /api/decisions`, `POST /api/review-rounds`.
- Deferred behavior: plagiarism integrations and batch screening remain out of scope.

Chair decision workbench:
- Information elements: round id/no/status, manuscript title/status, assignment count, submitted review count, conflict count, deadline, assignments, conflict checks, raw agent results, decision form.
- User actions: assign reviewer, mark overdue, reassign overdue, view conflict checks, trigger conflict analysis, submit decision.
- Element Plus components: `el-table`, `el-dialog`, `el-descriptions`, `el-form`, `el-select`, `el-input`, `el-button`, `el-tag`, `el-card`.
- Endpoints: `GET /api/chair/decision-workbench`, `POST /api/review-rounds/{roundId}/assignments`, `POST /api/review-assignments/{assignmentId}/mark-overdue`, `POST /api/review-assignments/{assignmentId}/reassign`, `GET /api/review-rounds/{roundId}/conflict-checks`, `POST /api/review-rounds/{roundId}/conflict-analysis`, `GET /api/manuscripts/{id}/versions/{versionId}/agent-results`, `POST /api/decisions`.
- Deferred behavior: side-by-side full review report comparison remains out of scope.

Admin agent monitor:
- Information elements: task id, external task id, type, status, manuscript/version/round ids, created/finished time, summary.
- User actions: filter by status/type, refresh list, inspect identifiers.
- Element Plus components: `el-table`, `el-select`, `el-button`, `el-tag`, `el-descriptions`.
- Endpoints: `GET /api/agent-tasks`.
- Deferred behavior: feedback, retries, cancel/requeue, provider failover controls remain out of scope.

**Implementation Result (2026-04-13):**
- Backend added `WorkflowQueryController` / `WorkflowQueryService` for reviewer assignment list/detail, chair screening queue, chair decision workbench, and admin agent task list.
- Backend extended existing manuscript PDF download authorization to authors, assigned reviewers, chair, and admin users; added `start-screening`; and aligned review round creation so a manuscript can move from `UNDER_SCREENING` into a review round.
- Frontend added workflow API helpers and seven role-specific pages wired into real guarded routes and role-aware navigation.
- Tests added: backend workflow query integration coverage, review round creation after screening starts, and frontend workflow screen coverage for desk reject action, reviewer redacted results, and chair raw results.

- [x] **Step 1: Write failing workflow UI tests**

```ts
it("shows desk reject action in chair screening queue", () => {});
it("shows redacted agent results for reviewer", () => {});
it("shows raw agent results for chair", () => {});
```

- [x] **Step 2: Add minimal backend workflow query endpoints**

Support:

- reviewer assignment list/detail
- chair screening queue
- chair decision workbench
- explicit start-screening action
- admin agent task list
- PDF download authorization for assigned reviewers and chair/admin users

- [x] **Step 3: Write page brief and implement author screens**

Support:

- create manuscript
- create version
- upload PDF
- submit revision

- [x] **Step 4: Write page brief and implement reviewer screens**

Support:

- accept or decline assignment
- declare conflict
- submit review
- view assignment history

- [x] **Step 5: Write page brief and implement chair screens**

Support:

- start screening
- assign reviewers
- view conflict checks
- trigger conflict analysis
- submit decisions
- reassign overdue tasks

- [x] **Step 6: Write page brief and implement admin agent monitor**

Support:

- list agent tasks
- filter by status/type
- inspect task identifiers and summaries

- [x] **Step 7: Re-run workflow UI and backend tests**

Run: `npm --prefix apps/web run test -- --run`
Run: `npm --prefix apps/web run typecheck`
Run: `npm --prefix apps/web run build`
Run: `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`
Run: `bash scripts/test-all.sh`
Expected: PASS.

Actual (2026-04-13): PASS.
- `npm --prefix apps/web run test -- --run`: 11 passed.
- `npm --prefix apps/web run typecheck`: passed.
- `npm --prefix apps/web run build`: passed with the existing Element Plus/Vite chunk size warning.
- `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`: 40 passed.
- `bash scripts/test-all.sh`: passed; API 40 passed, agent health 1 passed with the existing Python 3.14/Pydantic v1 warning, web 11 passed, typecheck passed, build passed.

Post-review fix (2026-04-13): The review found `ADMIN` users were blocked by frontend route guards from chair workflow pages even though backend endpoints allow chair-or-admin. Added a route-guard test for admin access to `/chair/screening` and `/chair/decisions`, changed both route meta entries to `roles: ["CHAIR", "ADMIN"]`, and recorded the reusable route/backend authorization alignment rule in `AGENTS.md`.

- [x] **Step 8: Commit**

```bash
git add apps/api/src/main/java/com/example/review apps/api/src/test/java/com/example/review apps/web/src docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md docs/superpowers/specs/2026-04-13-task11-workflow-screens-design.md
git commit -m "feat: add manuscript review and chair workflow screens"
```

## Task 12: End-to-End Verification and Demo Hardening

**Status:** Implementation in progress on 2026-04-13.

**Design Note:** Detailed Task 12 design is documented in `docs/superpowers/specs/2026-04-13-task12-e2e-demo-and-visual-hardening-design.md`.

**Scope Decision (2026-04-13):** Use MockMvc + Oracle API e2e as the primary full-flow verification rather than introducing Playwright. Add demo seed data and integrate it with `scripts/test-all.sh`. User approved full frontend polish for Task 12, so semantic status labels, agent trace panels, dossier headers, spacing harmonization, formatting helpers, and better empty states are all in scope.

**Files:**
- Create: `apps/api/src/test/java/com/example/review/e2e/ReviewFlowE2eTest.java`
- Create: `scripts/demo-seed.sh`
- Create: `database/oracle/007_seed_demo_workflow.sql`
- Modify: `apps/web/src/style.css`
- Modify: `apps/web/src/tests/workflow.spec.ts`
- Optional Create: `apps/web/src/lib/workflow-format.ts`
- Optional Modify: workflow Vue pages if needed for status labels and agent trace panels
- Modify: `scripts/test-all.sh`
- Modify: `docs/superpowers/specs/2026-04-09-paper-review-system-design.md`
- Create: `docs/superpowers/specs/2026-04-13-task12-e2e-demo-and-visual-hardening-design.md`
- Modify: `docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md`

- [x] **Step 1: Create an end-to-end scenario test**

The scenario must cover:

- author submits manuscript
- chair screens manuscript
- chair assigns reviewer
- reviewer submits review
- admin can read chair screening queue and decision workbench
- chair triggers conflict analysis
- chair makes decision
- author sees result

Execution note: Added `ReviewFlowE2eTest` with a MockMvc + Oracle happy path that logs in each actor, creates/uploads/submits a manuscript, verifies admin access to chair workbench endpoints, runs reviewer assignment and review submission, triggers review-assist and conflict analysis through a recording agent client, and verifies the author sees the revision decision. Targeted verification passed with `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=ReviewFlowE2eTest test`.

- [x] **Step 2: Add demo seed data**

Seed:

- one author
- two reviewers
- one chair
- one admin
- one manuscript with sample PDF
- sample PDF must be valid and text-extractable

Execution note: Added `database/oracle/007_seed_demo_workflow.sql` plus `scripts/demo-seed.sh`. The script first applies demo users, then merges a second reviewer, a submitted sample manuscript with a text-extractable PDF BLOB, authors, and a successful sample agent screening task/result. `scripts/test-all.sh` now runs `scripts/demo-seed.sh` when Docker is available. Verified `bash scripts/demo-seed.sh` twice against the local Oracle container with no duplicate-key failures.

- [x] **Step 2.5: Apply full frontend visual polish**

Execution note: Added `apps/web/src/lib/workflow-format.ts` and unified workflow pages around dossier headers, semantic status labels, formatted dates/file sizes, improved empty states, and Agent Trace panels for reviewer redacted results and chair raw results. Admin users now see chair workflow entries in the shell/dashboard to match the route authorization model. Added `apps/web/src/tests/setup.ts` because Node `v25.6.1` exposes an incomplete experimental `localStorage`; the Vitest setup now provides deterministic storage for auth tests. The reusable lesson was recorded in `AGENTS.md`.

Verification note: `npm run test -- --run src/tests/workflow.spec.ts`, `npm run test -- --run`, `npm run typecheck`, and `npm run build` passed in `apps/web`. The production build still emits the pre-existing Element Plus chunk-size warning, accepted by the Task 12 design.

- [x] **Step 3: Run the full verification script**

Run: `bash scripts/test-all.sh`  
Expected:

- backend tests pass
- agent tests pass
- frontend tests pass
- e2e happy path passes

Execution note: Full verification passed on 2026-04-13. `scripts/test-all.sh` applied `scripts/demo-seed.sh`, ran API tests with 41 passing tests including `ReviewFlowE2eTest`, ran agent pytest health with 1 passing test, then ran web Vitest with 13 passing tests, `vue-tsc --noEmit`, and `vite build`. The build emitted the known Element Plus chunk-size warning only.

- [x] **Step 4: Update the design doc with implementation notes if needed**

Only record deviations if implementation differs from spec.

Execution note: Updated the Task 12 design doc to reflect the approved full visual scope. Updated `AGENTS.md` with two reusable lessons from verification: explicit frontend test `localStorage` shim on bleeding-edge Node runtimes, and the full Oracle workflow cleanup order around `SOURCE_DECISION_ID`, `DECISION_RECORD`, and `REVIEW_ROUND`.

- [x] **Step 5: Commit**

```bash
git add apps/api/src/test/java/com/example/review/e2e scripts apps/web/src docs/superpowers/specs/2026-04-09-paper-review-system-design.md docs/superpowers/specs/2026-04-13-task12-e2e-demo-and-visual-hardening-design.md docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md
git commit -m "test: add end-to-end verification and demo seed"
```

Execution note: Committed Task 12 implementation with message `test: harden end-to-end demo flow`.

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
- Agent task types, schemas, redaction, PDF ingestion, and result persistence: Tasks 8 and 9. Agent feedback is deferred out of Task 9.
- API surface and frontend views: Tasks 9, 10, 11
- Deployment/demo readiness: Task 12

## Placeholder Scan

This plan intentionally defines:

- exact target paths
- explicit task order
- concrete commands for verification
- concrete workflow and schema examples

Avoid adding new endpoints or tables not already in the approved spec unless implementation exposes a concrete gap.
