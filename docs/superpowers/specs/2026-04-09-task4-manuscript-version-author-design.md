# Task 4 Manuscript, Version, and Author Management Design

## Scope

Task 4 implements the backend manuscript management slice for authors. It covers manuscript creation, version creation for revisions, author snapshot persistence, PDF upload and download for a version, version submission, and author-only read endpoints for manuscript details and version history.

This task does not implement review rounds, reviewer assignment, chair decisions, notifications, or agent analysis. It only provides the manuscript and version behaviors those later tasks depend on.

## Options Considered

### Option 1: Stable manuscript aggregate with draft versions and explicit submit actions

Use `MANUSCRIPT` as the long-lived aggregate root, create `MANUSCRIPT_VERSION` rows for initial and revision drafts, store author snapshots in `MANUSCRIPT_AUTHOR`, upload PDFs separately, and require an explicit submit action to move workflow state.

Pros:

- Matches the approved system design and mainstream conference submission systems
- Keeps metadata creation, file upload, and state transitions separated
- Preserves historical author snapshots per version
- Leaves room for later decision-driven revision flows without redesign

Cons:

- Requires several focused endpoints instead of one large submit endpoint

### Option 2: Create manuscript and immediately submit it

Skip the draft state and treat manuscript creation as formal submission.

Pros:

- Fewer steps for the author flow

Cons:

- Conflicts with the approved state machine
- Makes later PDF replacement and draft editing awkward

### Option 3: Single multipart endpoint that creates metadata, uploads PDF, and submits

Do everything in one request and one transaction.

Pros:

- Smallest API surface initially

Cons:

- Couples large-file upload to business-state changes
- Blurs resource boundaries
- Harder to recover from partial user workflow errors

## Selected Approach

Use Option 1.

Task 4 will add a focused manuscript slice under `com.example.review.manuscript`:

- `ManuscriptController` for manuscript and version endpoints
- `ManuscriptService` for transaction boundaries, state transitions, and authorization checks
- `ManuscriptRepository` for `MANUSCRIPT` reads and writes
- `VersionRepository` for `MANUSCRIPT_VERSION` reads and writes
- `AuthorRepository` for `MANUSCRIPT_AUTHOR` snapshot persistence and reads

## Resource Model

`MANUSCRIPT` is the stable top-level entity for one paper across all versions and rounds.

`MANUSCRIPT_VERSION` stores one concrete version of that manuscript. Task 4 uses:

- `INITIAL` for the first draft version
- `REVISION` for revisions created after a revision-required decision

`RESUBMISSION` is intentionally out of scope for Task 4 and will be handled by a later task if the workflow requires it.

`MANUSCRIPT_AUTHOR` stores per-version author snapshots. Task 4 always writes the full author list when creating a version and does not provide independent author-edit endpoints.

`MANUSCRIPT.CURRENT_VERSION_ID` points to the latest created version. `CURRENT_STATUS` carries the workflow state visible to the rest of the system.

Task 4 write paths implement only these transitions:

- `DRAFT -> SUBMITTED`
- `REVISION_REQUIRED -> REVISED_SUBMITTED`

Task 4 read paths must recognize and return all schema-defined manuscript statuses without filtering them.

## API Design

### `POST /api/manuscripts`

Creates a new manuscript and its initial draft version.

Request body fields:

- `title`
- `abstract`
- `keywords`
- `blindMode`
- `authors[]`

Each author entry includes:

- `authorName`
- `email`
- `institution`
- `authorOrder`
- `userId` (optional for external authors)
- `isCorresponding`
- `isExternal`

Behavior:

- authenticated principal becomes `SUBMITTER_ID`
- create `MANUSCRIPT` with `CURRENT_STATUS = DRAFT`
- create `MANUSCRIPT_VERSION` with:
  - `VERSION_NO = 1`
  - `VERSION_TYPE = INITIAL`
  - `SOURCE_DECISION_ID = null`
- create `MANUSCRIPT_AUTHOR` rows for the version
- update `CURRENT_VERSION_ID` to the new version

### `POST /api/manuscripts/{id}/versions`

Creates a new revision draft version for an existing manuscript.

Request body fields:

- `title`
- `abstract`
- `keywords`
- `authors[]`

Behavior:

- only allowed when the manuscript is currently `REVISION_REQUIRED`
- create a new `MANUSCRIPT_VERSION` with:
  - `VERSION_NO = max(existing) + 1`
  - `VERSION_TYPE = REVISION`
  - `SOURCE_DECISION_ID = null` in Task 4
- create a new author snapshot for the new version
- update `CURRENT_VERSION_ID`
- keep manuscript status at `REVISION_REQUIRED` until explicit submit

`SOURCE_DECISION_ID` remains nullable in Task 4. A later decision workflow task will populate that linkage when revision creation is triggered from a real `DECISION_RECORD`.

### `POST /api/manuscripts/{id}/versions/{versionId}/pdf`

Uploads or replaces the PDF for a version.

Request:

- multipart field `file`

Behavior:

- verify the version belongs to the manuscript
- accept only PDF uploads by content type and/or filename extension
- write `PDF_FILE`, `PDF_FILE_NAME`, and `PDF_FILE_SIZE`
- allow overwrite only while the version is not yet submitted

### `GET /api/manuscripts/{id}/versions/{versionId}/pdf`

Downloads the stored PDF for a version.

Behavior:

- same ownership rules as other manuscript reads
- verify the version belongs to the manuscript
- return `application/pdf`
- return stored filename in the response headers when available

### `POST /api/manuscripts/{id}/versions/{versionId}/submit`

Submits the current draft version.

Behavior:

- verify the version belongs to the manuscript
- require that `versionId` matches `CURRENT_VERSION_ID`
- require that the version has a stored PDF
- apply only these transitions:
  - `DRAFT -> SUBMITTED`
  - `REVISION_REQUIRED -> REVISED_SUBMITTED`
- update `MANUSCRIPT.SUBMITTED_AT`
- update the version `SUBMITTED_AT` to the formal submission time

### `GET /api/manuscripts/{id}`

Returns author-visible manuscript details.

Response includes:

- `manuscriptId`
- `submitterId`
- `currentVersionId`
- `currentStatus`
- `currentRoundNo`
- `blindMode`
- `submittedAt`
- `lastDecisionCode`

### `GET /api/manuscripts`

Returns the current author's manuscript list for the "My Manuscripts" screen.

Each list entry includes:

- `manuscriptId`
- `currentVersionId`
- `currentStatus`
- `currentRoundNo`
- `blindMode`
- `submittedAt`
- `lastDecisionCode`
- `currentVersionTitle`
- `currentVersionNo`

### `GET /api/manuscripts/{id}/versions`

Returns version history without streaming the PDF body.

Each version entry includes:

- `versionId`
- `versionNo`
- `versionType`
- `title`
- `submittedAt`
- `pdfFileName`
- `pdfFileSize`

## Validation and Authorization

All manuscript endpoints require authentication.

Write endpoints require the `AUTHOR` role.

Task 4 uses both role checks and owner checks:

- the current principal must have the `AUTHOR` role
- the manuscript `SUBMITTER_ID` must match `CurrentUserPrincipal.userId()`

Task 4 read endpoints are intentionally limited to the submitter. Broader visibility for chairs and reviewers will be added in later tasks.

Application-level validation rules:

- `blindMode` must be one of `DOUBLE_BLIND`, `SINGLE_BLIND`, or `OPEN`
- author list must not be empty
- exactly one author must have `isCorresponding = true`
- author orders must be positive and unique within the request
- PDF upload must be a PDF
- a submitted version cannot have its PDF replaced
- a version cannot be submitted without a PDF

## Transaction Boundaries

### Create manuscript

One transaction performs:

- insert `MANUSCRIPT`
- insert initial `MANUSCRIPT_VERSION`
- insert all `MANUSCRIPT_AUTHOR` rows
- update `CURRENT_VERSION_ID`

Any failure rolls the whole aggregate creation back.

### Create revision version

One transaction performs:

- lock and read the manuscript with `SELECT ... FOR UPDATE`
- validate owner and state
- compute the next `VERSION_NO`
- insert the new version
- insert the author snapshot
- update `CURRENT_VERSION_ID`

### Upload PDF

One transaction updates the target `MANUSCRIPT_VERSION` row only.

PDF upload is intentionally separate from submit so large-file write failures cannot partially apply a workflow state transition.

### Submit version

One transaction performs:

- lock and read manuscript and current version
- validate owner, version linkage, and current version identity
- validate PDF presence
- apply the allowed state transition
- update `MANUSCRIPT.SUBMITTED_AT`
- update version `SUBMITTED_AT`

## Error Handling

Use these status-code categories:

- `400 Bad Request`
  - invalid `blindMode`
  - empty author list
  - corresponding-author count not equal to one
  - duplicate or invalid author order
  - non-PDF upload
  - submit without PDF
  - invalid manuscript state for submit
- `403 Forbidden`
  - current user lacks `AUTHOR`
  - current user does not own the manuscript
- `404 Not Found`
  - manuscript does not exist
  - version does not exist
  - version does not belong to manuscript
- `409 Conflict`
  - revision creation attempted outside `REVISION_REQUIRED`
  - upload or submit targets a non-current version
  - PDF overwrite attempted after submission

## Test Design

Task 4 uses Oracle-backed Spring Boot integration tests with `MockMvc` and repository assertions.

Coverage target is 8 scenarios:

1. `createDraftManuscriptPersistsAggregate`
2. `submitDraftVersionMovesStatusToSubmitted`
3. `submitDraftWithoutPdfIsRejected`
4. `createRevisionRequiresRevisionRequiredStatus`
5. `submitRevisionMovesStatusToRevisedSubmitted`
6. `uploadPdfRejectsNonPdfFile`
7. `downloadPdfReturnsStoredFileForSubmitter`
8. `onlySubmitterCanAccessManuscript`
9. `listManuscriptsAndVersionsReturnAuthorOwnedData`

The revision-submit test must explicitly seed a manuscript in `REVISION_REQUIRED` because the decision workflow that normally sets that status is implemented later.

## Verification and Documentation

Task 4 verification will include:

- `mvn -f apps/api/pom.xml -Dtest=ManuscriptServiceTest test`
- a focused rerun of any manuscript integration tests added alongside the controller
- `mvn -f apps/api/pom.xml test`
- `bash scripts/test-all.sh`

Execution results and verification evidence must be recorded in:

- `docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md`

If a separate verification note is useful after implementation, it should be written under:

- `docs/verification/`

## Risks and Controls

### Concurrent version creation

Risk:

- two requests could attempt to create a revision version at the same time and collide on `VERSION_NO`

Control:

- lock the manuscript row while computing the next version number

### Large BLOB writes

Risk:

- storing PDFs in Oracle BLOB columns may fail mid-request or amplify transaction cost

Control:

- keep upload isolated from state transitions
- keep version submission as a separate transaction

### Later workflow integration

Risk:

- later decision and review tasks may require more manuscript visibility rules

Control:

- keep Task 4 ownership checks narrow and explicit
- preserve schema fields such as `SOURCE_DECISION_ID` and all manuscript statuses even when Task 4 does not yet write them
