# Task 10 Frontend Authentication and Shell Design

## Goal

Implement the first usable Vue frontend slice: login against the existing Spring Boot JWT endpoint, persist the authenticated session, protect app routes, and render a role-aware application shell for AUTHOR, REVIEWER, CHAIR, and ADMIN users.

Task 10 must stop at authentication and navigation shell. Workflow screens, manuscript forms, reviewer editors, chair decision workbenches, agent monitors, and agent feedback remain Task 11 or later work.

## Current Context

The frontend is still a minimal Vue 3 scaffold:

- `apps/web/src/main.ts` mounts `App.vue` with Element Plus.
- `apps/web/src/App.vue` renders scaffold text.
- `vue-router`, Element Plus, Vitest, Vue Test Utils, and TypeScript are already installed.
- There is no Pinia dependency.

The backend auth contract already exists:

- `POST /api/auth/login`
- Request: `{ "username": "...", "password": "..." }`
- Response: `{ "token": "..." }`
- JWT claims include `sub`, `uid`, `roles`, `iat`, and `exp`.
- Protected API calls require `Authorization: Bearer <token>`.

## Previous Task Audit

Task 1 through Task 9 have no unchecked implementation steps before Task 10 in the authoritative plan. The remaining items are explicit deferrals, not missed Task 9 requirements:

- The explicit screening-start entry point remains deferred from the backend workflow and should be considered when Task 11 implements chair screening screens.
- Agent feedback endpoints remain deferred out of Task 9 and should be handled as a later feature slice, not in Task 10.
- Durable agent queues, retries, and provider failover remain intentionally deferred.

Task 9 completion is represented by:

- `44f9481 feat: integrate main system with agent service`
- `2209067 fix: align agent task reuse semantics`

## Design Options

### Option 1: Lightweight Reactive Auth Store

Use a small local TypeScript module under `apps/web/src/stores/auth.ts` with Vue `reactive` state and exported actions. Decode the JWT locally to derive username, user id, roles, and expiration. Store only the token in `localStorage`.

Pros:

- No new dependency.
- Matches current scaffold size.
- Keeps Task 10 focused.
- Easy to test with Vitest.

Cons:

- If frontend state grows substantially, a later move to Pinia may be useful.

### Option 2: Add Pinia Now

Add Pinia and implement auth as a Pinia store.

Pros:

- Familiar store pattern for larger Vue apps.
- Scales well for Task 11 workflow data.

Cons:

- Adds a dependency before the project has enough frontend state to justify it.
- Requires plugin setup and test wrappers for a small initial slice.

### Option 3: Router-Only Auth State

Keep token state inside router guards and `App.vue`.

Pros:

- Smallest initial code footprint.

Cons:

- Harder to test and reuse.
- API calls, route guards, and shell rendering would duplicate auth parsing.

## Chosen Approach

Use Option 1: a lightweight reactive auth store.

This follows the current frontend scaffold without adding dependencies. The store provides a clear migration path to Pinia later because callers will already use named actions and computed session data instead of reaching into `localStorage` directly.

## Components and Files

### `apps/web/src/lib/api.ts`

Small fetch wrapper for API calls:

- `apiRequest(path, options)`
- `login(username, password)`
- Prepends a base URL.
- Sends `Content-Type: application/json` for JSON requests.
- Adds `Authorization` when a token exists.
- Converts non-2xx responses into typed errors with `status` and message.

Base URL rules:

- Use `import.meta.env.VITE_API_BASE_URL` when set.
- Default to `/api`.
- Add a Vite dev proxy from `/api` to `http://localhost:8080` so local frontend dev can call the Spring Boot API without CORS assumptions.

### `apps/web/src/stores/auth.ts`

Lightweight auth module:

- `authState`
- `initializeAuth()`
- `login(username, password)`
- `logout()`
- `isAuthenticated`
- `hasRole(role)`
- `primaryRole`

JWT handling:

- Decode JWT payload client-side for display and role routing.
- Treat malformed or expired tokens as logged out.
- Persist only the raw token in `localStorage` under a stable key such as `review.auth.token`.
- Do not trust decoded roles for backend security. Backend role checks remain authoritative.

### `apps/web/src/router/index.ts`

Create browser routes:

- `/login`: public login screen.
- `/`: redirects to `/dashboard` when authenticated, otherwise `/login`.
- `/dashboard`: protected role landing screen inside `AppShell`.
- Future role navigation entries can point to `/dashboard` anchors until Task 11 adds real screens.

Route metadata:

- `requiresAuth: true`
- optional `roles: ["ADMIN"]` for admin-only future routes

Guard behavior:

- Anonymous users visiting protected routes are redirected to `/login`.
- Authenticated users visiting `/login` are redirected to `/dashboard`.
- Expired or malformed stored tokens are cleared before navigation.

### `apps/web/src/layouts/AppShell.vue`

Authenticated layout:

- Header with product name and current username.
- Role-aware navigation links:
  - author: manuscripts
  - reviewer: review assignments
  - chair: screening and decisions
  - admin: audit and agent monitor
- Logout action.
- Main content slot via `RouterView`.

Navigation initially links to dashboard anchors because Task 11 owns workflow screens. The shell must not pretend unavailable workflow actions are complete.

### `apps/web/src/views/LoginView.vue`

The first screen for anonymous users:

- Username field.
- Password field.
- Submit button.
- Inline invalid-credentials message for `401`.
- Generic service-unavailable message for network or `5xx` failures.
- Redirect to `/dashboard` after successful login.

Use Element Plus form controls, but keep validation simple:

- username required
- password required

### `apps/web/src/views/DashboardView.vue`

Minimal authenticated landing view:

- Shows the user's role-specific entry points.
- Does not implement workflow behavior.
- Gives Task 11 stable places to replace with real screens.

### `apps/web/src/App.spec.ts`

Replace the scaffold-title assertion. After Task 10, `App.vue` becomes a router outlet and must no longer render "Monorepo scaffold is running." The updated test should mount the app with the router and assert that an anonymous visit lands on the login view.

## Data Flow

1. App startup calls `initializeAuth()`.
2. `initializeAuth()` reads the stored token, decodes claims, and checks expiration.
3. Router guard decides whether the route can load.
4. Login form calls `auth.login(username, password)`.
5. `auth.login` calls `POST /api/auth/login`, stores the token, decodes claims, and redirects.
6. API wrapper uses the current token for future authenticated calls.
7. Logout clears token and routes back to `/login`.

## Error Handling

- Empty username/password stays client-side and shows field-level validation.
- `401` from login shows an invalid credentials message.
- Network failures and `5xx` show a generic service message.
- Expired tokens are cleared and route to `/login`.
- Role-restricted routes show a not-authorized state or redirect to `/dashboard`; Task 10 should prefer redirect to keep the slice small.

## Security Notes

- The frontend may decode the JWT to drive display and routing, but that is not security enforcement.
- Do not store passwords.
- Do not store decoded user objects separately from the token.
- Do not commit real credentials or environment files.
- Backend authorization remains the source of truth.

## Testing Strategy

Use Vitest and Vue Test Utils. Mock `fetch` and `localStorage`.

Required tests:

- Existing `App.spec.ts` is rewritten from scaffold-title rendering to app/router auth entry behavior.
- Anonymous users visiting a protected route are redirected to `/login`.
- Successful login posts to `/api/auth/login`, stores the token, and routes to `/dashboard`.
- Invalid login shows an error and does not store a token.
- Existing valid token restores the session on startup.
- Expired or malformed token is cleared.
- Shell navigation renders links appropriate to the user's roles.
- Logout clears token and returns to `/login`.

Verification commands:

- `npm --prefix apps/web run test -- --run`
- `npm --prefix apps/web run typecheck`
- `npm --prefix apps/web run build`

## Acceptance Criteria

- Anonymous users see login first, not scaffold text.
- Authenticated users see an application shell with role-aware navigation.
- Token persistence survives a page reload until expiration.
- All API calls made through the frontend helper include the bearer token when authenticated.
- The implementation does not add Pinia or new UI dependencies.
- Task 11 workflow screens are not implemented early.
- Existing web verification commands pass.
