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
- `REVIEWER`: user submits academic profile data and remains pending until Chair/Admin approval grants `REVIEWER`.
- `ORGANIZER`: user submits organizer and planned conference data and remains pending until Admin approval grants `CHAIR`.

Admin accounts remain internal and are created by seed or privileged backend tooling.

Profile fields:

- real name, email, institution
- registration type
- approval status
- homepage, ORCID, DBLP, Google Scholar or equivalent academic identifier
- research areas
- conflict domains
- reviewer capacity defaults
- reviewed by, reviewed at, rejection reason

Approval statuses:

- `PENDING_EMAIL_VERIFICATION`
- `PENDING_APPROVAL`
- `ACTIVE`
- `REJECTED`

Implementation note: role assignment happens only after the approval rule for that registration type passes. This avoids letting pending reviewer or organizer accounts access privileged routes.

## Conference And CFP Model

Add a compact conference layer instead of a configurable venue engine.

Core entities:

- `CONFERENCE`: name, acronym, year, organizer user id, status, blind mode, CFP text, topic areas, target reviews per paper, default reviewer max load, public visibility fields.
- `CONFERENCE_PHASE`: fixed phase timestamps for submission open/close, bidding open/close, review deadline, decision release.
- `CONFERENCE_REVIEWER`: conference reviewer pool with user id, status, research-area snapshot, max load, current load, PC-member flag.
- `MANUSCRIPT.CONFERENCE_ID`: manuscript belongs to one conference.

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

- Only approved organizers/chairs can create conference drafts.
- Admin approval is required before a conference can become publicly visible.
- Authors can submit only to `OPEN_FOR_SUBMISSION` conferences.
- Existing manuscript/version/PDF validation continues to apply.
- Review rounds remain the existing review execution unit, but they become conference-scoped through the manuscript.

## Reviewer Pool, Conflicts, And Bidding

Add only the data required for guided assignment.

Reviewer pool:

- Chair/Admin can invite or approve reviewers into a conference pool.
- Reviewer membership stores max load and research-area snapshot for that conference.
- A reviewer can be globally approved but not yet part of a specific conference.

Conflict model:

- First version supports institution/domain conflicts and manual declared conflicts.
- Deterministic conflict checks block visibility during bidding and block assignment confirmation.
- Later slices may extend conflict types, but final assignment must always call the deterministic backend guard.

Bidding:

- Bidding is allowed only in `BIDDING_OPEN`.
- Reviewers see only non-conflicting manuscript title, abstract, keywords, and topic areas.
- Bid values: `EAGER`, `WILLING`, `NEUTRAL`, `NOT_WILLING`.
- Bids influence recommendations but do not override hard constraints.

## Guided Reviewer Assignment

Keep the current formal `REVIEW_ASSIGNMENT` table for confirmed assignments. Add a draft layer for proposed assignments.

New concept:

- `ASSIGNMENT_DRAFT`: stores candidate assignment plans for a review round, with source `SYSTEM`, `AGENT`, or `MANUAL`, status `DRAFT`, `CONFIRMED`, or `DISCARDED`, and an explanation payload.

Flow:

1. Chair moves the conference into `REVIEW_ASSIGNMENT`.
2. API builds eligible candidates for each manuscript from the conference reviewer pool.
3. Hard filters remove non-reviewers, inactive reviewers, conflicted reviewers, and over-capacity reviewers.
4. API computes a basic score from research-area overlap, bid value, current load, and target review count.
5. Chair sees ranked candidates and can create or edit a draft.
6. Chair may run Agent assignment assist for additional explanations and risk warnings.
7. Chair confirms a draft.
8. API re-validates conflicts, reviewer membership, reviewer status, target count, and max load.
9. API writes formal `REVIEW_ASSIGNMENT` rows and moves work into `REVIEWING`.

Hard rules stay in API/service code. Scores and Agent explanations are advisory.

## Agent Assignment Assist

Reuse the current Agent platform pattern by adding a new analysis type:

```text
REVIEWER_ASSIGNMENT_ASSIST
```

Anchor:

- First version anchors to `reviewRoundId`, because assignment is performed per review round.

API responsibilities:

- Authorize Chair/Admin for the round.
- Aggregate clean business data from the owning system.
- Create or reuse an `AnalysisIntent`.
- Publish the command through the existing outbox/RabbitMQ path.
- Store returned projection for chair-facing display.
- Never allow Agent output to bypass assignment confirmation validation.

Agent input payload:

- conference id, round id, target reviews per paper, reviewer max-load policy
- manuscript titles, abstracts, keywords, topic areas
- candidate reviewers with research areas, current load, max load, bid values
- deterministic conflict-filter results
- missing-data indicators

Agent output:

- recommended reviewer sets per manuscript
- ranked alternatives with explanation
- risk flags for candidate shortage, weak topic coverage, low willingness, load imbalance, or dense conflicts
- a proposed draft payload that Chair can copy into `ASSIGNMENT_DRAFT`

Agent output is never the source of truth. The source of truth remains the confirmed `REVIEW_ASSIGNMENT` rows after API validation.

## Frontend Experience

Keep the UI to five primary entry points:

- Public CFP: conference list and conference detail pages visible before login.
- Register: one page with Author, Reviewer, and Organizer tabs.
- Author: existing manuscript screens plus conference selection and deadline visibility.
- Organizer/Chair: a conference console for drafts, approval submission, phases, submissions, reviewer pool, bidding, assignment drafts, Agent assist, and decisions.
- Admin: one approval workbench for reviewer registrations, organizer registrations, and conference publication requests; keep existing Agent monitor.

The current role-aware shell remains. Route metadata should follow backend role helpers: Author pages require `AUTHOR`, reviewer work requires `REVIEWER`, organizer/chair work requires `CHAIR` or `ADMIN`, and admin approval work requires `ADMIN`.

## Testing And Verification Strategy

Backend:

- Registration tests for each public type and approval outcome.
- Role-grant tests proving pending reviewer/organizer accounts cannot access privileged endpoints.
- Conference lifecycle tests for allowed and rejected transitions.
- Manuscript submission tests proving authors can submit only to open conferences.
- Reviewer pool and bidding tests proving conflicted manuscripts are hidden.
- Assignment recommendation tests proving hard constraints are enforced before scoring.
- Assignment confirmation tests proving Agent output cannot create invalid assignments.
- Agent intent tests for `REVIEWER_ASSIGNMENT_ASSIST`.

Frontend:

- Registration tab tests for role-specific required fields.
- CFP list/detail tests for public visibility and author submission entry.
- Author submission tests for conference selection and closed-conference rejection feedback.
- Chair console tests for reviewer pool, bidding state, draft confirmation, and Agent assist progress.
- Admin approval workbench tests for reviewer, organizer, and conference approvals.

Schema:

- Add primary/foreign keys and indexes for conference lookup, conference status, reviewer pool lookup, bidding lookup, assignment draft lookup, and conflict checks.
- Extend Oracle verification SQL for every new table, sequence, trigger, and high-value index.

Operational docs:

- Update README, architecture, workflow, code-structure, testing, and demo docs in the implementation slice that changes the live user flow.

## Simplification Decisions

- One profile/application layer, not separate application tables for each registration type.
- One conference entity with fixed phase timestamps, not arbitrary workflow configuration.
- One conference reviewer pool, not a full PC/AC/SAC hierarchy.
- One bidding model with four values, not configurable bid scales.
- One assignment draft model for system, Agent, and manual proposals.
- One Agent type for reviewer assignment assistance, using the existing analysis platform.
- Chair confirmation remains mandatory before assignments become real.

## Open Implementation Notes

- The implementation plan should split this into small slices: registration, conference/CFP, conference-scoped submission, reviewer pool/conflicts/bidding, guided assignment, Agent assist, frontend integration, docs.
- The existing architecture-refactor stream is still active in the authoritative plan. This design should be planned as a new task group after the current active remediation/commit-separation work is resolved or explicitly paused.
