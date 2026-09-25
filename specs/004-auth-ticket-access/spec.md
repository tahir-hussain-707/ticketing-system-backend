# Feature Specification: Authentication, Auto-Assignment & Scoped Ticket Access

**Feature Branch**: `004-auth-ticket-access`

**Created**: 2026-09-22

**Status**: Superseded — merged into
[`specs/005-auth-rag-chatbot/spec.md`](../005-auth-rag-chatbot/spec.md) on 2026-09-22, along with
`specs/003-rag-resolution-chatbot`. This file, and its plan.md/tasks.md (written before the
merge, including a security matcher for a chatbot endpoint that was never built), are kept for
history only; do not plan or implement against them directly.

**Input**: User description: "1. There should be login mechanism in the application using email
and password. The system should have the capability to store the user information (name, email,
password) and roles (SUPPORT, GENERAL, ADMIN). 2. Only logged in user should be able to create
tickets or talk to chatbot. However, only the user with SUPPORT role can be assigned with
tickets. The assignment should not be manual. The system should identify the SUPPORT user who
has least number of tickets in progress and then should assign the ticket to them. 3. Comment
can be added by either the user who has created the ticket or the user who is assigned with the
ticket. The comment should carry the comment creator name with it. 4. Ticket listing API should
have the capability to bring only the tickets which were created with the user or are assigned
to the user or all."

## Clarifications

### Session 2026-09-22

- Q: Should the system lock out or slow down repeated failed login attempts against one
  account? → A: No lockout in this feature — deferred to the infrastructure/gateway layer, same
  as the chatbot's abuse protection (spec 003).
- Q: What minimum password strength should the system enforce? → A: None — user account
  creation/update is out of scope for this feature (see FR-005/Assumptions), so no
  password-strength rule is defined here.
- Q: Does this feature need an explicit logout action? → A: Yes — a user can explicitly end
  their own authenticated session on demand.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Log In to Access the System (Priority: P1)

A person logs in with their email and password before they can create tickets or use the
chatbot. Anyone who is not logged in is turned away from those actions.

**Why this priority**: Every other capability in this feature — and in the two features it
extends (ticket creation, the chatbot) — depends on knowing who is making the request. Without
login, none of the role-based rules below have anyone to apply to.

**Independent Test**: Attempt to create a ticket or query the chatbot without logging in and
confirm both are rejected. Log in with valid credentials and confirm both now succeed. Log in
with an incorrect password and confirm it is rejected without granting access.

**Acceptance Scenarios**:

1. **Given** a registered user with valid credentials, **When** they submit their email and
   password to log in, **Then** they are authenticated and can access the actions this feature
   gates (ticket creation, chatbot).
2. **Given** a registered user, **When** they submit an incorrect password, **Then** the login is
   rejected with a clear error and no access is granted.
3. **Given** no email is registered to a submitted address, **When** a login is attempted with
   it, **Then** the login is rejected with the same generic invalid-credentials error used for a
   wrong password (so the response does not reveal whether the email exists).
4. **Given** no one is logged in, **When** a request to create a ticket is submitted, **Then** it
   is rejected and the ticket is not created.
5. **Given** no one is logged in, **When** a request is submitted to the chatbot, **Then** it is
   rejected and no chatbot response is generated.
6. **Given** a logged-in user of any role (`SUPPORT`, `GENERAL`, `ADMIN`), **When** they create a
   ticket or query the chatbot, **Then** the request proceeds (subject to the other rules in this
   spec).
7. **Given** a logged-in user, **When** they explicitly log out, **Then** their authenticated
   context ends and any subsequent request they make to create a ticket or query the chatbot is
   rejected as unauthenticated (Scenarios 4–5), until they log in again.

---

### User Story 2 - Tickets Are Auto-Assigned to the Least-Busy Support User (Priority: P1)

When a ticket is created, the system picks which `SUPPORT`-role user should handle it — nobody
chooses manually. It picks whichever `SUPPORT` user currently has the fewest tickets on their
plate, so work stays balanced.

**Why this priority**: This is the core workload-distribution rule the request calls for, and it
changes how every new ticket behaves from creation onward — it cannot be deferred behind other
stories without leaving tickets unassigned.

**Independent Test**: Create several `SUPPORT`-role users with differing existing ticket loads.
Create a new ticket and confirm it is assigned to the `SUPPORT` user with the smallest load, with
no assignee supplied by the caller.

**Acceptance Scenarios**:

1. **Given** multiple `SUPPORT` users exist with different numbers of tickets currently on their
   plate, **When** a new ticket is created, **Then** it is automatically assigned to the
   `SUPPORT` user with the fewest.
2. **Given** two or more `SUPPORT` users are tied for the fewest tickets, **When** a new ticket
   is created, **Then** exactly one of the tied users is assigned, chosen by a consistent,
   repeatable rule (not arbitrarily).
3. **Given** a ticket-creation request supplies an assignee, **When** the ticket is created,
   **Then** the supplied value is ignored and the system's own assignment choice is used instead.
4. **Given** an assigned ticket later moves through its status lifecycle, **When** its status
   changes, **Then** that change is reflected in the assigned `SUPPORT` user's counted workload
   for future assignment decisions.
5. **Given** a ticket has already been auto-assigned, **When** an `ADMIN` user manually
   reassigns it to a different `SUPPORT` user, **Then** the reassignment succeeds and the ticket's
   assignee changes accordingly.
6. **Given** a ticket has already been auto-assigned, **When** a non-`ADMIN` user attempts to
   manually reassign it, **Then** the request is rejected with a clear authorization error and
   the assignee is unchanged.

---

### User Story 3 - Only the Ticket's Creator or Assignee Can Comment, and Is Named (Priority: P2)

A user can add a comment to a ticket only if they created it or are the `SUPPORT` user it is
assigned to. Every comment shows who wrote it.

**Why this priority**: This protects tickets from being commented on by unrelated users once
login (Story 1) and assignment (Story 2) exist, and makes the existing comment history
attributable. It builds directly on those two stories.

**Independent Test**: As the ticket's creator, add a comment and confirm it succeeds and shows
the creator's name. As the assigned `SUPPORT` user, add a comment and confirm it succeeds and
shows their name. As a third, unrelated logged-in user, attempt to add a comment and confirm it
is rejected.

**Acceptance Scenarios**:

1. **Given** a ticket, **When** the user who created it adds a comment, **Then** the comment is
   saved and shows that user's name as its creator.
2. **Given** a ticket currently assigned to a `SUPPORT` user, **When** that `SUPPORT` user adds
   a comment, **Then** the comment is saved and shows their name as its creator.
3. **Given** a ticket, **When** a logged-in user who neither created it nor is assigned to it
   attempts to add a comment, **Then** the request is rejected with a clear authorization error
   and no comment is saved.
4. **Given** an existing comment on a ticket, **When** the ticket is retrieved, **Then** the
   comment displays the name of the user who created it.

---

### User Story 4 - Filter the Ticket List by Ownership (Priority: P3)

A user narrows the ticket list to just the tickets they created, just the tickets assigned to
them, or every ticket they're allowed to see — instead of always getting one fixed list.

**Why this priority**: This is a convenience/efficiency refinement on top of the existing ticket
list (already delivered in a prior feature) and the ownership concepts Stories 1–2 introduce; the
system is usable without it once those stories exist.

**Independent Test**: As a user who both created some tickets and is assigned others, request the
list scoped to "created by me" and confirm only those appear; request "assigned to me" and
confirm only those appear; request "all" and confirm the full permitted set appears.

**Acceptance Scenarios**:

1. **Given** a user has created tickets and is separately assigned others, **When** they request
   the ticket list scoped to tickets they created, **Then** only tickets they created are
   returned.
2. **Given** a `SUPPORT` user is assigned several tickets, **When** they request the list scoped
   to tickets assigned to them, **Then** only those tickets are returned.
3. **Given** a user requests the ticket list with no ownership scope specified, **When** the
   request is submitted, **Then** the existing default listing behavior applies, filtered to what
   that user is permitted to see (see Assumptions).
4. **Given** an existing keyword/status filter (from prior search functionality) is combined with
   an ownership scope, **When** the request is submitted, **Then** only tickets matching both the
   ownership scope and the keyword/status filter are returned.

---

### Edge Cases

- What happens when a ticket must be created but no `SUPPORT`-role user exists yet? → The system
  MUST still create the ticket, leave it unassigned, and make the fact that it is unassigned
  visible on retrieval, rather than failing ticket creation outright.
- What happens when the `SUPPORT` user assigned to a ticket is later deleted or changes role away
  from `SUPPORT`? → The ticket's existing assignment record MUST be preserved as history; this
  spec does not require automatic reassignment when an assignee's role changes (see Assumptions).
- What happens when the ticket's creator is also the assigned `SUPPORT` user (self-assignment via
  the load-balancing rule)? → Commenting MUST still succeed under either qualifying condition;
  this is not a conflict.
- What happens when a login request is missing the email or password field? → It MUST be
  rejected with a descriptive validation error, distinct from the invalid-credentials error used
  when both fields are present but wrong.
- What happens when an authenticated user's role does not permit an action they attempt (e.g. a
  `GENERAL` user trying to act as an assignee)? → The request MUST be rejected with a clear
  authorization error identifying that the action is not permitted for their role.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a user to log in by submitting an email address and password,
  establishing an authenticated context for their subsequent requests.
- **FR-002**: System MUST store, for each user, a name, a unique email address, a password (held
  securely — never retrievable or displayable in plain text), and exactly one role of `SUPPORT`,
  `GENERAL`, or `ADMIN`.
- **FR-003**: System MUST reject a login attempt with an incorrect password or an unregistered
  email using the same generic invalid-credentials error, and MUST NOT establish an authenticated
  context for a rejected attempt.
- **FR-004**: System MUST reject a login request missing the email or password field with a
  descriptive validation error.
- **FR-005**: System MUST be able to hold user account records (name, email, password, role) that
  exist prior to any login attempt; how those accounts are created or onboarded is out of scope
  for this feature (see Assumptions).
- **FR-006**: System MUST reject a ticket-creation request from a caller without an authenticated
  context, and MUST NOT create the ticket.
- **FR-007**: System MUST reject a chatbot query request from a caller without an authenticated
  context, and MUST NOT generate a response.
- **FR-008**: System MUST NOT accept a caller-supplied assignee on ticket creation; the assignee
  MUST always be the system's own automatic choice.
- **FR-009**: Upon creating a ticket, system MUST automatically assign it to the `SUPPORT`-role
  user with the fewest tickets currently assigned to them in `OPEN` or `IN_PROGRESS` status (a
  ticket in `RESOLVED`, `CLOSED`, or `CANCELLED` status does not count toward their workload).
- **FR-010**: When two or more `SUPPORT` users are tied for the fewest counted tickets, system
  MUST select exactly one of them using a consistent, repeatable rule (e.g. a stable ordering),
  not an arbitrary or random choice.
- **FR-011**: When no `SUPPORT`-role user exists at ticket-creation time, system MUST still
  create the ticket, leaving it unassigned, rather than rejecting the creation request.
- **FR-012**: System MUST allow a comment to be added to a ticket only by the user who created
  that ticket or the user currently assigned to it, and MUST reject the request from any other
  user with a clear authorization error.
- **FR-013**: System MUST record, for every comment, the identity of the user who created it, and
  MUST return that user's name whenever the comment is displayed.
- **FR-014**: System MUST allow a ticket-list request to be scoped to exactly one of: tickets
  created by the requesting user, tickets assigned to the requesting user, or all tickets the
  requesting user is permitted to see.
- **FR-015**: System MUST allow an ownership scope (FR-014) to be combined with the existing
  keyword and status filters, returning only tickets matching all supplied conditions together.
- **FR-016**: System MUST allow a user with the `ADMIN` role to manually reassign a ticket to a
  different `SUPPORT`-role user after its automatic assignment; no other role MAY manually
  reassign a ticket.
- **FR-017**: System MUST allow a logged-in user to explicitly log out, ending their
  authenticated context so that subsequent requests from them are treated as unauthenticated
  until they log in again.

### Key Entities

- **User**: A person who can log in. Attributes: unique identifier, name, unique email, securely
  held password, role (`SUPPORT`, `GENERAL`, or `ADMIN`).
- **Ticket** *(extends the existing Ticket entity)*: Its assignee is now a reference to a
  `SUPPORT`-role `User` chosen automatically by the system, rather than free-form text supplied by
  a caller, and it additionally carries a reference to the `User` who created it.
- **Comment** *(extends the existing Comment entity)*: Now carries a reference to the `User` who
  created it, and that user's name is what is shown as the comment's creator.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of ticket-creation and chatbot-query attempts made without being logged in are
  rejected, verified across the full automated test suite.
- **SC-002**: 100% of newly created tickets are assigned to the `SUPPORT` user who, at the moment
  of creation, has the fewest counted tickets — verified by automated tests covering ties and
  varying load distributions.
- **SC-003**: 100% of comment attempts by a user who is neither a ticket's creator nor its
  assignee are rejected, and 100% of comments by a user who is either are accepted and correctly
  attributed by name.
- **SC-004**: Users can retrieve a ticket list scoped to "created by me," "assigned to me," or
  "all," and each scope returns exactly the expected subset in 100% of test cases, including when
  combined with an existing keyword or status filter.
- **SC-005**: No password is ever observable in plain text in any API response, log, or stored
  record, verified by review of every response and log format the feature introduces.

## Assumptions

- This feature amends prior specs' assumptions where they conflict: ticket assignment (previously
  a free-form value the caller could set — spec 001, FR-001/FR-004) is now exclusively automatic
  and role-constrained; comment authorship (previously "no separate authenticated author field" —
  spec 002) now requires an authenticated creator whose name is recorded and shown.
- "All tickets the requesting user is permitted to see" (FR-014) defaults to every ticket for
  `SUPPORT` and `ADMIN` users, and to only the tickets a `GENERAL` user created for a `GENERAL`
  user — a `GENERAL` user cannot list tickets they did not create, since they are never an
  assignee.
- A default ticket-list request with no ownership scope specified (User Story 4, Scenario 3)
  applies that same permitted-visibility rule as its default, preserving the existing "list all"
  behavior for `SUPPORT`/`ADMIN` without exposing other users' tickets to `GENERAL` users.
- Password storage and transmission follow standard secure-credential practice (one-way hashing
  at rest, never logged or returned); the specific mechanism is a planning decision, not a scope
  decision.
- An authenticated session/context, once established by login, is assumed to apply to a caller's
  subsequent requests for a reasonable duration; specific session lifetime/expiry is a planning
  decision not addressed by this spec.
- The chatbot feature (spec 003) is customer-facing and previously required no login; this spec
  supersedes that assumption — the chatbot now requires an authenticated user.
- User account creation/onboarding (who can create an account, and who may set a `SUPPORT` or
  `ADMIN` role) is explicitly out of scope for this feature and will be decided in a later
  feature. For this feature, user accounts (including `SUPPORT` and `ADMIN` users) are assumed to
  already exist as seed data populated at deployment time.
- Login brute-force/lockout protection is out of scope for this feature, deferred to the
  infrastructure/gateway layer, consistent with the chatbot's abuse-protection assumption in
  spec 003.
- Password strength/complexity rules are out of scope for this feature, since user account
  creation/update is out of scope (FR-005); any such rule belongs to the future account-creation
  feature.
