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
- If backend integration tests depend on seeded Oracle auth data, make the verification script apply the demo seed before API tests so repository-level verification matches the task's real execution assumptions.
- When a task spec is revised during review, update the authoritative plan file's concrete task steps in the same work cycle so the execution ledger stays aligned with the approved design instead of drifting behind it.
- For actor-facing workflow tasks, include the minimal collection/list endpoint needed to render that actor's primary screen in the same backend slice; do not stop at detail endpoints if the UI cannot navigate without a "my items" query.
- In service-layer error mapping, catch only the specific client-input exceptions you intend to translate to `4xx`. Do not wrap persistence or infrastructure failures in broad catches that misreport server faults as bad requests.
- When Oracle-backed integration tests span workflow tables with foreign-key chains, clean seeded data in dependency order inside test setup or teardown, deleting child workflow rows before parent manuscript/version rows so later task slices do not break existing test isolation.
- When a workflow task intentionally skips an intermediate state from the full state machine, record that deferral explicitly in the authoritative plan or spec in the same cycle so later tasks know which transition still needs a dedicated entry point.
- When adding an actor-facing workflow action that introduces or exposes an intermediate state, verify the next service-layer guard in the intended path accepts that state; UI-visible paths must not stop at a state that blocks the next required action.
- Keep frontend route role metadata aligned with backend authorization helpers. When a backend endpoint allows a role set such as chair-or-admin, add a route-guard test for every frontend page that calls that endpoint so the UI does not block a valid backend actor.
- For notifications in core workflow transactions, keep the notification call behind a separate service boundary and treat delivery as best-effort. Notification persistence failures must not roll back the primary business transaction unless the task explicitly requires synchronous guarantees.
- For live external-cost agent execution, prefer the smallest necessary safety controls before adding workflow complexity: service authentication, deterministic input-aware de-duplication, a simple concurrency cap, and explicit deferral of durable queues, retries, and provider failover unless the task requires them.
- For agent-service tool designs, keep business data aggregation on the owning system side and normalize transport metadata at the route boundary. Workflow nodes should consume clean payloads, and helper-style tools should stay deterministic unless a task explicitly requires autonomous tool calling.
- When the main system mirrors agent-service task caching, keep `force` and terminal-status reuse semantics aligned on both sides and cover intentional divergences with tests. Remove assertions that are not wired to the system under test rather than keeping always-true checks.
- When replacing scaffold UI with real application behavior, update or delete the original scaffold smoke tests in the same task plan. Do not leave tests asserting bootstrap placeholder text after the root component becomes a router shell or authenticated entry point.
- Avoid module-level creation of frontend app infrastructure such as Vue Router instances. Export factories from reusable modules and instantiate them in the application entrypoint so imports stay side-effect-light and future SSR/test isolation remains feasible.

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
