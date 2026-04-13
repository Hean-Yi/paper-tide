# Task 9 Agent Integration Design

Date: 2026-04-13
Task: Task 9 - Integrate the Main System with Agent Service
Scope: `apps/api`, `services/agent`

## Goal

Connect the Spring Boot main system to the FastAPI agent service through the multipart/PDF contract from the overall design.

Task 9 must implement the core integration path:

1. The Java API creates a local agent analysis task.
2. The Java API reads the submitted PDF from Oracle `MANUSCRIPT_VERSION.PDF_FILE`.
3. The Java API submits `metadata` JSON plus the PDF file to the agent service with `multipart/form-data`.
4. The agent service extracts PDF text, splits coarse sections, normalizes `requestPayload`, and starts the existing LangGraph workflow path.
5. The Java API polls task status, pulls completed results, and persists raw and redacted results to Oracle.
6. The Java API exposes result query endpoints with role-specific visibility.

Task 9 does not implement feedback collection, durable queues, automatic retries, provider failover, a general autonomous tool-calling framework, or agent-service access to Oracle.

## Scope Decisions

### Runtime Dependencies

Add to `services/agent/pyproject.toml`:

- `pypdf`

No live OpenRouter dependency is required for Task 9 tests.

### Multipart Contract

Keep the existing `POST /agent/tasks` endpoint and support both request types:

- `application/json`: existing Task 8 behavior.
- `multipart/form-data`: Task 9 PDF ingestion behavior.

The multipart fields are:

- `metadata`: JSON string using the same logical fields as `CreateTaskRequest`.
- `file`: PDF binary.

The route layer parses and normalizes metadata before creating the task. Workflow nodes must receive a clean `requestPayload`; they should not need to understand multipart-specific field shapes.

### Tool Layer

Implement the required Tool-layer subset as deterministic Python helpers called directly by workflow-adjacent code. Do not add tool registration, dynamic discovery, `ToolNode`, or LLM-driven tool selection in Task 9.

Required helpers:

- `PdfExtractTool`: extract best-effort text from a PDF using `pypdf`.
- `SectionSplitTool`: split extracted text into coarse sections with deterministic heading and fallback heuristics.

Reuse existing behavior:

- Redaction stays in the existing redaction module.
- Result validation stays in the existing Pydantic schema path.

Do not implement these as Task 9 agent-service tools:

- `MetadataFetchTool`: metadata normalization belongs in the route layer.
- `ConflictCompareTool`: review-report aggregation belongs in the Java service layer because Java owns Oracle business data.
- `ReviewTemplateTool`: if needed, keep review template text as static prompt input, not a tool framework.

### Feedback Deferral

Do not implement feedback endpoints in Task 9:

- `POST /api/agent-results/{resultId}/feedback`
- `GET /api/manuscripts/{id}/agent-feedback`

Feedback is a separate feature slice. Task 9 focuses on task creation, PDF submission, polling, result persistence, and result query.

## Agent Service Design

### Request Flow

For multipart task creation:

1. Enforce the existing `X-Agent-Api-Key` behavior.
2. Parse `metadata` as JSON.
3. Validate task type and identifiers using the existing request model semantics.
4. Validate that `file` is a PDF by content type and/or file name.
5. Extract text with `PdfExtractTool`.
6. Split sections with `SectionSplitTool`.
7. Merge extracted content with normalized metadata.
8. Create or reuse the task with `TaskStore.create_or_reuse_task`.
9. Start background execution only for a newly-started pending task, preserving Task 8 cache behavior.

### Normalized Request Payload

The agent service stores a normalized payload shaped like this:

```json
{
  "title": "string",
  "abstract": "string",
  "keywords": ["string"],
  "pdfText": "string",
  "sections": {
    "introduction": "string",
    "method": "string",
    "experiment": "string",
    "conclusion": "string"
  },
  "pdfExtraction": {
    "fileName": "string",
    "fileSize": 123,
    "status": "EXTRACTED"
  },
  "reviewReports": []
}
```

`pdfExtraction.status` may be:

- `EXTRACTED`: readable text was extracted.
- `EMPTY_TEXT`: the PDF was readable but yielded no useful text.

Unreadable PDFs or non-PDF uploads return `400`.

### Paper Understanding

Update `PaperUnderstanding` in `services/agent/app/workflows/paper_understanding.py` to consume the richer Task 9 payload. The intermediate representation must include at least:

```json
{
  "manuscriptId": "string",
  "versionId": "string",
  "title": "string",
  "abstractSummary": "string",
  "keywords": ["string"],
  "researchProblem": "string",
  "claimedContributions": ["string"],
  "methodSummary": "string",
  "experimentSummary": "string",
  "mainResults": ["string"],
  "limitationsMentioned": ["string"],
  "citationSignals": ["string"],
  "possibleBlindnessRisks": ["string"],
  "pdfText": "string",
  "sections": {
    "introduction": "string",
    "method": "string",
    "experiment": "string",
    "conclusion": "string"
  }
}
```

Analysis nodes should use this intermediate representation so Task 9 workflows can reason over actual extracted paper content, not only metadata.

### Known Limitation

Task 9 still uses the in-memory `TaskStore`. Large `pdfText` values are stored in memory and participate in `input_fingerprint` calculation through the canonical payload. The cache key stores only the fingerprint, but each task record stores the full normalized payload. This is acceptable for the current local/demo slice; durable persistence and payload offloading remain out of scope.

## Java API Design

### Components

Create `com.example.review.agent` with:

- `AgentTaskController`
- `AgentIntegrationService`
- `AgentPollingScheduler`
- `AgentRepository`
- DTOs for task creation, status, result query, and agent-service responses

Use one repository for Task 9 task/result SQL unless the implementation becomes too large. Feedback persistence is intentionally excluded.

### Configuration

Add configuration under:

```yaml
review:
  agent:
    base-url: http://localhost:8001
    internal-api-key: local-dev-internal-key
    timeout-seconds: 30
    polling-timeout-minutes: 10
```

The internal API key must be sent to the agent service as `X-Agent-Api-Key`.

### Main-System Endpoints

Implement:

- `POST /api/manuscripts/{id}/versions/{versionId}/agent-tasks`
- `POST /api/review-rounds/{roundId}/conflict-analysis`
- `GET /api/manuscripts/{id}/versions/{versionId}/agent-results`

Defer:

- `POST /api/agent-results/{resultId}/feedback`
- `GET /api/manuscripts/{id}/agent-feedback`

### Task Creation Flow

For manuscript/version task creation:

1. Verify the caller has `CHAIR` or `ADMIN`.
2. Verify manuscript and version exist and match.
3. Read `PDF_FILE`, `PDF_FILE_NAME`, and `PDF_FILE_SIZE`.
4. If no PDF is stored, return `400`.
5. Insert `AGENT_ANALYSIS_TASK` with `PENDING` and a `REQUEST_PAYLOAD` summary.
6. Submit multipart request to the agent service.
7. Persist `EXTERNAL_TASK_ID` idempotently.
8. Return local task information.

For `DECISION_CONFLICT_ANALYSIS`, Java must query and normalize review reports from Oracle into `requestPayload.reviewReports` before calling the agent service. The agent service must not perform business aggregation from Oracle.

### External Task Idempotency

`AGENT_ANALYSIS_TASK.EXTERNAL_TASK_ID` has a unique constraint. Writing it must be idempotent:

- If the same local task already has the returned external task ID, treat the write as successful.
- If another local task already has the external task ID, reuse or report the existing task state intentionally; do not allow a unique constraint exception to escape as an accidental `500`.
- Polling must also be idempotent: if `AGENT_ANALYSIS_RESULT` already exists for a task, repeated success polling should not insert a duplicate result.

A simple implementation can use "check before update" plus "catch duplicate and re-check" rather than a complex `MERGE`.

### Polling Flow

`AgentPollingScheduler.pollOnce()` processes local tasks with `PENDING` or `PROCESSING` and a non-null `EXTERNAL_TASK_ID`.

For each task:

1. If task age exceeds `polling-timeout-minutes`, mark `FAILED`.
2. Fetch `/agent/tasks/{externalTaskId}`.
3. Sync status and step for non-terminal tasks.
4. On `SUCCESS`, fetch `/agent/tasks/{externalTaskId}/result`.
5. Validate result envelope has `resultType`, `rawResult`, and `redactedResult`.
6. Insert or reuse `AGENT_ANALYSIS_RESULT`.
7. Mark the local task `SUCCESS` with `FINISHED_AT`.
8. On agent error or invalid result, mark local task `FAILED` with a readable summary.

Task 9 does not add automatic retry scheduling.

## Authorization

Use the existing Spring Security roles:

- `CHAIR` / `ADMIN`: may trigger agent tasks and see raw plus redacted results.
- `REVIEWER`: may query redacted results only for manuscripts assigned to them.
- `AUTHOR`: may not query agent results in Task 9.

The Java API should enforce these rules even though the agent service itself is protected only by internal API key.

## Testing Plan

### FastAPI Tests

Add focused tests for:

- multipart task creation succeeds with `metadata` and PDF `file`.
- multipart task creation enforces `X-Agent-Api-Key`.
- non-PDF uploads return `400`.
- unreadable PDFs return `400`.
- extracted text and sections flow into the stored `requestPayload`.
- multipart task creation uses the existing cache/fingerprint behavior.
- existing JSON task creation remains compatible.

Agent tests must not require live OpenRouter calls.

### Java Tests

Add Oracle-backed or slice-level integration tests for:

- `reviewAssistTaskUploadsPdfAndStoresPendingTask`
- `agentTaskCreationFailsWhenPdfMissing`
- `pollingCompletedTaskPersistsRawAndRedactedResults`
- `pollingTimedOutTaskMarksFailed`
- `reviewerGetsOnlyRedactedResult`
- `externalTaskIdWriteIsIdempotentForRepeatedRetry`
- `conflictAnalysisPayloadIncludesReviewReports`

Use a mock HTTP server or stubbed client for the FastAPI service. Tests should verify multipart shape, internal API-key header, local task persistence, result persistence, and role-based response filtering.

## Acceptance Criteria

Task 9 is complete when:

1. FastAPI supports both JSON and multipart `POST /agent/tasks`.
2. Multipart task creation extracts PDF text and sections into normalized `requestPayload`.
3. `PaperUnderstanding` consumes Task 9 PDF/content fields.
4. Java can create local agent tasks, submit Oracle PDF BLOBs to FastAPI, and persist external task IDs idempotently.
5. Java polling can sync status, persist successful results, and mark timed-out or failed tasks.
6. Java result query returns raw results only to `CHAIR` / `ADMIN` and redacted results to eligible reviewers.
7. Feedback endpoints remain deferred.
8. FastAPI and Java tests for this slice pass without requiring live LLM calls.
