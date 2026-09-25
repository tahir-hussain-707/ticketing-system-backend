# Phase 0 Research: Authentication, Auto-Assignment & Scoped Ticket Access

Technical Context has no unresolved `NEEDS CLARIFICATION` — the stack is already fixed by the
constitution and by 001/002's own research. The real decisions here are how to add
authentication/authorization onto that stack with the least new complexity, and how to implement
the auto-assignment and ownership rules correctly.

## Authentication mechanism

- **Decision**: `spring-boot-starter-security`, with a custom `POST /api/v1/auth/login` JSON
  endpoint that authenticates via `AuthenticationManager` and saves the resulting
  `SecurityContext` into the servlet `HttpSession` (Spring Security's default
  `HttpSessionSecurityContextRepository`), plus a `POST /api/v1/auth/logout` endpoint that
  invalidates it. Passwords are hashed with `BCryptPasswordEncoder`.
- **Rationale**: Satisfies FR-001–FR-004/FR-017 with one dependency already in the Spring Boot
  BOM (no version to pick, no new library family). Session-based auth is the simplest mechanism
  that meets every stated requirement — nothing in the spec calls for statelessness, mobile
  clients, or third-party identity federation.
- **Alternatives considered**: JWT (stateless bearer tokens) — rejected; requires an additional
  signing-key management decision and a token library, solving a horizontal-scalability problem
  the spec never raises (constitution Principle V: don't introduce complexity without a named
  problem). OAuth2/OIDC — rejected; the constitution previously removed its OAuth2
  authentication rule by explicit amendment, and reintroducing a third-party identity provider is
  well beyond what points 1–4 of the feature request ask for.

## Session storage

- **Decision**: The default in-process servlet `HttpSession` (Tomcat's built-in session store).
- **Rationale**: Simplest option satisfying the spec; spec.md's Assumptions explicitly defer
  session lifetime/expiry as a planning decision, not a scale requirement, and nothing in scope
  requires the service to run as more than the single deployable it already is.
- **Alternatives considered**: Spring Session backed by PostgreSQL/JDBC (durable, multi-instance
  sessions) — rejected for now; would be the correct upgrade if/when this service is horizontally
  scaled, but that need is not established by this feature and would be unjustified complexity
  today per constitution Principle V. Revisit if a future plan introduces multiple instances.

## Endpoint access rules

- **Decision**: An explicit `permitAll` allow-list — `POST /api/v1/auth/login`,
  `GET /actuator/health`, `GET /actuator/info`, the OpenAPI/Swagger UI paths, and the two
  endpoints this feature does not touch (`GET /api/v1/tickets/{id}`,
  `GET /api/v1/tickets/{id}/comments`, unchanged from 001/002) — with `anyRequest().authenticated()`
  as the fallback for everything else, including `/api/v1/chatbot/**` even though that controller
  does not exist yet (spec 003 is not implemented; this reserves the path so the chatbot is
  gated the moment it ships, per FR-007, without this plan needing to touch spec 003's code).
- **Rationale**: A default-deny fallback is the safer default once any authentication exists at
  all — leaving unlisted write endpoints (ticket update, status transition) implicitly open would
  be a real gap the moment `User`-based ownership exists, even though spec 004 doesn't name every
  one of them individually. Matching only what's explicitly named would under-secure the API;
  matching everything unnamed is the smaller, safer surface.
- **Alternatives considered**: Gating only the exact endpoints spec 004's FRs name (ticket
  creation, comments, chatbot) and leaving everything else open by default — rejected as an
  accidental security hole for ticket update/transition, which become meaningless to protect
  individually once `Ticket.createdBy`/`assignee` exist as real identities.
- **Explicit scope boundary**: `GET /api/v1/tickets/{id}` and `GET /api/v1/tickets/{id}/comments`
  keep their existing unauthenticated access from 001/002 — spec 004 never asks to gate ticket or
  comment *retrieval*, only creation, chatbot access, and the ownership-scoped *list*. Tightening
  single-ticket/comment retrieval further is a future decision, not silently added here.

## Authorization model

- **Decision**: Role checks that are purely role-based (e.g. "must be `ADMIN`" for manual
  reassignment) are Spring Security matcher/`@PreAuthorize` rules. Checks that depend on
  *which* ticket is involved (comment authorship: creator-or-assignee) are enforced in
  `CommentService`, not as a security-layer rule, since they need the specific ticket's data.
- **Rationale**: Constitution Principle II: "business rules that validation annotations cannot
  express MUST be enforced in the domain layer." A generic role check cannot express "this
  specific user created or is assigned to this specific ticket" — that has to be a domain-layer
  query-and-compare.

## Data model: assignee and comment authorship become real identities

- **Decision**: `tickets.assignee` (free-text `VARCHAR`) is replaced with `assignee_id` (nullable
  UUID FK to `users`); `tickets` gains `created_by_id` (UUID FK to `users`, not null);
  `comments` gains `author_id` (UUID FK to `users`, not null).
- **Rationale**: Direct foreign keys are what constitution Principle IV requires ("foreign keys
  on all relationships") and are the only way to enforce FR-008/FR-009 (assignee must be a real
  `SUPPORT` user, not arbitrary text) and FR-012/FR-013 (comment authorship must be a real,
  named user) at the database level, not just in application code.
- **Alternatives considered**: Keeping `assignee` as text and adding a separate
  `SUPPORT`-user-lookup table joined by name — rejected; string-matched identity is exactly the
  free-form assumption spec 004 explicitly supersedes from 001.
- **Migration note**: Both new not-null foreign keys (`created_by_id`, `author_id`) are added
  directly as `NOT NULL` (no backfill step). This assumes no ticket/comment rows predate this
  feature in any environment the migration runs against — reasonable for this still-early-stage
  project (only 001/002 have shipped). If a populated environment ever needs this migration, a
  backfill step must precede it; that is out of scope here since no such environment exists yet.

## Auto-assignment algorithm

- **Decision**: `UserRepository` exposes a query equivalent to: among `role = 'SUPPORT'` users,
  count each one's tickets where `assignee_id = user.id AND status IN ('OPEN','IN_PROGRESS')`,
  order by that count ascending then by `id` ascending, and take the first row. That same query
  (minus the limit) backs `TicketService.create()`'s assignment step inside the ticket's own
  `@Transactional` block.
- **Rationale**: Directly implements FR-009 (workload = `OPEN` + `IN_PROGRESS` tickets, per the
  `/speckit-clarify` answer) and FR-010 (tie-break by a stable, repeatable order — ascending user
  ID is deterministic and requires no extra column). A composite index on
  `tickets (assignee_id, status)` keeps the aggregate cheap at the spec's target scale.
- **Concurrency trade-off**: Two tickets created in true concurrent transactions could both read
  the same "currently least-loaded" `SUPPORT` user before either commits, both landing on that
  user. This is accepted as a soft load-balancing heuristic, not a correctness invariant — no
  ticket is ever left in an invalid state, workload self-corrects as more tickets arrive, and
  SC-002's "at the moment of creation" wording is verified by sequential test scenarios, not
  simultaneous ones. Adding row-level or advisory locking to make this perfectly atomic under
  concurrency is unjustified complexity per constitution Principle V for a best-effort
  distribution rule; revisit only if real skew is observed in production.
- **Alternatives considered**: `SELECT ... FOR UPDATE` across all `SUPPORT` users before
  assigning — rejected per the trade-off above; round-robin instead of least-loaded — rejected,
  contradicts FR-009's explicit "fewest tickets" rule.

## Manual reassignment

- **Decision**: A dedicated `PATCH /api/v1/tickets/{ticketId}/assignee` endpoint, `ADMIN`-role
  only, taking `{ assigneeId }`. The general `PATCH /api/v1/tickets/{ticketId}` endpoint's request
  body no longer accepts an `assignee` field at all (removed from `TicketUpdateRequest`).
- **Rationale**: FR-008 already forbids a caller-supplied assignee at creation; FR-016 makes
  `ADMIN` reassignment the *only* legal way to change an assignee afterward. Keeping `assignee` in
  the general update DTO would let any caller who can update a ticket's title also silently
  change its assignee, contradicting FR-016. A separate endpoint makes the `ADMIN`-only rule a
  single matcher/authorization check instead of a conditional inside the general update path.
- **Alternatives considered**: Keeping `assignee` in `TicketUpdateRequest` but rejecting it
  unless the caller is `ADMIN` — rejected; conflates two different authorization models (anyone
  who owns/can-touch-a-ticket vs. `ADMIN`-only) in one endpoint and one request body.

## Listing scope semantics

- **Decision**: `GET /api/v1/tickets` gains a `scope` query parameter (`mine`, `assigned`, `all`;
  default: `all`). `all` always means "every ticket the requesting user is permitted to see," not
  literally every row — for `SUPPORT`/`ADMIN` that is every ticket; for `GENERAL` it is narrowed
  to tickets they created (since a `GENERAL` user is never an assignee). `assigned` for a
  `GENERAL` user returns an empty page (not an error), consistent with 001's existing "no match
  is not an error" pattern.
- **Rationale**: Matches FR-014 and spec.md's Assumptions exactly ("all tickets the requesting
  user is permitted to see"); keeps the query string small and role-agnostic (the caller never
  has to know their own role to ask for "all") while still respecting `GENERAL` visibility limits
  server-side.
- **Alternatives considered**: Rejecting `scope=all` for `GENERAL` users with a 403 — rejected;
  the spec frames "all" as permission-bounded, not as a request only certain roles may make, and
  a 403 on the default scope value would be a worse experience for no added security (the result
  set is identical to `scope=mine` for that role either way).

## Seed users

- **Decision**: Demo/seed `User` rows (one each of `ADMIN`, `SUPPORT`, `GENERAL`) are created by
  a `@Profile("dev")` `CommandLineRunner`, not a Flyway migration.
- **Rationale**: Constitution Principle V forbids committing secrets "in any form, including test
  fixtures and example config." A Flyway migration runs identically in every environment,
  including a future production one; a dev-only seeder never executes there. Real
  production/staging account provisioning is explicitly out of scope (FR-005) and left to the
  future onboarding feature.
- **Alternatives considered**: A Flyway seed migration with a clearly-fake password — rejected;
  even a "fake" credential shipped in a migration that runs in every environment is exactly the
  pattern Principle V forbids, and it would need a follow-up migration to remove later.

## CORS and CSRF

- **Decision**: `CorsConfig` is updated to `allowCredentials(true)` (required for the browser to
  send/receive the session cookie cross-origin) — origins must stay an explicit allow-list
  (already true today; `allowCredentials(true)` combined with a wildcard origin is rejected by
  browsers anyway). CSRF protection is disabled for this API (`SecurityConfig`), since it is a
  JSON-only API with no server-rendered form submissions; the session cookie's `SameSite`
  attribute plus the existing strict CORS origin allow-list are the mitigation for this
  simplification.
- **Rationale**: Matches how the existing separate frontend already calls this API (JSON over
  `fetch`/XHR, not HTML forms), and avoids adding CSRF-token issuance/verification plumbing the
  spec never asks for.
- **Alternatives considered**: Double-submit CSRF token — rejected as unjustified complexity for
  a same-origin-enforced, non-form JSON API; revisit if a browser form-based client is ever added.

## Outcome

No unresolved `NEEDS CLARIFICATION` markers. Ready for Phase 1.
