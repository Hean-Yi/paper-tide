# Task 11 Workflow Screens Design

## Goal

Build the first role-specific workflow screens on top of the Task 10 authenticated shell:

- author manuscript list and submission flow
- reviewer assignment list and review editor
- chair screening queue and decision workbench
- admin agent monitor

Task 11 should use real API calls where the backend already supports them and add only the minimal missing query/action endpoints required for the pages to render honestly. It must not implement agent feedback, durable agent infrastructure, or end-to-end demo automation.

## Design Process Constraint

Before implementing each workflow page, spend a short text-only pass listing:

- information elements on the page
- user actions on the page
- Element Plus components to use
- backend endpoints used by that page
- unavailable behavior intentionally deferred

Write that page brief into the Task 11 execution notes in the authoritative plan before editing the page file. This replaces Figma or a separate visual design phase. After the brief is written, implement directly with Element Plus `table`, `form`, `card`, `descriptions`, `tag`, `dialog`, `upload`, `select`, and `button` components.

## Visual Direction

Use a pragmatic dashboard style inspired by the structured-data parts of Airtable and the clean reading density of Mintlify from the local `awesome-design-md` index. Do not copy either brand. Translate the direction into this project as:

- neutral full-width authenticated app surfaces
- dense but readable tables
- clear status tags
- compact forms with explicit labels
- Element Plus defaults where they help consistency
- restrained semantic accents: blue for information, green for success, amber for warnings, red for destructive actions

Keep the existing Task 10 shell as the base. Avoid decorative hero sections, nested cards, large gradients, and mock marketing copy. Cards should be individual content units only, with border radius at or below 8px.

## Existing API Coverage

Already available:

- `GET /api/manuscripts`
- `POST /api/manuscripts`
- `GET /api/manuscripts/{id}`
- `POST /api/manuscripts/{id}/versions`
- `GET /api/manuscripts/{id}/versions`
- `POST /api/manuscripts/{id}/versions/{versionId}/pdf`
- `GET /api/manuscripts/{id}/versions/{versionId}/pdf`
- `POST /api/manuscripts/{id}/versions/{versionId}/submit`
- `POST /api/review-rounds`
- `POST /api/review-rounds/{roundId}/assignments`
- `GET /api/review-rounds/{roundId}/conflict-checks`
- `POST /api/review-assignments/{assignmentId}/accept`
- `POST /api/review-assignments/{assignmentId}/decline`
- `POST /api/review-assignments/{assignmentId}/mark-overdue`
- `POST /api/review-assignments/{assignmentId}/reassign`
- `POST /api/review-assignments/{assignmentId}/review-report`
- `GET /api/decisions`
- `POST /api/decisions`
- `POST /api/manuscripts/{manuscriptId}/versions/{versionId}/agent-tasks`
- `POST /api/review-rounds/{roundId}/conflict-analysis`
- `GET /api/manuscripts/{manuscriptId}/versions/{versionId}/agent-results`

Missing but required for honest Task 11 screens:

- reviewer assignment list and detail
- chair screening queue list
- chair decision workbench list/detail
- explicit screening-start action
- admin agent task list
- PDF download authorization for reviewer and chair/admin users

Task 11 should add minimal backend endpoints for these. This follows the repository rule that actor-facing workflow tasks need the collection/list endpoint required to render that actor's primary screen.

## Minimal Backend Additions

### Reviewer Query

Add a query endpoint:

- `GET /api/review-assignments`
- role: `REVIEWER`
- returns only assignments belonging to the current reviewer

Fields:

- `assignmentId`
- `roundId`
- `manuscriptId`
- `versionId`
- `title`
- `versionNo`
- `taskStatus`
- `deadlineAt`
- `acceptedAt`
- `submittedAt`
- `roundStatus`
- `recommendation` when a report already exists

Add detail endpoint if needed by editor:

- `GET /api/review-assignments/{assignmentId}`
- role: assignment owner reviewer, chair, or admin
- includes the same fields plus `abstract`, `keywords`, `pdfFileName`, and existing report summary when available.

### Chair Workflow Query

Add chair/admin query endpoints:

- `GET /api/chair/screening-queue`
- `GET /api/chair/decision-workbench`

`screening-queue` returns manuscripts in `SUBMITTED`, `REVISED_SUBMITTED`, or `UNDER_SCREENING` with:

- `manuscriptId`
- `versionId`
- `title`
- `currentStatus`
- `blindMode`
- `submittedAt`
- `pdfFileName`
- `pdfFileSize`
- `currentRoundNo`

`decision-workbench` returns active review rounds and manuscripts in `UNDER_REVIEW` with:

- `roundId`
- `roundNo`
- `roundStatus`
- `manuscriptId`
- `versionId`
- `title`
- `currentStatus`
- `assignmentCount`
- `submittedReviewCount`
- `conflictCount`
- `deadlineAt`
- `lastDecisionCode`

Add explicit screening transition:

- `POST /api/manuscripts/{manuscriptId}/versions/{versionId}/start-screening`
- role: `CHAIR` or `ADMIN`
- allowed from `SUBMITTED` or `REVISED_SUBMITTED`
- sets manuscript status to `UNDER_SCREENING`

This closes the previously deferred screening-start entry point so the chair screening page can truthfully support desk reject.

### Admin Agent Monitor Query

Add:

- `GET /api/agent-tasks`
- role: `ADMIN`
- optional query params: `status`, `taskType`

Fields:

- `taskId`
- `externalTaskId`
- `taskType`
- `taskStatus`
- `manuscriptId`
- `versionId`
- `roundId`
- `createdAt`
- `finishedAt`
- `resultSummary`

Do not add feedback endpoints in Task 11.

### PDF Download Authorization

The existing `GET /api/manuscripts/{id}/versions/{versionId}/pdf` endpoint only allows the submitting author because `ManuscriptService.downloadPdf` calls `requireAuthor` and checks ownership. Task 11 reviewer and chair pages need real PDF access:

- authors may download their own manuscript PDFs, preserving current behavior
- reviewers may download a version PDF only when they have a review assignment for that manuscript/version
- chairs and admins may download manuscript PDFs for screening and decision work

Implement this by extending the existing endpoint's service authorization rather than adding a parallel PDF URL. Keep the same response shape and file headers.

## Frontend API Client

Extend `apps/web/src/lib/api.ts` with typed helper functions where that keeps view code clear. Keep the generic `apiRequest` as the underlying primitive.

Recommended split:

- `apps/web/src/lib/workflow-api.ts` for manuscript, review, decision, and agent monitor calls.
- Keep auth-specific calls in `api.ts` or a small auth section.

The helper layer should avoid a heavy client abstraction. A few typed functions are enough.

## Routing and Navigation

Replace Task 10 dashboard anchor navigation with real routes:

- `/author/manuscripts`
- `/author/submit`
- `/reviewer/assignments`
- `/reviewer/assignments/:assignmentId`
- `/chair/screening`
- `/chair/decisions`
- `/admin/agents`

Route guards should use existing route meta:

- author routes: `roles: ["AUTHOR"]`
- reviewer routes: `roles: ["REVIEWER"]`
- chair routes: `roles: ["CHAIR", "ADMIN"]`
- admin routes: `roles: ["ADMIN"]`

Dashboard cards should link to these routes instead of anchors.

## Page Designs

### Author Manuscript List

File: `apps/web/src/views/author/ManuscriptListView.vue`

Information elements:

- table of the current author's manuscripts
- title, version number, current status, blind mode, submitted date, last decision
- PDF/version quick indicators
- empty state for first submission

User actions:

- create new manuscript
- open submission form for revision when status is `REVISION_REQUIRED`
- upload PDF for current draft/revision version
- submit current version when PDF exists
- open PDF download

Element Plus components:

- `el-table`
- `el-tag`
- `el-button`
- `el-upload`
- `el-empty`
- `el-dialog`
- `el-descriptions` for selected row details

Backend:

- existing manuscript endpoints are sufficient.

### Author Submit Manuscript

File: `apps/web/src/views/author/SubmitManuscriptView.vue`

Information elements:

- manuscript title
- abstract
- keywords
- blind mode
- author rows with order, name, email, institution, corresponding flag, external flag
- PDF file selection

User actions:

- create initial manuscript
- create revision for an existing manuscript
- add/remove author rows
- upload PDF after version creation
- submit version

Element Plus components:

- `el-form`
- `el-input`
- `el-select`
- `el-table` or simple repeated author row group
- `el-upload`
- `el-button`
- `el-alert`

Backend:

- existing author manuscript/version/PDF/submit endpoints are sufficient.

### Reviewer Assignment List

File: `apps/web/src/views/reviewer/AssignmentListView.vue`

Information elements:

- reviewer assignments owned by the current user
- title, round, status, deadline, accepted date, submitted date
- existing recommendation when submitted
- status counters

User actions:

- accept assigned task
- decline with reason and optional conflict declaration
- open review editor

Element Plus components:

- `el-table`
- `el-tag`
- `el-button`
- `el-dialog`
- `el-form`
- `el-input`

Backend:

- requires the new reviewer assignment list endpoint.
- uses existing accept/decline endpoints.

### Reviewer Review Editor

File: `apps/web/src/views/reviewer/ReviewEditorView.vue`

Information elements:

- assignment detail
- manuscript title, abstract, keywords, PDF file name
- review scores: novelty, method, experiment, writing, overall
- confidence
- recommendation
- strengths, weaknesses, comments to author, comments to chair
- redacted agent results for the manuscript version

User actions:

- download PDF when available
- submit review report
- return to assignments

Element Plus components:

- `el-descriptions`
- `el-form`
- `el-rate` or `el-select` for 1-5 scores
- `el-input` textarea
- `el-select`
- `el-button`
- `el-alert`
- `el-card` for individual agent result items

Backend:

- requires assignment detail endpoint.
- requires extended PDF download authorization for assigned reviewers.
- uses existing review-report endpoint.
- uses existing agent results endpoint; reviewer receives redacted result only by backend policy.

### Chair Screening Queue

File: `apps/web/src/views/chair/ScreeningQueueView.vue`

Information elements:

- submitted/revised/under-screening manuscripts awaiting screening
- title, version, status, blind mode, submitted date, PDF status
- selected manuscript details

User actions:

- start screening
- trigger screening agent analysis
- desk reject when manuscript is under screening
- create review round for manuscripts ready to review

Element Plus components:

- `el-table`
- `el-tag`
- `el-button`
- `el-dialog`
- `el-form`
- `el-descriptions`

Backend:

- requires chair screening queue endpoint.
- requires start-screening endpoint.
- requires extended PDF download authorization for chair/admin users.
- uses existing agent task endpoint for `SCREENING_ANALYSIS`.
- uses existing decisions endpoint for `DESK_REJECT`.
- uses existing review-round creation endpoint.

### Chair Decision Workbench

File: `apps/web/src/views/chair/DecisionWorkbenchView.vue`

Information elements:

- active rounds and their manuscript status
- assignment count, submitted review count, conflict count, deadline
- conflict check rows
- agent results with raw result visible to chair/admin
- decision form

User actions:

- assign reviewer
- mark assignment overdue
- reassign overdue task
- view conflict checks
- trigger conflict analysis
- make accept/reject/revision decision

Element Plus components:

- `el-table`
- `el-drawer` or `el-dialog` for selected round details
- `el-descriptions`
- `el-form`
- `el-select`
- `el-input`
- `el-button`
- `el-tag`
- `el-card` for agent results

Backend:

- requires chair decision workbench endpoint.
- may need assignment rows in the workbench detail response to support overdue/reassign actions.
- uses existing assignment, conflict-check, conflict-analysis, agent-result, and decision endpoints.

### Admin Agent Monitor

File: `apps/web/src/views/admin/AgentMonitorView.vue`

Information elements:

- agent task list
- external task id, task type, status, manuscript/version/round identifiers
- created/finished time
- result summary
- status filters

User actions:

- filter by status/type
- open related manuscript/version identifiers
- refresh task list

Element Plus components:

- `el-table`
- `el-select`
- `el-button`
- `el-tag`
- `el-descriptions`

Backend:

- requires admin agent task list endpoint.
- no feedback actions in Task 11.

## Data and Loading Strategy

- Use `ref`/`reactive` state in each page; do not introduce Pinia in Task 11.
- Fetch page data on mount.
- Show `el-skeleton` or table loading state while loading.
- Use `ElMessage` for successful actions and `ElAlert` or inline messages for recoverable errors.
- After mutations, refresh the current page list/detail.
- Keep optimistic updates out of scope.

## Error Handling

- `401`: route guard/session state should send user back to login.
- `403`: show not-authorized inline state.
- `404`: show empty/not-found state for detail pages.
- `409`: show conflict message from backend, then refresh list/detail.
- network/`5xx`: show generic service unavailable message.

## Testing Strategy

Use Vitest and Vue Test Utils with mocked `fetch`.

Required tests:

- author list renders manuscripts and exposes submit/PDF actions only for valid statuses
- author submit flow sends create/upload/submit calls in order
- reviewer or chair PDF download is allowed only through assignment or chair/admin role
- reviewer assignment list shows accept/decline/editor actions
- reviewer review editor submits scores and comments
- reviewer sees redacted agent result only
- chair screening queue shows start screening and desk reject action
- chair decision workbench shows raw agent result and can trigger conflict analysis
- admin agent monitor renders task status filters
- route guards block roles from other actors' screens

Verification commands:

- `npm --prefix apps/web run test -- --run`
- `npm --prefix apps/web run typecheck`
- `npm --prefix apps/web run build`
- `mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test` if backend endpoints are added
- `bash scripts/test-all.sh`

## Acceptance Criteria

- AppShell navigation points to real workflow routes.
- Each role has a usable first screen.
- Pages use Element Plus table/form/card/descriptions primitives, not custom-heavy UI.
- Each page has a short pre-implementation page brief in the authoritative plan.
- Reviewer agent results never show raw payload.
- Chair/admin agent results can show raw payload.
- No fake workflow data is hardcoded into production pages.
- Task 11 does not implement feedback endpoints.
- Full repository verification passes.
