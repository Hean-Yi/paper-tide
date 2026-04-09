# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Intelligent Paper Review System (智能论文评审系统) — a course project that manages academic paper submission, double-blind peer review, and chair decisions, augmented by an async AI Agent service for automated paper analysis. The system enforces "Agent assists, humans decide" — AI provides analysis references only; formal reviews and final decisions are made by human reviewers and chairs.

## Architecture

Three decoupled services + Oracle database in a monorepo:

- **`apps/api`** — Spring Boot 3.3 (Java 21) main system. Handles auth (Spring Security + JWT), manuscript/version CRUD, review state machines, chair decisions, agent task lifecycle, notifications, and audit logging.
- **`apps/web`** — Vue 3 + Element Plus + Vite frontend. Role-based UI for Author, Reviewer, Chair, and Admin.
- **`services/agent`** — Python 3.11+ FastAPI + LangGraph agent service. Receives PDF + metadata via multipart, runs LLM-powered analysis workflows (screening, review-assist, conflict-summary), returns structured JSON with redacted variants for double-blind compliance.
- **`database/oracle`** — Oracle schema: 17 tables, 17 sequences, 18 triggers (auto-PK + audit), 3 stored procedures (stats/reporting). SQL files are numbered for execution order.

Communication: Main system pushes tasks to Agent Service via `POST /agent/tasks` (multipart), polls status via `GET /agent/tasks/{taskId}`, fetches results via `GET /agent/tasks/{taskId}/result`. Agent Service never accesses Oracle directly.

## Build and Run Commands

```bash
# Start all services (skips unavailable runtimes gracefully)
bash scripts/dev-up.sh

# Run all tests (backend + agent + web; auto-detects available toolchains)
bash scripts/test-all.sh

# Backend (Spring Boot)
cd apps/api
mvn spring-boot:run                           # start
mvn test                                       # all tests
mvn -Dtest=HealthControllerTest test           # single test class

# Agent Service (FastAPI)
cd services/agent
python3 -m uvicorn app.main:app --reload --port 8001   # start
python3 -m pytest tests/                                # all tests
python3 -m pytest tests/test_health.py -q               # single test file

# Frontend (Vue 3 + Vite)
cd apps/web
npm run dev                                    # dev server (port 5173)
npm run build                                  # production build

# Oracle schema (execute in order)
sqlplus user/pass@db @database/oracle/001_init.sql
sqlplus user/pass@db @database/oracle/002_seed_roles.sql
sqlplus user/pass@db @database/oracle/003_indexes.sql
sqlplus user/pass@db @database/oracle/004_procedures.sql
sqlplus user/pass@db @database/oracle/005_triggers.sql
sqlplus user/pass@db @database/oracle/verify_schema.sql   # validates all objects
```

## Key Design Decisions

- **Double-blind enforcement**: Agent results are stored as both `RAW_RESULT_JSON` (for chairs) and `REDACTED_RESULT_JSON` (for reviewers). Redaction strips author identity signals (self-citations, acknowledgments, institutional metadata).
- **Async agent integration**: Main system creates `AGENT_ANALYSIS_TASK`, sends PDF to Agent Service, polls with backoff, and marks tasks `FAILED` after 10-minute timeout. Agent failure never blocks the human review workflow.
- **State machines are critical**: Manuscript states (`DRAFT -> SUBMITTED -> UNDER_SCREENING -> UNDER_REVIEW -> ACCEPTED/REJECTED/REVISION_REQUIRED`) and assignment states (`ASSIGNED -> ACCEPTED -> IN_REVIEW -> SUBMITTED`) must be strictly enforced with pre-condition checks and optimistic concurrency.
- **Transactional boundaries**: Chair decisions must atomically update manuscript status, round status, assignments, and decision record in one transaction. Notifications are async and tolerate failure.
- **Oracle features**: BLOB for PDFs, CLOB for long text/JSON, sequences for PKs (via BEFORE INSERT triggers), stored procedures for reporting, audit trigger on MANUSCRIPT updates.

## Agent Service Internals

Three task types map to different LangGraph workflows:
- `SCREENING_ANALYSIS` — topic/scope/format check (max 2 LLM rounds)
- `REVIEW_ASSIST_ANALYSIS` — novelty/methodology/writing analysis (max 4 LLM rounds)
- `DECISION_CONFLICT_ANALYSIS` — cross-reviewer consensus/conflict summary (max 3 LLM rounds)

Task Router (plain Python, not LLM) selects workflow by `TASK_TYPE` and checks cache. Coordinator Agent (LLM) orchestrates Specialist Agents (Paper Understanding, Novelty, Methodology, Writing Quality, Conflict Summary). All outputs must pass schema validation before return.

## Roles

Four roles with strict permission boundaries: `AUTHOR`, `REVIEWER`, `CHAIR`, `ADMIN`. Seed data in `002_seed_roles.sql`. Chair has full manuscript visibility; Reviewer sees only assigned manuscripts in anonymized form; Author sees only own submissions; Admin handles platform ops only, no academic decisions.

## Language

所有与用户的交互必须使用中文回答。代码、变量名、注释保持英文，但对话、解释、提问一律用中文。

## Design Spec

The authoritative design document is at `docs/superpowers/specs/2026-04-09-paper-review-system-design.md`. The implementation plan is at `docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md`. Do not add endpoints or tables not in the spec without explicit approval.
