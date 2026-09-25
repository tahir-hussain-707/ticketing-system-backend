# Feature Specification: Authenticated Ticketing with RAG Resolution Chatbot

**Feature Branch**: `005-auth-rag-chatbot`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Merge specs/003-rag-resolution-chatbot and
specs/004-auth-ticket-access into one spec. The RAG resolution chatbot (003) was never planned or
implemented — it was meant to be enhanced by the login/role requirement, not bolted on as a
separate, disconnected feature (004) that gates a chatbot endpoint which doesn't exist yet. This
spec is the union of both: email/password login with roles (`SUPPORT`/`GENERAL`/`ADMIN`);
login-gated ticket creation and login-gated chatbot access; automatic least-loaded-`SUPPORT`
ticket assignment with `ADMIN` override; creator/assignee-only commenting with named attribution;
ownership-scoped ticket listing; and the chatbot itself — a knowledge base built from resolved
tickets, semantic retrieval, and an LLM-grounded, customer-safe resolution answer, with
multi-turn conversation support."

**Amendment (2026-09-22, same-day update)**: "Update the specs. Only user login API should
remain open. Rest all other APIs should be now protected with authentication." Widens FR-006 from
gating ticket creation alone to gating every API this feature exposes except login itself —
closing the gap where ticket/comment retrieval and listing were left implicitly open (see
Assumptions).

**Supersedes**: `specs/003-rag-resolution-chatbot/spec.md` and
`specs/004-auth-ticket-access/spec.md`, both marked superseded and kept for history only. Neither
was planned/implemented before this merge (003 had no plan.md/tasks.md at all; 004's plan.md/
tasks.md are stale — written against 004 in isolation, including a chatbot-endpoint security
matcher for a controller that didn't exist — and are superseded by a fresh `/speckit-plan` run
against this merged spec).

## Clarifications

### Session 2026-09-22 (carried over from spec 003, before the merge)

- Q: Who queries the chatbot? → A: External/customer-facing. Raw internal comment content MUST
  NOT be surfaced verbatim — responses MUST be reworded into a customer-safe resolution answer.
  *(Post-merge: "customer-facing" now means any authenticated `GENERAL` user — see this spec's
  own login requirement below, which spec 003 did not originally have.)*
- Q: What happens when no confident match is found? → A: Tell the user no confident match was
  found and direct them to raise a ticket manually through the existing ticket flow. No
  automatic ticket creation from the chatbot.
- Q: Does each chatbot query stand alone, or can a user ask a follow-up referencing earlier
  turns? → A: Multi-turn — chatbot keeps conversation context; follow-ups can reference earlier
  turns in the same conversation.
- Q: How should abusive/excessive automated querying against the chatbot be stopped? → A: Out of
  scope for this feature — rate limiting/abuse protection is deferred to the infrastructure/
  gateway layer, not a functional requirement of the chatbot itself. *(Post-merge: the chatbot is
  no longer unauthenticated, which is itself a meaningful abuse mitigation, but a logged-in
  caller can still send unlimited queries — this deferral still stands.)*
- Q: What scale should the chatbot handle under "normal load"? → A: Up to 50,000 resolved
  tickets in the knowledge base, and up to 100 concurrent chatbot conversations.
- Q: How long should chatbot conversations be retained after they end? → A: Retained 90 days for
  quality review/audit purposes, then deleted.
- Q: What ends a chatbot conversation? → A: Both — an explicit end action by the user, or a
  30-minute inactivity timeout, whichever happens first.

### Session 2026-09-22 (carried over from spec 004, before the merge)

- Q: Who creates user accounts, and who can set a `SUPPORT`/`ADMIN` role? → A: Out of scope for
  this feature. User accounts (including `SUPPORT` and `ADMIN`) are assumed to already exist as
  seed data populated at deployment time; onboarding is a later feature.
- Q: What counts as a `SUPPORT` user's "tickets in progress" for auto-assignment
  load-balancing? → A: Every ticket currently assigned to them in `OPEN` or `IN_PROGRESS` status.
- Q: Can an automatically assigned ticket be reassigned later? → A: Yes — an `ADMIN` user can
  manually reassign it afterward; no other role may.
- Q: Should the system lock out or slow down repeated failed login attempts against one
  account? → A: No lockout in this feature — deferred to the infrastructure/gateway layer.
- Q: What minimum password strength should the system enforce? → A: None — user account
  creation/update is out of scope for this feature.
- Q: Does this feature need an explicit logout action? → A: Yes — a user can explicitly end
  their own authenticated session on demand.

### Session 2026-09-22 (post-amendment `/speckit-clarify`)

- Q: Can a `GENERAL` user view a single ticket (or its comments) by ID that they neither created
  nor are assigned to? → A: No — restrict single-ticket/comment view to the ticket's creator, its
  assignee, or a `SUPPORT`/`ADMIN` user, matching the same visibility rule already established for
  listing's "all" scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Log In to Access the System (Priority: P1)

A person logs in with their email and password before they can create tickets or use the
chatbot. Anyone who is not logged in is turned away from those actions.

**Why this priority**: Every other capability in this feature depends on knowing who is making
the request — including the chatbot, which (unlike the original, disconnected spec 003) has
never been built without this requirement. Without login, none of the role-based rules below have
anyone to apply to.

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
8. **Given** no one is logged in, **When** a request is submitted to retrieve a ticket's details,
   retrieve a ticket's comments, list tickets, update a ticket, transition a ticket's status,
   reassign a ticket, or add a comment, **Then** it is rejected as unauthenticated — the login
   request itself is the only exception to this feature's authentication requirement.
9. **Given** a logged-in `GENERAL` user who neither created a ticket nor is assigned to it,
   **When** they request that ticket's details or its comments by ID, **Then** the request is
   rejected with a clear authorization error — being logged in is necessary but not sufficient
   for single-ticket/comment view (FR-037).

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

### User Story 3 - Get an AI-Suggested Resolution from Past Tickets (Priority: P1)

A logged-in user describes an issue to the chatbot in their own words. The chatbot finds past
tickets that dealt with a similar issue and answers with the resolution those tickets actually
used, instead of a generic or made-up answer.

**Why this priority**: This is the core value the chatbot exists for — turning the organization's
history of solved problems into an instant, grounded answer. It is P1 alongside login and
auto-assignment because, unlike spec 003's original standalone framing, this chatbot has never
existed without login already required — the two ship together.

**Independent Test**: Seed the knowledge base with a resolved ticket describing a specific issue
and its resolution (via comments). As a logged-in user, submit a chatbot query describing the
same issue in different words. Confirm the response reflects that ticket's actual resolution, not
a generic answer.

**Acceptance Scenarios**:

1. **Given** the knowledge base contains a resolved ticket whose title/description closely match
   a new query's meaning, **When** a logged-in user submits that query to the chatbot, **Then**
   the chatbot returns a resolution consistent with that ticket's recorded comments/resolution.
2. **Given** the knowledge base contains several resolved tickets on related but distinct topics,
   **When** a logged-in user submits a query, **Then** the chatbot's response is grounded in the
   ticket(s) most relevant to that specific query, not an unrelated one.
3. **Given** a chatbot response was generated from one or more past tickets, **When** the user
   views the response, **Then** the response identifies which past ticket(s) it was based on.
4. **Given** a logged-in user submits an empty or blank query, **When** the request is
   submitted, **Then** it is rejected with a descriptive validation error and no chatbot call is
   made.
5. **Given** a user has received a response in an ongoing conversation, **When** they send a
   follow-up query that references something said earlier in that same conversation (e.g. "what
   about on iOS?"), **Then** the chatbot's retrieval and response account for that earlier
   context rather than treating the follow-up as fully standalone.

---

### User Story 4 - Knowledge Base Stays Current as Tickets Are Resolved (Priority: P2)

As support agents resolve tickets day to day, each resolved ticket automatically becomes part of
the chatbot's knowledge, without anyone having to manually curate or re-index it.

**Why this priority**: The chatbot's answers are only as good as its knowledge base. This must
work continuously for Story 3 to keep delivering value as new issues get resolved, but the
initial chatbot experience can be demonstrated (Story 3) against a knowledge base seeded ahead of
time, so this is the next incremental slice rather than a blocking prerequisite.

**Independent Test**: Resolve a ticket that describes a new, previously-unseen issue and its
resolution. Without any manual action, submit a chatbot query about that issue and confirm the
newly resolved ticket is now used to ground the response.

**Acceptance Scenarios**:

1. **Given** a ticket reaches a resolved state, **When** the resolution completes, **Then** the
   ticket's title, description, and comments become part of the knowledge base without manual
   intervention.
2. **Given** a ticket that is not yet resolved, **When** a user submits a query about that
   ticket's topic, **Then** the chatbot does not use that unresolved ticket as grounding.
3. **Given** the knowledge base is being updated for a just-resolved ticket, **When** a concurrent
   chatbot query is being answered, **Then** the query is served using a consistent knowledge base
   state rather than a partially updated one.

---

### User Story 5 - Only the Ticket's Creator or Assignee Can Comment, and Is Named (Priority: P2)

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

### User Story 6 - Chatbot Is Honest When It Has No Good Match (Priority: P3)

When no past resolved ticket is a close enough match to the user's question, the chatbot says so
instead of guessing, and tells the user what to do next.

**Why this priority**: This protects trust in the chatbot once Stories 3–4 are working — it
prevents confidently wrong answers — but the feature already delivers value without it for the
common case where good matches exist.

**Independent Test**: Submit a chatbot query about an issue with no similar ticket anywhere in the
knowledge base and confirm the chatbot states it has no confident match and directs the user to
raise a ticket manually through the existing ticket-creation flow.

**Acceptance Scenarios**:

1. **Given** no past resolved ticket is sufficiently similar to the query, **When** a user submits
   that query, **Then** the chatbot response clearly states it found no confident match rather
   than presenting a fabricated resolution.
2. **Given** the chatbot has told the user it found no confident match, **When** the user needs
   further help, **Then** the response directs them to raise a ticket manually through the
   existing ticket-creation flow (no ticket is created automatically by the chatbot).

---

### User Story 7 - Filter the Ticket List by Ownership (Priority: P3)

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
- What happens when a resolved ticket has no comments at all (no recorded resolution text)? →
  The system MUST still index the ticket's title/description but MUST NOT present it as a
  resolution source if it carries no resolution content to ground an answer.
- What happens when a ticket is reopened after being resolved? → The knowledge base entry MUST
  reflect the ticket's latest resolved content; a ticket no longer in a resolved state MUST NOT be
  used as grounding (see User Story 4, Scenario 2).
- What happens when the same query matches many almost-equally-similar past tickets? → The
  chatbot MUST ground its answer in the most relevant match(es) rather than merging unrelated
  resolutions into one inconsistent answer.
- What happens when the LLM or vector search is temporarily unavailable? → The chatbot MUST
  return a clear service-unavailable style error rather than an empty or silently wrong response.
- What happens when a query contains no extractable meaning (e.g. random characters)? → The
  system MUST still attempt retrieval and, finding no confident match, follow the Story 6
  no-match behavior rather than erroring.
- What happens when a user sends a new query after their conversation already ended (explicitly
  or via inactivity timeout)? → The system MUST start a new conversation rather than resuming
  context from the ended one.

## Requirements *(mandatory)*

### Functional Requirements

**Login & access gating**

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
- **FR-006**: System MUST reject every request to this feature's APIs from a caller without an
  authenticated context, with the single exception of the login request itself (FR-001) — this
  includes, at minimum, ticket creation, ticket retrieval (single and list), ticket updates and
  status transitions, ticket reassignment, comment creation and retrieval, and chatbot queries.
  No API this feature exposes is reachable by an unauthenticated caller other than login.
- **FR-007**: System MUST reject a chatbot query request from a caller without an authenticated
  context, and MUST NOT generate a response (a specific instance of FR-006).
- **FR-008**: System MUST allow a logged-in user to explicitly log out, ending their
  authenticated context so that subsequent requests from them are treated as unauthenticated
  until they log in again.

**Ticket assignment**

- **FR-009**: System MUST NOT accept a caller-supplied assignee on ticket creation; the assignee
  MUST always be the system's own automatic choice.
- **FR-010**: Upon creating a ticket, system MUST automatically assign it to the `SUPPORT`-role
  user with the fewest tickets currently assigned to them in `OPEN` or `IN_PROGRESS` status (a
  ticket in `RESOLVED`, `CLOSED`, or `CANCELLED` status does not count toward their workload).
- **FR-011**: When two or more `SUPPORT` users are tied for the fewest counted tickets, system
  MUST select exactly one of them using a consistent, repeatable rule (e.g. a stable ordering),
  not an arbitrary or random choice.
- **FR-012**: When no `SUPPORT`-role user exists at ticket-creation time, system MUST still
  create the ticket, leaving it unassigned, rather than rejecting the creation request.
- **FR-013**: System MUST allow a user with the `ADMIN` role to manually reassign a ticket to a
  different `SUPPORT`-role user after its automatic assignment; no other role MAY manually
  reassign a ticket.

**Comments**

- **FR-014**: System MUST allow a comment to be added to a ticket only by the user who created
  that ticket or the user currently assigned to it, and MUST reject the request from any other
  user with a clear authorization error.
- **FR-015**: System MUST record, for every comment, the identity of the user who created it, and
  MUST return that user's name whenever the comment is displayed.

**Ticket listing**

- **FR-016**: System MUST allow a ticket-list request to be scoped to exactly one of: tickets
  created by the requesting user, tickets assigned to the requesting user, or all tickets the
  requesting user is permitted to see.
- **FR-017**: System MUST allow an ownership scope (FR-016) to be combined with the existing
  keyword and status filters, returning only tickets matching all supplied conditions together.

**Chatbot knowledge base**

- **FR-018**: System MUST automatically add a ticket's title, description, and comments to a
  knowledge base when the ticket reaches a resolved state, with no manual step required.
- **FR-019**: System MUST represent each knowledge base entry so that it can be retrieved by
  semantic similarity to a free-text query, not only exact keyword matches.
- **FR-020**: System MUST retain, per knowledge base entry, enough of the originating ticket's
  comments to identify what resolution was actually used.

**Chatbot query & response**

- **FR-021**: System MUST provide a chatbot interface that accepts a free-text query describing a
  user's issue.
- **FR-022**: System MUST reject empty or blank chatbot queries with a descriptive validation
  error, without invoking retrieval or the language model.
- **FR-023**: For each valid query, system MUST retrieve the past resolved ticket(s) most similar
  in meaning to the query.
- **FR-024**: System MUST generate the chatbot's response using the retrieved ticket(s)'
  comments/resolution content as grounding, so the answer reflects a real historical resolution.
- **FR-025**: System MUST identify, alongside each chatbot response, which past ticket(s) the
  response was grounded in, using a reference that does not itself expose internal-only ticket
  data (e.g. a ticket reference number, not a raw internal record dump).
- **FR-026**: System MUST NOT use a ticket that is not in a resolved state as grounding for a
  chatbot response.
- **FR-027**: System MUST NOT present a knowledge base entry with no resolution content
  (title/description only, no comments) as the basis for a resolution.
- **FR-028**: When no past resolved ticket is a sufficiently close match to the query, system
  MUST tell the user no confident match was found instead of fabricating a resolution, and MUST
  direct the user to raise a ticket manually through the existing ticket-creation flow. The
  chatbot MUST NOT create a ticket automatically on the user's behalf.
- **FR-029**: System MUST keep the knowledge base consistent with the resolved-ticket data it was
  built from — a concurrent chatbot query MUST be served against a coherent knowledge base state,
  never a partially updated one.
- **FR-030**: System MUST return a clear, distinct error when knowledge base retrieval or
  response generation is unavailable, rather than an empty or silently incorrect answer.

**Chatbot customer-safety**

- **FR-031**: System MUST NOT surface internal-only ticket or comment content verbatim in a
  chatbot response; the resolution content from matched tickets MUST be reworded into a
  customer-appropriate answer before being returned.
- **FR-032**: System MUST NOT expose, in a chatbot response, any identifying details of the
  customer(s) or agent(s) associated with the matched past ticket(s) (e.g. names, contact
  information, internal assignee identifiers).

**Chatbot conversation**

- **FR-033**: System MUST maintain conversation context across multiple turns within the same
  chatbot conversation, so a follow-up query is interpreted in light of earlier turns rather than
  in isolation.
- **FR-034**: System MUST use the ongoing conversation's context, in addition to the current
  query, when retrieving and grounding a follow-up response.
- **FR-035**: System MUST retain a chatbot conversation (its queries and responses) for 90 days
  after the conversation ends, then delete it.
- **FR-036**: System MUST end a chatbot conversation when the user explicitly ends it, or after
  30 minutes with no new query in that conversation, whichever occurs first.

**Ticket & comment view authorization**

- **FR-037**: System MUST restrict retrieval of a single ticket's details and its comments to
  that ticket's creator, its currently assigned `SUPPORT` user, or any user with `SUPPORT` or
  `ADMIN` role; a request from any other authenticated user MUST be rejected with a clear
  authorization error (same visibility rule as FR-016's "all" scope for listing).

### Key Entities

- **User**: A person who can log in. Attributes: unique identifier, name, unique email, securely
  held password, role (`SUPPORT`, `GENERAL`, or `ADMIN`).
- **Ticket** *(extends the existing Ticket entity)*: Its assignee is now a reference to a
  `SUPPORT`-role `User` chosen automatically by the system, rather than free-form text supplied by
  a caller, and it additionally carries a reference to the `User` who created it.
- **Comment** *(extends the existing Comment entity)*: Now carries a reference to the `User` who
  created it, and that user's name is what is shown as the comment's creator.
- **Knowledge Base Entry**: A retrievable representation of one resolved ticket's title and
  description, used to find matches for a chatbot query. Carries a reference back to its source
  ticket and the resolution content (comments) needed to ground an answer.
- **Chatbot Conversation**: An ongoing exchange between one logged-in `User` and the chatbot,
  spanning one or more turns. Attributes: unique identifier, the `User` who owns it, start
  timestamp, ordered sequence of queries and responses that make up its turns, retained for 90
  days after the conversation ends then deleted.
- **Chatbot Query**: A free-text question or issue description submitted by a user as one turn of
  a conversation. Attributes: the query text, timestamp, the response ultimately produced for it,
  and a reference to its parent conversation.
- **Chatbot Response**: The answer returned to a chatbot query. Attributes: response text, the
  set of source ticket(s) it was grounded in (or an explicit no-match indicator), timestamp.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of attempts to call any API this feature exposes — other than login itself —
  made without being logged in, are rejected, verified across the full automated test suite.
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
- **SC-006**: For a query closely matching an existing resolved ticket's issue, the chatbot
  returns a response grounded in that ticket's actual resolution in at least 90% of such queries
  during testing.
- **SC-007**: A newly resolved ticket becomes usable as grounding for a matching chatbot query
  without any manual re-indexing step, verified for 100% of tickets resolved during testing.
- **SC-008**: When no sufficiently similar past ticket exists, the chatbot states it has no
  confident match rather than fabricating one, in 100% of such cases during testing.
- **SC-009**: Users can receive a chatbot response, including which past ticket(s) grounded it,
  in under 5 seconds under normal load, defined as a knowledge base of up to 50,000 resolved
  tickets and up to 100 concurrent chatbot conversations.
- **SC-010**: 100% of chatbot responses grounded in past tickets identify the specific ticket(s)
  used, so a user can verify the source.

## Assumptions

- This feature amends 001/002's original assumptions where they conflict: ticket assignment
  (previously a free-form value the caller could set — spec 001, FR-001/FR-004) is now
  exclusively automatic and role-constrained; comment authorship (previously "no separate
  authenticated author field" — spec 002) now requires an authenticated creator whose name is
  recorded and shown.
- "All tickets the requesting user is permitted to see" (FR-016) defaults to every ticket for
  `SUPPORT` and `ADMIN` users, and to only the tickets a `GENERAL` user created for a `GENERAL`
  user — a `GENERAL` user cannot list tickets they did not create, since they are never an
  assignee.
- A default ticket-list request with no ownership scope specified (User Story 7, Scenario 3)
  applies that same permitted-visibility rule as its default, preserving the existing "list all"
  behavior for `SUPPORT`/`ADMIN` without exposing other users' tickets to `GENERAL` users.
- Password storage and transmission follow standard secure-credential practice (one-way hashing
  at rest, never logged or returned); the specific mechanism is a planning decision, not a scope
  decision.
- An authenticated session/context, once established by login, is assumed to apply to a caller's
  subsequent requests for a reasonable duration; specific session lifetime/expiry is a planning
  decision not addressed by this spec.
- User account creation/onboarding (who can create an account, and who may set a `SUPPORT` or
  `ADMIN` role) is explicitly out of scope for this feature and will be decided in a later
  feature. For this feature, user accounts (including `SUPPORT` and `ADMIN` users) are assumed to
  already exist as seed data populated at deployment time.
- Login brute-force/lockout protection and chatbot query rate-limiting/abuse protection are both
  out of scope for this feature, deferred to the infrastructure/gateway layer.
- Password strength/complexity rules are out of scope for this feature, since user account
  creation/update is out of scope (FR-005); any such rule belongs to the future account-creation
  feature.
- "Resolved state" for knowledge-base inclusion means the ticket has reached `RESOLVED` (or later
  `CLOSED`) per the existing ticket lifecycle; tickets in `OPEN`, `IN_PROGRESS`, or `CANCELLED`
  are never used as grounding.
- The knowledge base is derived data built from existing ticket/comment records; it does not
  introduce a new source of truth for ticket content — the ticket and its comments remain
  authoritative.
- One resolved ticket can produce at most one knowledge base entry, refreshed if its comments
  change after resolution (e.g. a late clarifying comment), rather than one entry per comment.
- "User" throughout the chatbot-related requirements means any logged-in user regardless of role
  (`GENERAL`, `SUPPORT`, or `ADMIN`) — the chatbot is not restricted to one role, mirroring how
  ticket creation is open to every logged-in role.
- Reworded/customer-safe chatbot response content (FR-031) means the resolution steps are
  conveyed without exposing raw internal comment text, internal notes, or people's identifying
  details (FR-032) — the specific rewording mechanism is a planning decision, not a scope
  decision.
- FR-006's "only login is open" boundary applies to this feature's business APIs (tickets,
  comments, chatbot). It deliberately does not extend to operational/infrastructure endpoints
  (health checks, API documentation) — those are outside this feature's scope and already
  governed separately by the project constitution.
- This amendment closes a gap the original merge left implicit: ticket/comment retrieval and
  listing were not previously named as requiring login, and — because this feature's ticket/
  comment responses now carry real user names (`createdBy`, `assignee`, comment author) — leaving
  them open would have exposed that identifying information to anonymous callers. FR-006 now
  closes that gap explicitly rather than leaving it to be decided during planning.
- FR-037 extends that same closing to authorization, not just authentication: being logged in is
  necessary but not sufficient to view a specific ticket/its comments by ID — a `GENERAL` user is
  limited to tickets they created or are assigned to (in practice, tickets they created, since a
  `GENERAL` user is never an assignee — same as FR-016's listing rule), while `SUPPORT`/`ADMIN`
  retain full view access, consistent with their existing "all" listing visibility.
