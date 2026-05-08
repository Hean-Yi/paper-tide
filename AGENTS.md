# Repository Guidelines

## Project Structure & Module Organization
This monorepo contains a paper review system. `apps/api` holds the Spring Boot API (`src/main/java`) and JUnit tests (`src/test/java`). `apps/web` holds the Vue 3 + Vite frontend in `src/`. `services/agent` holds the FastAPI service in `app/` and pytest tests in `tests/`. Oracle schema files live in `database/oracle`; helper scripts live in `scripts/`. Treat `apps/web/node_modules`, `apps/web/dist`, `apps/api/target`, and Python cache directories as generated output.

## Build, Test, and Development Commands
Run the full local stack with `bash scripts/dev-up.sh`; it starts whichever runtimes are installed. Run full verification with `bash scripts/test-all.sh`.

- `cd apps/api && mvn spring-boot:run` starts the API.
- `cd apps/api && mvn test` runs backend tests.
- `cd services/agent && python3 -m uvicorn app.main:app --reload --port 8001` starts the agent service.
- `cd services/agent && python3 -m pytest tests/` runs agent tests.
- `cd apps/web && npm run dev` starts the frontend.
- `cd apps/web && npm run test -- --run && npm run typecheck && npm run build` verifies the web app.

## Coding Style & Naming Conventions
Follow existing language conventions: 4 spaces in Python and Java, 2 spaces in Vue templates, JSON, and CSS. Use `PascalCase` for Vue components and Java classes, `snake_case` for Python modules and test files, and descriptive test names such as `test_health_endpoint_returns_ok`. Keep Java packages under `com.example.review`. SQL migration files should stay numbered, for example `003_indexes.sql`.

No repo-wide formatter or linter config is committed yet, so rely on framework defaults and keep imports tidy. Always run `npm run typecheck` for frontend changes and the relevant test command for API or agent changes before opening a PR.

## Testing Guidelines
Frontend tests use Vitest (`*.spec.ts`), API tests use JUnit 5 (`*Test.java`), and agent tests use pytest (`test_*.py`). Add or update tests in the same service you change. Prefer small endpoint-focused tests for the current scaffold.

## Commit & Pull Request Guidelines
Git history is not available in this workspace snapshot, so use imperative commit messages with a scope when helpful, such as `feat(web): add reviewer dashboard` or `fix(agent): validate task payload`. Keep pull requests narrow. Include a short summary, the commands you ran, linked issues, and screenshots for visible frontend changes.

## Security & Configuration Tips
Do not commit real Oracle credentials, tokens, or `.env` files. Review `scripts/oracle-up.sh` before changing default passwords for local containers. When modifying database scripts, preserve execution order and re-run `database/oracle/verify_schema.sql`.

## Review Feedback Learning Loop
When review feedback identifies an execution mistake or a project design problem, treat it as reusable repository knowledge instead of a one-off fix.

Required rule:
- Every feedback-driven fix that falls into either of these categories must also be written back into `AGENTS.md` in the same work cycle:
- `Execution error`: wrong dependency setup, incomplete verification, broken script assumptions, missing ignore rules, incorrect runtime/bootstrap behavior, or other implementation/process mistakes.
- `Project design problem`: schema naming hazards, missing baseline test scaffolding, mismatches between selected stack and scaffold, missing high-value indexes, or other architectural/design issues that can recur.

Working rule:
- Do not stop at fixing code only. Update `AGENTS.md` with the generalized lesson before considering the feedback fully handled.
- Write the lesson as a stable rule or heuristic, not as a one-time incident report.
- Prefer rules that help future tasks avoid the same class of mistake.

Current lessons captured from recent review cycles:
- Add a root `.gitignore` early in scaffold work so generated files such as `.DS_Store`, `node_modules`, `dist`, `target`, `.m2`, `.venv`, and Python caches cannot drift back into the workspace.
- Extend the root `.gitignore` early enough to cover local tool staging and packaging byproducts too, especially `.skill-staging/` and `*.egg-info`, so repository initialization and first commits do not capture machine-local metadata.
- When the frontend stack is already chosen, the scaffold must reflect it early: use TypeScript entrypoints and include baseline dependencies and verification hooks for `Vitest`, `Element Plus`, and `vue-router` instead of leaving them implicit for later.
- For Oracle schema design, avoid quoted reserved identifiers in persistent columns. Prefer explicit business-safe names such as `FEEDBACK_COMMENT` over `"COMMENT"` to reduce friction in Java/MyBatis and future SQL work.
- Add high-value lookup indexes as soon as the access pattern is obvious, especially version-scoped result lookups such as `AGENT_ANALYSIS_RESULT (MANUSCRIPT_ID, VERSION_ID)`.
- Verification scripts should test the real selected toolchain when dependencies are present. For the frontend, that means `Vitest + vue-tsc + vite build`, not syntax-only checks once the project has moved past pure scaffold stage.
- On newer local JDKs where Mockito inline attachment can fail, prefer pinning Spring Boot tests to the subclass mock maker unless the task truly requires inline mocking; otherwise red-green verification can fail for toolchain reasons instead of product behavior.
- In Spring Security, avoid permissive catch-all rules. Keep API allow/auth rules explicit and use `anyRequest().denyAll()` so future non-`/api/**` endpoints cannot become public by accident.
- For manuscript PDF uploads, validate cheap metadata before reading the body and validate file magic after reading: enforce the configured size cap and require `%PDF-` bytes instead of trusting filename or content type alone.
- If backend integration tests depend on seeded Oracle auth data, make the verification script apply the demo seed before API tests so repository-level verification matches the task's real execution assumptions.
- When a task spec is revised during review, update the authoritative plan file's concrete task steps in the same work cycle so the execution ledger stays aligned with the approved design instead of drifting behind it.
- For actor-facing workflow tasks, include the minimal collection/list endpoint needed to render that actor's primary screen in the same backend slice; do not stop at detail endpoints if the UI cannot navigate without a "my items" query.
- In service-layer error mapping, catch only the specific client-input exceptions you intend to translate to `4xx`. Do not wrap persistence or infrastructure failures in broad catches that misreport server faults as bad requests.
- When Oracle-backed integration tests span workflow tables with foreign-key chains, clean seeded data in dependency order inside test setup or teardown, deleting child workflow rows before parent manuscript/version rows so later task slices do not break existing test isolation. In particular, null `MANUSCRIPT_VERSION.SOURCE_DECISION_ID` before deleting `DECISION_RECORD`, and delete `DECISION_RECORD` before deleting `REVIEW_ROUND`.
- When a workflow task intentionally skips an intermediate state from the full state machine, record that deferral explicitly in the authoritative plan or spec in the same cycle so later tasks know which transition still needs a dedicated entry point.
- When adding an actor-facing workflow action that introduces or exposes an intermediate state, verify the next service-layer guard in the intended path accepts that state; UI-visible paths must not stop at a state that blocks the next required action.
- Keep frontend route role metadata aligned with backend authorization helpers. When a backend endpoint allows a role set such as chair-or-admin, add a route-guard test for every frontend page that calls that endpoint so the UI does not block a valid backend actor.
- For notifications in core workflow transactions, keep the notification call behind a separate service boundary and treat delivery as best-effort. Notification persistence failures must not roll back the primary business transaction unless the task explicitly requires synchronous guarantees.
- For live external-cost agent execution, prefer the smallest necessary safety controls before adding workflow complexity: service authentication, deterministic input-aware de-duplication, a simple concurrency cap, and explicit deferral of durable queues, retries, and provider failover unless the task requires them.
- Agent task de-duplication must not pin callers to a failed cached task. Reuse pending/processing/success tasks for idempotency and concurrency control, but create a fresh task when the cached matching task is `FAILED` unless the task spec explicitly requires failure replay.
- Frontend workflow actions that can close, decline, reject, or otherwise irreversibly move review state need an explicit confirmation step, and PDF preview/download helpers must revoke object URLs after opening to avoid browser memory leaks during long demos.
- Actor-facing workflow list actions such as upload, download, submit, and revision creation must surface backend failures explicitly in the UI. Do not leave table-row actions as bare awaited calls; wrap them with the shared API error presenter and cover at least one failure path in frontend tests so invalid state transitions do not look like no-ops.
- On bleeding-edge Node runtimes, do not assume the built-in browser-like globals are complete enough for Vitest/jsdom. If auth or router tests rely on `localStorage`, provide an explicit test setup storage shim so Node experimental globals cannot break deterministic frontend verification.
- For agent-service tool designs, keep business data aggregation on the owning system side and normalize transport metadata at the route boundary. Workflow nodes should consume clean payloads, and helper-style tools should stay deterministic unless a task explicitly requires autonomous tool calling.
- When the main system mirrors agent-service task caching, keep `force` and terminal-status reuse semantics aligned on both sides and cover intentional divergences with tests. Remove assertions that are not wired to the system under test rather than keeping always-true checks.
- When replacing scaffold UI with real application behavior, update or delete the original scaffold smoke tests in the same task plan. Do not leave tests asserting bootstrap placeholder text after the root component becomes a router shell or authenticated entry point.
- Avoid module-level creation of frontend app infrastructure such as Vue Router instances. Export factories from reusable modules and instantiate them in the application entrypoint so imports stay side-effect-light and future SSR/test isolation remains feasible.
- Keep role checks centralized through `RoleGuard` once it exists. Do not reintroduce service-local `requireAuthor` / `requireReviewer` helpers unless the rule is genuinely domain-specific rather than a plain role check.
- If two workflow services need the same Oracle row lock, put the `SELECT ... FOR UPDATE` query in the owning repository and expose a small shared row shape; avoid duplicating lock SQL inside individual services.
- For Element Plus workflow forms, pair `el-form` rules with explicit submit-time guards for required fields that protect the API call path. Tests should prove invalid forms do not issue fetch requests, not only that validation text renders.
- Reviewer paper access must be assignment-scoped and rendered for online reading. Do not broaden the original manuscript PDF download endpoint to reviewers; instead guard rendered-page endpoints by reviewer ownership and assignment state, and test that the raw PDF endpoint stays forbidden for reviewers.
- Reviewer-facing Agent assist must be assignment-scoped, fixed to the reviewer-assist task type, and checklist-only. Do not allow callers to choose arbitrary agent task types or expose `rawResult`, scores, recommendations, or complete review text to reviewer UI/API responses.
- Actor-facing asynchronous agent actions must show immediate submitted/progress feedback, poll or otherwise refresh until a terminal state is visible, and surface failed states with an explicit Retry path. Do not leave successful request acceptance looking like a no-op while background analysis runs.
- Runtime schedulers that mirror manually testable workflow actions should have explicit delay configuration, bounded repository queries, and a public service method such as `pollOnce()` for deterministic tests.
- When idempotency keys must cover nested request payloads, canonicalize nested maps recursively before hashing so semantically identical map/list structures produce the same key regardless of input key order.
- For refactors that introduce queue/outbox/inbox tables, add the foreign-key indexes and the status/timestamp polling indexes in the same change, and verify them in schema checks rather than leaving them implicit.
- RabbitMQ bootstrap scripts used by dev/test flows must wait for broker readiness and fail clearly on Docker or startup errors; do not wrap them in `|| true` once they become part of the verification path.
- When a runtime is optional in developer workflows, make the mode explicit in the bootstrap helper and have `dev-up.sh`/`test-all.sh` call the optional path so one missing local service does not abort the rest of the stack.
- Optional bootstrap helpers should preflight the runtime engine and skip cleanly when the CLI exists but the engine is unreachable; only the required path should fail fast on that condition.
- Container bootstrap helpers for local brokers should not rely on `docker run --rm` plus immediate readiness polling. Keep the container available for restart/log inspection, read state via `docker inspect`, and give first boot a short grace period before declaring startup failure.
- In Oracle verification SQL, keep `IN (...)` lists syntactically closed and avoid trailing commas in object-name audits so schema checks remain executable.
- When business analysis identity may need more than one durable key, model the composite anchor explicitly in the domain and schema instead of collapsing it into a single overloaded anchor ID and pushing the missing semantics into payload hashing.
- If `UPDATED_AT` participates in ordering, polling, or indexed lookup semantics, maintain it through a trigger or an equally enforced persistence contract; do not leave it as an insert-only default that later writers must remember manually.
- Frozen dataclasses do not make durable state immutable when nested JSON-like payloads remain mutable. For job or outbox snapshots, freeze nested mappings and sequences recursively and expose mutable copies only through explicit helper methods.
- Typed construction helpers such as idempotency-key factories must validate enum and value-object compatibility at creation time so semantically invalid combinations fail fast instead of producing durable but nonsensical identities.
- Cross-service outbox messages must publish a stable command envelope, not a naked business payload. Include routing/identity fields such as `idempotencyKey`, `analysisType`, `intentReference`, and nested `requestPayload` so consumers do not infer transport metadata from business data.
- `force` semantics in intent-based agent flows must allocate a new durable request identity instead of toggling a fixed alternate key. Compute the next request version from existing intents so repeated forced reruns do not collapse onto the same cached work.
- When moving an execution state machine from memory into durable storage, persist every state-transition field that affects retry or terminal-state decisions in the schema and cover it in schema verification in the same slice. Do not leave values such as `attemptCount` as in-memory-only state once repositories become database-backed.
- In Oracle-backed test suites that still coexist with legacy agent tables, keep cleanup helpers aligned with the full foreign-key graph even after migrated assertions stop reading those tables. Delete legacy agent children such as `AGENT_FEEDBACK`, `AGENT_ANALYSIS_RESULT`, and `AGENT_ANALYSIS_TASK` before `REVIEW_ROUND` so seeded data does not break unrelated workflow tests.
- For Oracle `JdbcTemplate` queries that compare nullable numeric keys, do not rely on untyped vararg `null` values in predicates such as `? IS NULL`. Use explicit typed binding via `PreparedStatement` setters so Oracle does not fail with `ORA-17004`.
- Do not mark an analysis-flow migration complete when only the request/intake path has been moved. Completion requires the paired execution result path too: handler execution, completion-event consumption, intent status update, projection persistence, and a focused test that proves a request can reach `AVAILABLE` without relying on the removed legacy task infrastructure.
- When removing a legacy actor-facing monitor or workflow page during a refactor, ship a minimal replacement read model and UI in the same slice or explicitly defer the removal in the plan. Do not leave long-lived placeholder routes for admin or operator workflows after retiring the original screen.
- When a transport or ownership refactor changes the live integration path, update the operational docs in the same slice. Keep `README.md`, `docs/ARCHITECTURE.md`, `docs/CODE_STRUCTURE.md`, `docs/TESTING.md`, and bootstrap instructions aligned with the current runtime path; do not leave legacy HTTP/task-flow descriptions as if they were still authoritative.
- Message-driven refactors are not complete when only schema, repositories, and in-process handlers exist. Completion requires broker lifecycle wiring, pending-message dispatch, completion-event intake, and a repository-level verification command that exercises more than `/health`.
- When wiring live LLM providers from `.env`, keep unit tests deterministic by injecting an offline provider executor or fake client. Do not let ordinary pytest runs consume local API keys or require network access; reserve real provider calls for explicit smoke tests.
- When upload size is enforced in service logic, keep the framework/container multipart ceiling aligned in the same slice and surface the same limit in the UI before upload. Do not leave Spring or proxy defaults below the business rule, or users will hit generic `413 Payload Too Large` errors before application validation runs.
- Shared frontend API helpers must tolerate successful empty response bodies for mutation endpoints such as upload actions that return `ResponseEntity<Void>`. Do not unconditionally call `response.json()` on every `2xx` response, or the UI will surface client-side parse errors after successful backend writes.
- When adding conference-management concepts to the existing review workflow, keep public registration labels separate from durable authorization roles. If the database already has `CHAIR`, do not introduce an `ORGANIZER` role implicitly; either map organizer approval to `CHAIR` explicitly or model conference-scoped roles deliberately.
- Conference-scoped migrations must state how existing platform manuscripts are backfilled before tightening new foreign keys. Add new Oracle migration files in numeric order, use a legacy/default conference when needed, and clarify whether new columns start nullable.
- For conference submission settings such as blind mode, define one authoritative source and treat copied values on manuscripts as immutable snapshots for the submitted version; do not leave conference-level and manuscript-level fields competing at review time.
- Reviewer bidding and assignment-assist previews must preserve double-blind constraints. Expose only de-identified metadata to reviewers and Agent payloads unless the workflow explicitly permits open review.
- Agent-assisted workflow features should use the smallest correct business anchor. If the existing review round is per manuscript, assignment assist should recommend reviewers for one manuscript/version at a time rather than implying a conference-wide batch optimizer without a batch entity.
- Do not store mutable reviewer load counters unless there is an enforced persistence contract. Prefer deriving load from confirmed `REVIEW_ASSIGNMENT` rows during recommendation and display.
- Keep account identity, academic profile data, research interests, approval metadata, and email verification tokens in distinct physical homes. In this project that means `SYS_USER` for login identity, `USER_ACADEMIC_PROFILE` for scholarly profile fields, `USER_RESEARCH_AREA` for platform-level interests, `ROLE_APPLICATION` for registration/approval workflow state, and `EMAIL_VERIFICATION_TOKEN` for hashed single-use verification tokens.
- Public registration workflows that require verification must define an email delivery boundary in the same design slice. Use deterministic fake/logging adapters for tests and local development, and do not treat in-app notifications as a substitute for email verification.
- Assignment draft models must state row granularity before implementation. Prefer one row per proposed reviewer/manuscript assignment when chairs need per-candidate editing, and normalize Agent bundle output into that draft shape.
- When confirming reviewer assignments from drafts, lock the selected conference-reviewer rows inside the transaction and recompute load from confirmed assignments before insert so concurrent chair actions cannot over-assign a reviewer.
- Phase timestamp semantics must identify which API actions hard-reject after deadlines and which only warn. Do not leave submission, bidding, review, and decision-release boundary behavior implicit.
- When a page-level query repeatedly filters, counts, or joins by a column set, add the matching composite indexes in the same slice and include those index names in `verify_schema.sql`. Do not rely on ad hoc repository queries without schema verification coverage.
- For existing local Oracle bootstrap paths, every new migration file must be wired into both full schema apply and incremental dev bootstrap. A migration that only exists on disk but is not called by `oracle-schema-apply.sh` or `dev-up.sh` is not operationally complete.
- Replace actor-facing N+1 read services with page-shaped read repositories before adding more indexes. Indexes help hot predicates, but they do not fix per-row query loops in workflow workbenches.
- Legacy table retirement must remove all active schema, seed, procedure, trigger, index, verification, and test-cleanup dependencies in the same slice. Keep exactly one idempotent drop migration for existing databases; do not leave tests deleting retired tables as hidden compatibility dependencies.
- Before adding lookup indexes in Oracle migrations, check whether a primary key or unique constraint already creates the needed index. Do not add same-column duplicate indexes; use the extra index only when it supports a distinct access pattern such as status polling or child-row cleanup.
- Once an Oracle migration has been committed or may have been applied locally, do not add new DDL to that same migration as the only delivery path. Put follow-up DDL in the next numbered migration and wire it into both full apply and incremental bootstrap, otherwise existing databases can skip the new objects while verification expects them.
- Registration resubmission paths must update or upsert durable profile rows that are keyed by user. Do not insert-only into one-to-one profile tables such as `USER_ACADEMIC_PROFILE` when rejected applications can be reopened, or a valid retry can become a server error.
- When running real Oracle migration verification on an existing local database, do not assume earlier incremental migrations were already applied. Run `verify_schema.sql`, inspect missing object classes, and apply missing prior migrations in numeric order before treating the latest migration as failed.
- Direct test seeds for manuscripts must respect the current-version foreign-key order used by the application: insert `MANUSCRIPT` with `CURRENT_VERSION_ID = NULL`, insert `MANUSCRIPT_VERSION`, then update `MANUSCRIPT.CURRENT_VERSION_ID`. Do not insert a manuscript pointing at a version row that does not exist yet.
- Oracle test cleanup must include assignment planning children before review rounds. Delete `ASSIGNMENT_DRAFT` after `REVIEW_ASSIGNMENT` and before `REVIEW_ROUND` so assignment-planning slices do not break older workflow tests.
- Oracle test cleanup that deletes manuscripts must also delete conference workflow children first, especially `REVIEWER_BID`, before `MANUSCRIPT`; E2E bidding flows can otherwise leave FK children that break unrelated manuscript, workflow, or decision tests.
- Oracle test cleanup that deletes conferences must delete `CONFERENCE_REVIEWER` before `CONFERENCE`, and cleanup that deletes `ANALYSIS_INTENT` must delete `ANALYSIS_OUTBOX` first. Keep cleanup helpers aligned with new FK children introduced by later workflow slices.
- Adding a new analysis type requires synchronized changes across both systems: API enum/anchor policy/use case, Oracle analysis constraints in a new migration, outbox payload construction, Agent schema/provider/handler registry/redaction, and focused API plus Agent tests. Do not update only one side of the split-sovereignty analysis flow.
- Reviewer-assignment assist should consume persisted assignment drafts as advisory candidate input and return ranked guidance only. Keep final assignment confirmation in the API transaction that locks conference-reviewer rows and revalidates load.
- Frontend workflow logic must use the backend's durable status vocabulary exactly. Assignment drafts are `PROPOSED` until confirmation; do not substitute generic labels such as `DRAFT` in filters or button enabling logic.
- E2E tests that seed Oracle directly must use physical schema names from migrations, not DTO names. For example, seed `CONFERENCE.CONFERENCE_YEAR` and `ASSIGNMENT_DRAFT.ASSIGNMENT_DRAFT_ID`, then assert DTO aliases through API responses.
- Agent provider input budgeting must cover both long strings and large lists. When adding an Agent analysis that accepts user- or workflow-sized collections, cap collection length and long per-item rationale fields before building the LLM prompt, and add a test proving extreme payloads stay bounded.
- Git index operations such as `git add`, `git commit`, and other commands that write `.git/index` must be run sequentially, not through parallel tool calls, because concurrent index writes can create transient lock failures and interrupt the commit path.
- When retiring schema objects, update active design documentation in the same slice. Docs must not describe retired tables as possibly present in active migrations or test cleanup after the retirement contract says only the drop migration and guard tests may mention them.
- Incremental Oracle migrations that create several independent objects must be idempotent as a group, and local bootstrap detection must check every object in that group rather than a single representative object, so partially applied migrations can self-heal.
- Request-level trace IDs are not complete until asynchronous command and event envelopes carry them too. When adding observability to message-driven flows, propagate the trace through API outbox payloads, broker consumers, Agent input snapshots, completion events, and completion consumers.
- Retiring a parallel Agent execution stack must remove runtime imports, tests, dependencies, and active docs in the same slice. Move any still-used schema/helper code into the retained stack first, then delete the obsolete modules so future work has exactly one execution path to reason about.
- Execution-job governance fields that affect retry, terminal-state diagnosis, or operator polling belong in a new numbered migration with schema verification and incremental bootstrap checks. Do not retrofit already-applied execution migrations as the only delivery path.
- CI dependency setup must follow the committed package metadata. For the Agent service, install from `services/agent/pyproject.toml` with the `test` extra instead of assuming a `requirements.txt` exists.
- When changing schema verification object counts, update every guard test that asserts those exact verification strings in the same slice. Otherwise full API tests can fail even when the migration itself is wired correctly.
- Spring AMQP integration must declare a JSON message converter for cross-language Agent payloads. Do not rely on the default `RabbitTemplate` converter for API-to-Agent messages, because Java serialization is unreadable by the Python consumer.
- Real broker smoke scripts should use isolated exchange, queue, and routing-key names or cleanly reset their own topology. Do not assume a long-lived local RabbitMQ container has the expected exchange type from a previous development cycle.
- Business-flow reviews must distinguish demo-complete workflows from real-deployment readiness. For paper review platforms, explicitly check conference-scoped roles, phase/deadline enforcement, COI and blind-review governance, author-facing decision packages, reviewer discussion/meta-review, camera-ready publication, and operational compensation actions.
- When a business-flow review identifies real-platform gaps, update `TODO.md` with prioritized backlog items and mark stale completed items in the same cycle so planning reflects both product readiness and current implementation state.
- When landing a minimum viable slice of a broad real-platform workflow, mark the backlog item as complete only for the explicit landed scope and state the remaining product depth in the same TODO line. Do not let "minimal closure" erase follow-up needs such as full area-chair hierarchies, external mail delivery, publication exports, or data lifecycle automation.
- Direct workflow actions must enforce the same eligibility policy as assisted/draft workflows. If assignment drafts filter by membership, conflicts, bids, and load, direct assignment and reassignment must call an equivalent repository check before inserting rows.
- If a security matcher narrows a previously broad protected path for a new actor-facing read endpoint, add a guard test that asserts matcher ordering. Broad rules such as `/api/decisions/**` can silently shadow more specific author-safe reads unless order is covered.
- Oracle tests that delete `ANALYSIS_INTENT` must first clear the bidirectional execution link and delete both API-side analysis children and Agent-side execution children. Use the order: null `ANALYSIS_INTENT.EXECUTION_JOB_ID`, delete `EXECUTION_INBOX`, `EXECUTION_OUTBOX`, `EXECUTION_ARTIFACT`, `EXECUTION_ATTEMPT`, `EXECUTION_JOB`, then `ANALYSIS_OUTBOX`, `ANALYSIS_INBOX`, `ANALYSIS_PROJECTION`, and `ANALYSIS_INTENT`.
- Oracle tests must not assume migration backfill rows such as legacy conference `CONFERENCE_ID = 0` still exist after other suites have cleaned data. Direct seeds that reference backfill parents must merge the required parent rows in setup or seed their own conference.
- Best-effort audit or communication writes called from a primary business transaction must not use a nested transactional proxy that can mark the outer transaction rollback-only after a caught persistence failure.
- Time-sensitive positive-path tests must not use near-term absolute dates as future deadlines. Use relative future timestamps or far-future fixtures, and keep expired-deadline behavior isolated in dedicated negative tests.
- Schema guard tests for older migrations must verify that their objects remain wired, not assert stale global object totals after later migrations add tables, sequences, triggers, or indexes. Keep exact total-count assertions in the newest verification slice only.
- When adding new manuscript-, assignment-, conference-, or form-scoped FK children, update every Oracle integration-test cleanup helper in the same slice. Delete new children before existing parent workflow rows so full-suite order does not depend on which test ran first.
- Oracle test cleanup for bidirectional or parent-held active pointers must null the pointer before deleting the pointed child rows. For example, clear `EMAIL_TEMPLATE.ACTIVE_VERSION_ID` before deleting `EMAIL_TEMPLATE_VERSION`.
- When closing broad real-platform waves, do not redefine completion as a smallest usable loop after the user asks for full business coverage. Keep the authoritative plan explicit about excluded production-provider work, and ship page-shaped read models plus actor-facing UI for every new write workflow before marking the wave complete.
- Large workflow services and repositories must not keep absorbing unrelated wave behavior. When a service or repository becomes a cross-wave catch-all, split new work by business boundary and add page-shaped read repositories/services for operator screens instead of appending more ad hoc methods.

## Plan File Discipline
Execution work must stay anchored to one authoritative implementation plan file.

Required rule:
- Before starting any implementation task, first open the current plan markdown file and confirm which task is active.
- Do not start coding, schema changes, environment work, or verification until the active task has been identified from the plan file.
- After each task execution or meaningful sub-step, write the result back into the same plan file, including:
- what was executed
- what changed
- what verification was run
- the current completion state

Single-plan rule:
- Maintain exactly one authoritative plan markdown file for implementation tracking in this repository.
- The current authoritative plan file is `docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md`.
- Do not create additional plan-tracking markdown files for the same implementation stream unless the user explicitly asks to replace the current one.
- If a new plan file is ever intentionally introduced, it must replace the old one as the sole authoritative plan, and `AGENTS.md` must be updated to point at the new path in the same change.

Working rule:
- Treat the plan file as the execution ledger: read before work, update after work.
- Keep status updates concise but factual so later readers can reconstruct what was done without reading terminal logs.
- Avoid parallel plan documents because they cause stale reads, duplicated status, and task-order confusion.
