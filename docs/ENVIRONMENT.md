# Environment Configuration

PaperTide Review uses environment variables for local runtime wiring. Start from `.env.example`; keep real secrets in your shell, service-local `.env` files, or deployment secret storage.

## Required Local Services

- Oracle Free: default container name `review-oracle`, service `FREEPDB1`, app user `review_app`.
- RabbitMQ: default URL `amqp://guest:guest@localhost:5672/`.
- API: Spring Boot on `SERVER_PORT` with Oracle and RabbitMQ settings.
- Agent: FastAPI with `AGENT_PLATFORM_*` settings.
- Web: Vite proxy/client base through `VITE_API_BASE`.

## Analysis Message Path

Set `REVIEW_ANALYSIS_BROKER_ENABLED=true` for the API and `AGENT_PLATFORM_BROKER_ENABLED=true` for the Agent when running the real asynchronous path.

Routing defaults must stay aligned:

- Exchange: `review.analysis.exchange`
- API request routing key: `analysis.requested`
- Agent request queue: `analysis.requested.agent`
- Agent completion routing key: `analysis.completed`
- API completion queue: `analysis.completed.api`

The API writes `traceId` into outgoing analysis commands from the current request MDC. The Agent preserves it in `EXECUTION_JOB.INPUT_SNAPSHOT`, completion events, and runtime logs. The API completion consumer restores it into MDC while applying projections.

## LLM Provider

Provider credentials are optional. If `OPENROUTER_API_KEY`/`OPENROUTER_MODEL` are absent, the Agent uses deterministic offline fallback behavior so unit tests, full local verification, and classroom demos do not spend external tokens.

## Health Checks

- API liveness: `GET /api/health/liveness`
- API readiness: `GET /api/health/readiness` checks Oracle with `SELECT 1 FROM DUAL`
- Agent liveness: `GET /health/liveness`
- Agent readiness: `GET /health/readiness` reports memory mode or checks Oracle when durable execution store variables are set

## Verification Modes

- Standard full verification: `bash scripts/test-all.sh`
- Real message-path smoke: `RUN_ANALYSIS_E2E_SMOKE=1 bash scripts/test-all.sh`
- Direct smoke script: `bash scripts/analysis-e2e-smoke.sh`

The smoke script starts RabbitMQ, applies Oracle schema/seed data, starts API and Agent on isolated ports, triggers a demo screening analysis, and polls the admin analysis monitor until a projection becomes available.
