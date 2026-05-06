# Conference Onboarding And Assignment Design

Date: 2026-05-06

## Purpose

Upgrade the paper review system from a direct manuscript workflow into a simplified conference-management platform. The design covers three public registration flows, conference CFP publishing, conference-scoped submission, reviewer bidding, guided reviewer assignment, and Agent-assisted assignment recommendations.

The design intentionally avoids a full OpenReview/EasyChair-style configuration engine. It adds only the smallest platform layer needed to support a mature conference launch flow while preserving the current manuscript, review, decision, and Agent platform foundations.

## Product Gaps Against Mature Systems

Current system strengths:

- Role-aware author, reviewer, chair, and admin workflows already exist.
- Manuscript submission, PDF upload, review rounds, assignments, reports, decisions, and Agent analysis are already present.
- The Agent platform already supports business-owned analysis intents, outbox/RabbitMQ dispatch, Agent handlers, completion events, and API-side projections.

Current gaps:

- No public registration. Users are effectively pre-seeded before login.
- No conference or CFP entity. Authors submit manuscripts directly, not to a selected conference.
- No reviewer application, reviewer pool, research-area profile, reviewer bidding, or conference-scoped reviewer membership.
- Reviewer assignment is manual `reviewerId` entry by chair, without a mature candidate workflow.
- No public CFP page or platform approval flow for organizer-created conferences.

Reference comparison:

- EasyChair lists CFP, abstract/paper submission, reviewer management, preference-based assignment, review, communication, monitoring, and program publishing as core conference-management features: https://easychair.org/conference_management
- OpenReview venue workflows expose user groups, review stages, bidding, assignments, decisions, visibility, and automation around a venue timeline: https://docs.openreview.net/how-to-guides/workflow
- HotCRP supports PC review preferences, conflict checks, automatic/manual assignments, primary/secondary reviews, and assignment workflows: https://help.hotcrp.com/help/chair
- CMT documents reviewer bidding with relevance scoring and bid values such as eager, willing, in a pinch, and not willing: https://cmt3.research.microsoft.com/docs/help/reviewer/reviewer-bidding.html

## Scope

In scope:

- Three public registration types: Author, Reviewer, Organizer.
- Organizer is a registration label only. Approval grants the existing global `CHAIR` role; the design does not add a separate global `ORGANIZER` role.
- Admin-only platform administration; Admin is not publicly registered.
- Reviewer and organizer approval before privileged access.
- Conference creation, approval, CFP publishing, and fixed lifecycle states.
- Author submission to an open conference.
- Conference reviewer pool, reviewer profile data, research areas, conflict domains, and bidding.
- Guided reviewer assignment with deterministic filtering and scoring.
- New Agent analysis type `REVIEWER_ASSIGNMENT_ASSIST` for assignment recommendations and risk explanations.
- Chair confirmation before formal `REVIEW_ASSIGNMENT` rows are created.

Out of scope for this slice:

- Payments, attendee registration, proceedings, and camera-ready publication.
- Arbitrary custom submission forms.
- Full multi-track configuration engine.
- Public discussion, rebuttal, metareview, and area-chair hierarchy.
- Agent-created final assignments without chair confirmation.

## Account Registration And Approval

Reuse the existing `SYS_USER` and `SYS_ROLE` model. Add one lightweight profile/application layer rather than a separate identity system.

Public registration types:

- `AUTHOR`: email verification activates the user and grants `AUTHOR`.
- `REVIEWER`: user submits academic profile data and remains pending until Admin approval grants `REVIEWER`.
- `ORGANIZER`: user submits organizer and planned conference data and remains pending until Admin approval grants the existing global `CHAIR` role.

Admin accounts remain internal. The initial Admin should be created by a seed script after `002_seed_roles.sql`; later Admins are promoted from existing `ACTIVE` users by an existing Admin in the management console, not through public registration.

Physical ownership:

- `SYS_USER` remains the account identity table and stores login-facing identity: username, password hash, real name, email, institution, and account status.
- `USER_ACADEMIC_PROFILE` is a new 1:1 child table for academic profile data: homepage, ORCID, DBLP, Google Scholar or equivalent academic identifier, representative work metadata, conflict domains, and default reviewer capacity.
- `USER_RESEARCH_AREA` remains the platform-level research-interest table. Do not create another global research-area table.
- `ROLE_APPLICATION` stores each role-registration request and approval state: user id, registration type, approval status, submitted payload snapshot, reviewed by, reviewed at, and rejection reason. Its current-row uniqueness is `(USER_ID, REGISTRATION_TYPE)`, not only `USER_ID`, so one account can pursue multiple roles independently.
- `EMAIL_VERIFICATION_TOKEN` stores hashed verification tokens, user id, purpose, expiry, consumed timestamp, and created metadata. Raw tokens are sent only through the email adapter and are not stored.

Multi-role rule:

- One `SYS_USER` may hold multiple global roles, such as `AUTHOR`, `REVIEWER`, and `CHAIR`.
- Each public registration path has an independent `ROLE_APPLICATION`.
- The first version keeps one current application row per `(USER_ID, REGISTRATION_TYPE)`; rejected applications may be updated and resubmitted through that row. Full application history can be added later only if audit requirements demand it.

Reviewer approval baseline:

- Admin approval should require at least one verifiable academic profile URL or identifier.
- Reviewer applications should include recent representative work metadata, such as up to five recent publication titles or profile-sourced publication evidence.
- Rejection must store a reason so applicants and future Admin reviews are auditable.

Approval statuses:

- `PENDING_EMAIL_VERIFICATION`
- `PENDING_APPROVAL`
- `ACTIVE`
- `REJECTED`

Implementation note: role assignment happens only after the approval rule for that registration type passes. This avoids letting pending reviewer or organizer accounts access privileged routes.

Abuse controls:

- Email verification tokens expire after 24 hours and are single-use.
- Public registration and email-verification endpoints need rate limiting.
- Optional institution-domain allowlists or blocklists may be configured for reviewer and organizer approval, but they are advisory unless an implementation slice explicitly makes them mandatory.

Email channel:

- Task 25.1 includes the minimal email-delivery foundation required by registration verification and approval results.
- Add an email service boundary with templates for verification, approval, rejection, and reviewer invitation messages.
- Production configuration should use SMTP settings from environment variables.
- Local development and ordinary automated tests use a deterministic fake/logging adapter that records the verification link instead of sending real mail.
- `SYS_NOTIFICATION` remains the in-app notification table; it does not replace email verification because unverified users cannot rely on in-app delivery.

## Conference And CFP Model

Add a compact conference layer instead of a configurable venue engine.

Core entities:

- `CONFERENCE`: name, acronym, year, organizer user id, status, blind mode, CFP text, topic areas, target reviews per paper, default reviewer max load, public visibility fields.
- `CONFERENCE_PHASE`: fixed phase timestamps for submission open/close, bidding open/close, review deadline, decision release.
- `CONFERENCE_REVIEWER`: conference reviewer pool with user id, status, research-area snapshot, max load, PC-member flag. The research-area snapshot is copied from `USER_RESEARCH_AREA` into `RESEARCH_AREAS_JSON` or equivalent conference-scoped columns when the reviewer joins the conference. Current load is derived from `REVIEW_ASSIGNMENT` counts at read time, not stored as a mutable counter.
- `MANUSCRIPT.CONFERENCE_ID`: manuscript belongs to one conference for new submissions.

Migration strategy:

- Add `database/oracle/010_conference_onboarding.sql` after the existing `009_execution_job_attempt_count.sql`.
- Add `MANUSCRIPT.CONFERENCE_ID` as nullable first to avoid breaking existing seed data and historical manuscripts.
- Create a `Legacy / Platform Default` conference and backfill existing manuscripts to it in the migration.
- After backfill and seed updates are stable, a later migration may tighten `MANUSCRIPT.CONFERENCE_ID` to `NOT NULL`.

Blind mode ownership:

- `CONFERENCE.BLIND_MODE` is the authority for new submissions.
- `MANUSCRIPT.BLIND_MODE` remains as a denormalized snapshot copied from the conference at submission creation time, preserving existing workflow code and historical decisions.
- Review and reviewer visibility checks should use the manuscript snapshot for the submitted version being reviewed.

Conference lifecycle:

```text
DRAFT
-> PENDING_APPROVAL
-> OPEN_FOR_SUBMISSION
-> SUBMISSION_CLOSED
-> BIDDING_OPEN
-> REVIEW_ASSIGNMENT
-> REVIEWING
-> DECISION
-> CLOSED
```

Rules:

- Only approved `CHAIR` users can create conference drafts.
- Admin approval is required before a conference can become publicly visible.
- Authors can submit only to `OPEN_FOR_SUBMISSION` conferences.
- Existing manuscript/version/PDF validation continues to apply.
- Review rounds remain the existing review execution unit, but they become conference-scoped through the manuscript.
- Conference status transitions are triggered explicitly by Chair/Admin actions and must be idempotent. Phase timestamps are used for display and boundary validation; for example, the API rejects new submissions after `submission_close_at` even if the status has not yet been manually advanced.

Phase boundary checks:

- `submission_close_at`: hard rejection for new initial submissions and PDF replacement on submitted versions after the close time.
- `bidding_close_at`: hard rejection for new or changed bids after the close time; chairs may still view existing bids.
- `review_deadline_at`: warning and overdue/progress signal, not a hard rejection. Reviewers may still submit late unless the assignment was cancelled or reassigned.
- `decision_release_at`: controls when author-facing decision visibility opens. Chairs/Admins may record decisions earlier, but author decision endpoints should hide unreleased decisions until this timestamp or an explicit release action.

## Reviewer Pool, Conflicts, And Bidding

Add only the data required for guided assignment.

Reviewer pool:

- Reviewer registration approval creates membership in the platform reviewer pool only.
- Chair/Admin can invite or approve platform-approved reviewers into a specific conference pool.
- Reviewer membership stores max load and a conference-scoped research-area snapshot copied from `USER_RESEARCH_AREA`.
- A reviewer can be globally approved but not yet part of a specific conference.

Conflict model:

- First version supports institution/domain conflicts and manual declared conflicts.
- Reuse `CONFLICT_CHECK_RECORD` rather than adding a parallel conflict table. The `010_conference_onboarding.sql` migration should extend it for pre-assignment use by allowing `ASSIGNMENT_ID` to be nullable and by expanding supported `CONFLICT_TYPE` values such as `INSTITUTION`, `DECLARED`, and `SELF_CITATION`.
- Assignment-created conflict checks still populate `ASSIGNMENT_ID`; bidding and recommendation visibility checks may record manuscript/reviewer conflicts before an assignment exists.
- Deterministic conflict checks block visibility during bidding and block assignment confirmation.
- Later slices may extend conflict types, but final assignment must always call the deterministic backend guard.

Bidding:

- Bidding is allowed only in `BIDDING_OPEN`.
- Reviewers see only non-conflicting, de-identified manuscript title, abstract, keywords, and topic areas.
- Bidding previews must follow the same double-blind redaction policy as reviewer-facing paper access: no author names, institutions, acknowledgements, identifying links, or raw PDF download. If redacted metadata is not available, the API must generate or store a redacted metadata snapshot before exposing the paper for bidding.
- Bid values: `EAGER`, `WILLING`, `NEUTRAL`, `NOT_WILLING`.
- Bids influence recommendations but do not override hard constraints.

## Guided Reviewer Assignment

Keep the current formal `REVIEW_ASSIGNMENT` table for confirmed assignments. Add a draft layer for proposed assignments.

New concept:

- `ASSIGNMENT_DRAFT`: one row per proposed `(ROUND_ID, REVIEWER_ID)` pair, similar to an unconfirmed `REVIEW_ASSIGNMENT`.
- Draft rows include source `SYSTEM`, `AGENT`, or `MANUAL`, status `DRAFT`, `CONFIRMED`, or `DISCARDED`, rank/score fields, and an explanation payload.
- Agent output may produce a full suggested set, but the API normalizes it into per-reviewer draft rows so chairs can edit, discard, or confirm individual candidates without replacing an opaque JSON bundle.

Flow:

1. Chair moves the conference into `REVIEW_ASSIGNMENT`.
2. API builds eligible candidates for the target manuscript from the conference reviewer pool.
3. Hard filters remove non-reviewers, inactive reviewers, conflicted reviewers, and over-capacity reviewers.
4. API computes a basic score from research-area overlap, bid value, current load, and target review count.
5. Chair sees ranked candidates and can create or edit a draft.
6. Chair may run Agent assignment assist for additional explanations and risk warnings.
7. Chair confirms a draft.
8. API re-validates conflicts, reviewer membership, reviewer status, target count, and max load.
9. API writes formal `REVIEW_ASSIGNMENT` rows and moves work into `REVIEWING`.

Confirmation transaction:

- Confirmation locks the selected `CONFERENCE_REVIEWER` rows with `SELECT ... FOR UPDATE`.
- The service recomputes each selected reviewer's current load from confirmed `REVIEW_ASSIGNMENT` rows inside the transaction.
- If any reviewer would exceed max load, or if a conflict was created after draft generation, confirmation fails without writing partial assignments.
- Hard rules stay in API/service code. Scores and Agent explanations are advisory.

## Agent Assignment Assist

Reuse the current Agent platform pattern by adding a new analysis type:

```text
REVIEWER_ASSIGNMENT_ASSIST
```

Anchor:

- First version anchors to the target manuscript version (`manuscriptId + versionId`) and includes `roundId` in the request payload for authorization and workflow context.
- One assist request produces reviewer recommendations for one manuscript only. It does not perform cross-conference or all-submissions batch optimization.

API responsibilities:

- Authorize Chair/Admin for the round.
- Aggregate clean business data from the owning system.
- Create or reuse an `AnalysisIntent`.
- Publish the command through the existing outbox/RabbitMQ path.
- Store returned projection for chair-facing display.
- Never allow Agent output to bypass assignment confirmation validation.

Agent input payload:

- conference id, manuscript id, version id, round id, target reviews per paper, reviewer max-load policy
- manuscript title, abstract, keywords, topic areas
- candidate reviewers with research areas, current load, max load, bid values
- deterministic conflict-filter results
- missing-data indicators

Agent output:

- recommended reviewer set for the manuscript
- ranked alternatives with explanation
- risk flags for candidate shortage, weak topic coverage, low willingness, load imbalance, or dense conflicts
- a proposed draft payload that Chair can copy into `ASSIGNMENT_DRAFT`

Agent output is never the source of truth. The source of truth remains the confirmed `REVIEW_ASSIGNMENT` rows after API validation.

## Frontend Experience

Keep the UI to five primary entry points:

- Public CFP: conference list and conference detail pages visible before login.
- Register: one page with Author, Reviewer, and Organizer tabs.
- Author: existing manuscript screens plus conference selection and deadline visibility.
- Chair: a conference console for drafts, approval submission, phases, submissions, reviewer pool, bidding, assignment drafts, Agent assist, and decisions. The public registration tab may be labeled Organizer, but the approved role is `CHAIR`.
- Admin: one approval workbench for reviewer registrations, organizer registrations, and conference publication requests; keep existing Agent monitor.

The current role-aware shell remains. Route metadata should follow backend role helpers: Author pages require `AUTHOR`, reviewer work requires `REVIEWER`, Chair conference-console work requires `CHAIR` or `ADMIN`, and admin approval work requires `ADMIN`.

## Testing And Verification Strategy

Backend:

- Registration tests for each public type and approval outcome.
- Role-grant tests proving pending reviewer/organizer accounts cannot access privileged endpoints.
- Multi-role tests proving one user can independently hold `AUTHOR`, `REVIEWER`, and `CHAIR` through separate applications.
- Profile persistence tests proving account identity, academic profile data, platform research areas, and approval metadata land in the intended tables.
- Email verification tests using the fake/logging adapter for token expiry and single-use behavior.
- Conference lifecycle tests for allowed and rejected transitions.
- Phase-boundary tests for submission close, bidding close, review deadline warning behavior, and decision release visibility.
- Manuscript submission tests proving authors can submit only to open conferences.
- Reviewer pool and bidding tests proving conflicted manuscripts are hidden.
- Multi-conference isolation tests proving a reviewer for conference A cannot see conference B manuscripts in bidding or assignment views.
- Assignment recommendation tests proving hard constraints are enforced before scoring.
- Assignment confirmation tests proving Agent output cannot create invalid assignments, including concurrent confirmation attempts that would exceed reviewer max load.
- Agent intent tests for `REVIEWER_ASSIGNMENT_ASSIST`.
- Registration abuse-control tests for token expiry, single-use verification, and endpoint rate-limit hooks where implemented.

Frontend:

- Registration tab tests for role-specific required fields.
- CFP list/detail tests for public visibility and author submission entry.
- Author submission tests for conference selection and closed-conference rejection feedback.
- Chair console tests for reviewer pool, bidding state, draft confirmation, and Agent assist progress.
- Admin approval workbench tests for reviewer, organizer, and conference approvals.

Schema:

- Add primary/foreign keys and indexes for `USER_ACADEMIC_PROFILE`, `ROLE_APPLICATION`, `EMAIL_VERIFICATION_TOKEN`, conference lookup, conference status, reviewer pool lookup, bidding lookup, assignment draft lookup, and conflict checks.
- Extend Oracle verification SQL for every new table, sequence, trigger, and high-value index.

Operational docs:

- Update README, architecture, workflow, code-structure, testing, and demo docs in the implementation slice that changes the live user flow.

## Simplification Decisions

- One profile/application layer, not separate application tables for each registration type.
- Account identity, academic profile data, platform research areas, approval metadata, and verification tokens have separate physical homes: `SYS_USER`, `USER_ACADEMIC_PROFILE`, `USER_RESEARCH_AREA`, `ROLE_APPLICATION`, and `EMAIL_VERIFICATION_TOKEN`.
- Organizer is not a separate global role. It is the public registration path that grants the existing `CHAIR` role after Admin approval.
- A single `SYS_USER` may hold multiple global roles through independent role applications.
- One conference entity with fixed phase timestamps, not arbitrary workflow configuration.
- One conference reviewer pool, not a full PC/AC/SAC hierarchy.
- Reviewer global approval is Admin-owned; conference-specific reviewer membership is Chair/Admin-owned inside that conference.
- One bidding model with four values, not configurable bid scales.
- One assignment draft model for system, Agent, and manual proposals, stored as one candidate reviewer row per draft assignment.
- One Agent type for reviewer assignment assistance, using the existing analysis platform.
- Chair confirmation remains mandatory before assignments become real.

## Open Implementation Notes

- The implementation plan should split this into small slices: registration, academic profile, role applications, email foundation and abuse controls, conference/CFP schema and lifecycle, conference-scoped submission and legacy migration, reviewer pool/conflicts/bidding, guided assignment, Agent assist, frontend integration, docs.
- The existing architecture-refactor stream is still active in the authoritative plan. This design should be planned as a new task group after the current active remediation/commit-separation work is resolved or explicitly paused.
