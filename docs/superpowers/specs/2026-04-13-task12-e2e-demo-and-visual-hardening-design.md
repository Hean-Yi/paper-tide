# Task 12 End-to-End Demo Hardening and Visual Polish Design

## Status

Approved direction with two adjustments from review:

- Main verification should use Oracle-backed API end-to-end testing rather than adding Playwright.
- Frontend visual polish should be scoped tightly: agent trace panels and semantic status labels are required; broader layout refinements are optional after verification is stable.

## Goal

Task 12 should make the system demonstrable and regression-resistant at the end of the implementation stream.

It must prove the full paper review workflow can run through the real Spring Boot service layer and Oracle schema, and it should improve the Task 11 frontend enough that the demo reads as a coherent scholarly review system rather than a scaffolded admin panel.

## Non-Goals

- Do not introduce Playwright, browser drivers, or a new e2e dependency in Task 12.
- Do not add new workflow product features beyond demo hardening.
- Do not add durable agent queues, retries, provider failover, or feedback endpoints.
- Do not redesign every page from scratch.
- Do not add a marketing landing page.

## Verification Strategy

### Chosen Approach

Use a Spring Boot MockMvc + Oracle integration test as the primary end-to-end scenario.

This replaces the original `apps/web/tests/e2e/review-flow.spec.ts` idea with a lower-risk equivalent that exercises the actual backend workflow, authorization, state transitions, Oracle persistence, and agent integration boundary without requiring browser installation or Playwright orchestration.

Frontend coverage remains Vitest-based smoke coverage for route guards, role-specific pages, and visual/agent result separation.

### End-to-End Scenario

Add an API e2e test, likely `apps/api/src/test/java/com/example/review/e2e/ReviewFlowE2eTest.java`.

The scenario should cover:

1. Author logs in.
2. Author creates a manuscript.
3. Author uploads a valid sample PDF.
4. Author submits the current version.
5. Chair starts screening.
6. Admin can access chair workflow reads:
   - `GET /api/chair/screening-queue`
   - `GET /api/chair/decision-workbench`
7. Chair creates a review round.
8. Chair assigns reviewer.
9. Reviewer accepts assignment.
10. Reviewer downloads PDF.
11. Reviewer sees redacted agent results when present.
12. Reviewer submits a review report.
13. Chair triggers conflict analysis using the existing recording/fake agent client pattern.
14. Chair sees raw agent results when present.
15. Chair makes a decision.
16. Author lists manuscripts and sees the resulting status/decision.

The admin chair-access step is mandatory because Task 11 post-review found a frontend route mismatch between `CHAIR` and `ADMIN`. Task 12 should keep this class of regression visible at the scenario level, not only in a route-unit test.

### Agent Boundary

The e2e test should not call a live LLM or external agent provider.

Use the existing Java test pattern around `RecordingAgentServiceClient` or a similar test client so the main system still exercises:

- local `AGENT_ANALYSIS_TASK` creation
- external task id attachment
- polling
- result persistence
- raw vs redacted result lookup behavior

### Sample PDF

Demo seed and tests must use a valid minimal PDF with extractable text. Reuse the shape of the valid PDF fixture from `services/agent/tests/test_multipart_tasks_api.py` rather than empty bytes or a broken fake PDF.

The sample should contain readable demo text such as:

- title-like line
- abstract-like line
- method/result sentence

This prevents agent-service PDF extraction from producing an empty-text path during demos.

## Demo Seed

### Script

Create or extend a script for demo workflow data. The existing repo has `scripts/oracle-demo-seed.sh` for demo users. Task 12 should add a narrow, idempotent workflow seed script, for example:

- `scripts/demo-seed.sh`

The script should be safe to run repeatedly and should not require dropping schema objects.

### Seed Data

Seed:

- `author_demo`
- `reviewer_demo`
- second reviewer for conflict/reassignment demos
- `chair_demo`
- `admin_demo`
- one sample manuscript with current version and valid PDF
- one optional review round/assignment if useful for immediate reviewer and chair screens

The seed should favor demo navigation, not test isolation. Tests that need exact table state should still clean and seed their own records.

### Relationship to `scripts/test-all.sh`

`scripts/test-all.sh` already applies `scripts/oracle-demo-seed.sh` before API tests when Docker is available. Task 12 should extend it to apply the workflow demo seed too, if present.

The script should remain tolerant of missing local runtimes. If Oracle/Docker is not available, existing skip behavior should remain intact.

## Frontend Visual Direction

### Chosen Style

Use `Editorial Dossier + Agent Trace`.

The general workflow UI should feel like a clear academic review dossier:

- calm white and off-white surfaces
- ink text
- restrained teal primary actions
- amber warning and red destructive states
- compact but readable tables
- page headings that frame a manuscript or workflow as a review record

Agent-related areas should use a more technical trace language:

- monospace JSON/result panels
- left-edge status indicators
- compact trace rows for task states
- clear raw/redacted labeling
- no full dark theme

This visual direction is informed by the local `awesome-design-md` index:

- Notion-style document calm for dossier readability.
- Mintlify-style reading clarity for technical detail.
- IBM/Vercel-style precision for tables and operational hierarchy.
- VoltAgent-style trace vocabulary only in agent-specific areas.

The resulting style must be original to this project, not a copy of any single reference.

### Required Polish

Required visual changes:

- Add agent trace panel styling for:
  - reviewer redacted agent results
  - chair raw agent results
  - admin agent monitor summary/result areas if present
- Add semantic status label mapping:
  - success/accepted/submitted states
  - warning/pending/under screening states
  - danger/rejected/failed/overdue states
  - neutral draft/in-progress states
- Ensure these mappings are centralized enough that pages do not all invent their own status color logic.

### Optional Polish

Optional if verification work is already stable:

- Dossier-like page headers for workflow pages.
- More consistent spacing between tables, forms, and descriptions.
- A small `workflow-format.ts` helper for date, file size, status tag type, and JSON formatting.
- Better empty states for demo screens.

Optional polish should be skipped if it risks destabilizing Task 12 verification.

## Implementation Slices

### Slice 1: Verification Red/Green

- Add the API e2e test first and watch it fail on missing demo/e2e support.
- Implement only what is needed to pass.
- Include admin access to chair workflow reads.

### Slice 2: Demo Seed

- Add an idempotent workflow demo seed script.
- Integrate it into `scripts/test-all.sh`.
- Keep seeded PDF valid and extractable.

### Slice 3: Required Frontend Polish

- Add semantic status formatting.
- Add agent trace panel styling.
- Add or adjust Vitest coverage for the visual/role behaviors that matter for demo safety.

### Slice 4: Verification

Run:

- `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test`
- `npm --prefix apps/web run test -- --run`
- `npm --prefix apps/web run typecheck`
- `npm --prefix apps/web run build`
- `bash scripts/test-all.sh`

Expected:

- API tests include the new e2e scenario.
- Frontend tests include the existing Task 11 route and raw/redacted coverage.
- Agent health test passes.
- Build passes, allowing the existing Element Plus chunk-size warning unless Task 12 explicitly introduces code-splitting.

## Risks and Controls

- Risk: Playwright would expand setup risk late in the project.
  Control: Use MockMvc + Oracle API e2e and Vitest frontend smoke.

- Risk: Demo seed data could make tests brittle.
  Control: Keep e2e tests self-seeding where exact state matters; demo seed supports manual demo navigation.

- Risk: PDF fixture could fail extraction.
  Control: Use a valid minimal PDF with extractable text, not arbitrary bytes.

- Risk: Visual polish expands into redesign.
  Control: Required polish is limited to status labels and agent trace panels; all broader spacing/header work is optional.

- Risk: Frontend route roles drift from backend authorization.
  Control: Include admin chair-route API and frontend route coverage in Task 12 verification.

## Acceptance Criteria

- Full review workflow e2e passes through API integration testing.
- Demo seed can be run repeatedly without duplicate-key failures.
- `scripts/test-all.sh` runs the new e2e path when local dependencies are present.
- Admin access to chair workflow reads is verified.
- Agent raw/redacted separation remains verified.
- Agent trace panel and semantic status label styling are present.
- No new external browser/e2e dependency is introduced.
- Plan file records implementation and verification results after completion.
