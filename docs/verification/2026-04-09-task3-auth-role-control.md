# Task 3 Verification: Authentication and Role Control

## Scope

This note records the verification performed for Task 3 on 2026-04-09.

Verified areas:

- Oracle-backed login against `SYS_USER` / `SYS_USER_ROLE` / `SYS_ROLE`
- JWT route protection and role gating
- demo seed application
- API regression coverage
- repository verification script behavior after the API auth changes

## Environment Assumptions

- local Oracle container name: `review-oracle`
- Oracle service: `FREEPDB1`
- Oracle app user: `review_app`
- Oracle app password: `ReviewApp12345`
- Maven local repository for this workspace: `/Users/hean/Agent_proj/.m2/repository`

## Demo Accounts Seeded

Password for all enabled demo users: `demo123`

- `author_demo`
- `reviewer_demo`
- `chair_demo`
- `admin_demo`
- `disabled_demo`

## Commands Run

### 1. Apply Task 3 demo seed

```bash
bash scripts/oracle-demo-seed.sh
```

Observed result:

- `5 rows merged.` for `SYS_USER`
- `5 rows merged.` or `0 rows merged.` for `SYS_USER_ROLE` on repeat runs
- `1 row merged.` for `USER_RESEARCH_AREA`
- `Commit complete.`

### 2. Run focused auth integration tests

```bash
mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository -Dtest=AuthControllerTest test
```

Observed result:

- `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

Covered scenarios:

1. valid login returns a token
2. wrong password returns `401`
3. disabled user returns `401`
4. anonymous manuscript access returns `401`
5. authenticated manuscript access returns `200`
6. non-chair decision access returns `403`
7. chair decision access returns `200`
8. admin-only audit log access behaves as expected

### 3. Run API regression suite

```bash
mvn -f apps/api/pom.xml -Dmaven.repo.local=/Users/hean/Agent_proj/.m2/repository test
```

Observed result:

- `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`
- includes `AuthControllerTest`
- includes `HealthControllerTest`
- `BUILD SUCCESS`

### 4. Run repository verification script

```bash
bash scripts/test-all.sh
```

Observed result:

- API verification passed
- agent verification passed
- web verification passed
- Vite build completed successfully
- web build emitted an existing chunk-size warning for the frontend bundle, but the build still succeeded

## Execution Notes

- The initial red-test run first exposed a Java 25 + Mockito inline attachment problem instead of the intended auth failure. This was corrected by adding `apps/api/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` with `mock-maker-subclass`, after which the tests failed for the expected missing-route reasons.
- Maven dependency download required network access beyond the sandbox.
- Oracle-backed test execution and repository verification also required local Oracle socket access beyond the sandbox.

## Result

Task 3 verification passed for the implemented scope.
