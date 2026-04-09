# Task 3 Authentication and Role Control Design

## Scope

Task 3 implements backend authentication and baseline authorization for the paper review system API. This task connects Spring Boot directly to the real Oracle user tables, issues JWTs for authenticated users, protects API routes, seeds demo users for local development, and verifies the flow with Oracle-backed integration tests.

This task does not implement manuscript, round, review, or decision business logic. It only establishes the authentication and authorization foundation those tasks will reuse.

## Options Considered

### Option 1: Spring Security + JWT + JdbcTemplate + Oracle integration tests

Use Spring Security for the filter chain and authorization rules, `JdbcTemplate` for minimal authentication queries against `SYS_USER`, `SYS_USER_ROLE`, and `SYS_ROLE`, and run integration tests against the real local Oracle container.

Pros:

- Matches the approved system design
- Keeps SQL in a repository boundary
- Reuses Spring Security primitives for later role and method authorization
- Validates the real Oracle integration early

Cons:

- Requires adding security, JDBC, Oracle, and JWT dependencies now

### Option 2: Spring Security + JWT + hand-written JDBC in service/config

Use Spring Security but keep SQL inline in service or security configuration classes.

Pros:

- Slightly fewer files initially

Cons:

- Weak boundaries
- Harder to extend when manuscript and review repositories arrive

### Option 3: Custom auth filter chain outside Spring Security conventions

Build login and bearer token checks mostly by hand.

Pros:

- Smallest up-front surface area

Cons:

- Fights the framework
- Makes later role control and testability worse

## Selected Approach

Use Option 1.

Task 3 will add a focused authentication slice under `com.example.review.auth`:

- `AuthController` for `POST /api/auth/login`
- `AuthService` for status and password verification
- `JwtService` for signing and parsing JWTs
- `JwtAuthenticationFilter` for bearer token restoration
- `CurrentUserPrincipal` as the authenticated principal model
- `AuthUserRepository` for Oracle-backed authentication queries
- `AuthUserRecord` as a Java `record` carrying user ID, username, password hash, status, and roles
- `SecurityConfig` for the security filter chain and authorization rules

## Data Access Design

Authentication reads from these existing Oracle tables:

- `SYS_USER`
- `SYS_USER_ROLE`
- `SYS_ROLE`

`AuthUserRepository` will perform the minimal query needed to authenticate a user and collect roles. The repository is intentionally narrow and not a shared user-management abstraction.

Rules:

- user must exist
- `SYS_USER.STATUS` must be `ACTIVE`
- password must match stored BCrypt hash
- roles come from `SYS_USER_ROLE` joined to `SYS_ROLE`

## JWT Design

JWTs use `HS256` with `jjwt`.

Claims:

- `sub`: username
- `uid`: numeric user ID
- `roles`: role code list
- `iss`: `review-api`
- `iat`
- `exp`

Policy:

- expiration is 24 hours
- no refresh token support in Task 3
- expired, malformed, or bad-signature tokens are treated as unauthenticated
- unauthenticated access returns `401`

Configuration comes from `application.yml` with environment-variable overrides:

- datasource host, port, service name, username, password
- JWT secret, issuer, expiration

The Oracle service name must be configurable through an environment variable instead of hard-coding `FREEPDB1`.

## Authorization Design

Anonymous access is allowed only for:

- `/api/auth/login`
- `/api/health`

All other `/api/**` routes require authentication.

Role-restricted routes established in Task 3:

- `/api/decisions/**`: `CHAIR` or `ADMIN`
- `/api/audit-logs/**`: `ADMIN`

Authenticated-only routes in Task 3:

- `/api/manuscripts/**`
- `/api/review-assignments/**`
- `/api/review-reports/**`
- `/api/review-rounds/**`
- `/api/notifications/**`
- `/api/agent-results/**`

Business-level visibility rules remain out of scope for this task and will be implemented in later tasks.

## Demo Seed Data

Task 3 adds a separate development/demo seed file:

- `database/oracle/006_seed_demo_users.sql`

It is intentionally separate from `002_seed_roles.sql` so schema/base data and local demo data do not share lifecycle.

Demo accounts:

- `author_demo`
- `reviewer_demo`
- `chair_demo`
- `admin_demo`
- `disabled_demo`

Rules:

- passwords are stored as precomputed BCrypt hashes
- the SQL file documents the generation method and cost factor
- `reviewer_demo` also gets at least one `USER_RESEARCH_AREA` row for later review workflow tasks

Task 3 also adds:

- `scripts/oracle-demo-seed.sh`

This script applies only the demo seed data to a running local Oracle container after the schema has already been applied.

## Test Design

Task 3 uses real Oracle-backed Spring Boot integration tests with `MockMvc`.

Coverage target is 8 scenarios:

1. valid login returns a token
2. wrong password returns `401`
3. disabled user returns `401`
4. anonymous access to a protected route returns `401`
5. authenticated access to a general protected route is accepted by security
6. non-chair roles cannot access `/api/decisions/**`
7. chair role can pass `/api/decisions/**` security
8. only admin can pass `/api/audit-logs/**` security

To avoid relying on framework-specific behavior for missing routes, Task 3 will add small placeholder controllers for protected route families used in the tests. That keeps assertions explicit:

- authenticated route returns `200`
- forbidden role returns `403`
- anonymous route returns `401`

## Verification and Documentation

Task 3 verification will include:

- Oracle container ready check
- schema already applied check
- demo seed application
- `mvn -f apps/api/pom.xml -Dtest=AuthControllerTest test`
- `mvn -f apps/api/pom.xml test`

Verification evidence will be written to:

- `docs/verification/2026-04-09-task3-auth-role-control.md`

The authoritative execution ledger remains:

- `docs/superpowers/plans/2026-04-09-paper-review-system-implementation.md`

## Risks and Controls

### Maven dependency availability

Risk:

- Oracle and JWT dependencies may not already exist in the local Maven cache

Control:

- request elevated execution if dependency download is blocked by sandbox/network restrictions

### Oracle environment drift

Risk:

- local Oracle service name, credentials, or container status may differ

Control:

- parameterize datasource settings through environment variables
- document the exact local assumptions in verification notes

### Over-scoping authorization

Risk:

- adding business-specific route restrictions too early would block later tasks

Control:

- keep Task 3 limited to authentication plus only the stable route-level role checks
