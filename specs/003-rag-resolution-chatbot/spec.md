# Feature Specification: RAG-Powered Resolution Chatbot

**Feature Branch**: `003-rag-resolution-chatbot`

**Created**: 2026-09-22

**Status**: Superseded — merged into
[`specs/005-auth-rag-chatbot/spec.md`](../005-auth-rag-chatbot/spec.md) on 2026-09-22, along with
`specs/004-auth-ticket-access`. This file is kept for history only; do not plan or implement
against it directly.

**Input**: User description: "Build a chatbot backed by a knowledge base of past resolved tickets. The knowledge base is the vector representation of resolved tickets' titles and descriptions, with metadata containing the comments/resolution. Whenever a ticket is resolved, generate embeddings from its title and description and store them in a vector database with metadata. When a user sends a chatbot query, search the vector database for similar past tickets and send the findings plus the query to an LLM, so the LLM can use the matched tickets' comments to identify the resolution and provide the correct resolution to the user."

## Clarifications

### Session 2026-09-22

- Q: Who queries the chatbot? → A: External/customer-facing. Raw internal comment content MUST
  NOT be surfaced verbatim — responses MUST be reworded into a customer-safe resolution answer.
- Q: What happens when no confident match is found? → A: Tell the user no confident match was
  found and direct them to raise a ticket manually through the existing ticket flow. No
  automatic ticket creation from the chatbot.
- Q: Does each chatbot query stand alone, or can a user ask a follow-up referencing earlier
  turns? → A: Multi-turn — chatbot keeps conversation context; follow-ups can reference earlier
  turns in the same conversation.
- Q: How should abusive/excessive automated querying against the unauthenticated chatbot be
  stopped? → A: Out of scope for this feature — rate limiting/abuse protection is deferred to the
  infrastructure/gateway layer, not a functional requirement of the chatbot itself.
- Q: What scale should the chatbot handle under "normal load"? → A: Up to 50,000 resolved
  tickets in the knowledge base, and up to 100 concurrent chatbot conversations.
- Q: How long should chatbot conversations be retained after they end? → A: Retained 90 days for
  quality review/audit purposes, then deleted.
- Q: What ends a chatbot conversation? → A: Both — an explicit end action by the user, or a
  30-minute inactivity timeout, whichever happens first.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Get an AI-Suggested Resolution from Past Tickets (Priority: P1)

A user describes an issue to the chatbot in their own words. The chatbot finds past tickets that
dealt with a similar issue and answers with the resolution those tickets actually used, instead
of a generic or made-up answer.

**Why this priority**: This is the core value of the feature — turning the organization's history
of solved problems into an instant, grounded answer. Without it there is no product.

**Independent Test**: Seed the knowledge base with a resolved ticket describing a specific issue
and its resolution (via comments). Submit a chatbot query describing the same issue in different
words. Confirm the response reflects that ticket's actual resolution, not a generic answer.

**Acceptance Scenarios**:

1. **Given** the knowledge base contains a resolved ticket whose title/description closely match
   a new query's meaning, **When** a user submits that query to the chatbot, **Then** the chatbot
   returns a resolution consistent with that ticket's recorded comments/resolution.
2. **Given** the knowledge base contains several resolved tickets on related but distinct topics,
   **When** a user submits a query, **Then** the chatbot's response is grounded in the ticket(s)
   most relevant to that specific query, not an unrelated one.
3. **Given** a chatbot response was generated from one or more past tickets, **When** the user
   views the response, **Then** the response identifies which past ticket(s) it was based on.
4. **Given** a user submits an empty or blank query, **When** the request is submitted, **Then**
   it is rejected with a descriptive validation error and no chatbot call is made.
5. **Given** a user has received a response in an ongoing conversation, **When** they send a
   follow-up query that references something said earlier in that same conversation (e.g. "what
   about on iOS?"), **Then** the chatbot's retrieval and response account for that earlier
   context rather than treating the follow-up as fully standalone.

---

### User Story 2 - Knowledge Base Stays Current as Tickets Are Resolved (Priority: P2)

As support agents resolve tickets day to day, each resolved ticket automatically becomes part of
the chatbot's knowledge, without anyone having to manually curate or re-index it.

**Why this priority**: The chatbot's answers are only as good as its knowledge base. This must
work continuously for Story 1 to keep delivering value as new issues get resolved, but the
initial chatbot experience can be demonstrated (Story 1) against a knowledge base seeded ahead of
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

### User Story 3 - Chatbot Is Honest When It Has No Good Match (Priority: P3)

When no past resolved ticket is a close enough match to the user's question, the chatbot says so
instead of guessing, and tells the user what to do next.

**Why this priority**: This protects trust in the chatbot once Stories 1–2 are working — it
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

### Edge Cases

- What happens when a user sends a new query after their conversation already ended (explicitly
  or via inactivity timeout)? → The system MUST start a new conversation rather than resuming
  context from the ended one.
- What happens when a resolved ticket has no comments at all (no recorded resolution text)? →
  The system MUST still index the ticket's title/description but MUST NOT present it as a
  resolution source if it carries no resolution content to ground an answer.
- What happens when a ticket is reopened after being resolved (e.g. `RESOLVED → CLOSED` is fine,
  but if a correction workflow existed)? → The knowledge base entry MUST reflect the ticket's
  latest resolved content; a ticket no longer in a resolved state MUST NOT be used as grounding
  (see User Story 2, Scenario 2).
- What happens when the same query matches many almost-equally-similar past tickets? → The
  chatbot MUST ground its answer in the most relevant match(es) rather than merging unrelated
  resolutions into one inconsistent answer.
- What happens when the LLM or vector search is temporarily unavailable? → The chatbot MUST
  return a clear service-unavailable style error rather than an empty or silently wrong response.
- What happens when a query contains no extractable meaning (e.g. random characters)? → The
  system MUST still attempt retrieval and, finding no confident match, follow the Story 3
  no-match behavior rather than erroring.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST automatically add a ticket's title, description, and comments to a
  knowledge base when the ticket reaches a resolved state, with no manual step required.
- **FR-002**: System MUST represent each knowledge base entry so that it can be retrieved by
  semantic similarity to a free-text query, not only exact keyword matches.
- **FR-003**: System MUST retain, per knowledge base entry, enough of the originating ticket's
  comments to identify what resolution was actually used.
- **FR-004**: System MUST provide a chatbot interface that accepts a free-text query describing a
  user's issue.
- **FR-005**: System MUST reject empty or blank chatbot queries with a descriptive validation
  error, without invoking retrieval or the language model.
- **FR-006**: For each valid query, system MUST retrieve the past resolved ticket(s) most similar
  in meaning to the query.
- **FR-007**: System MUST generate the chatbot's response using the retrieved ticket(s)'
  comments/resolution content as grounding, so the answer reflects a real historical resolution.
- **FR-008**: System MUST identify, alongside each chatbot response, which past ticket(s) the
  response was grounded in, using a reference that does not itself expose internal-only ticket
  data (e.g. a ticket reference number, not a raw internal record dump).
- **FR-009**: System MUST NOT use a ticket that is not in a resolved state as grounding for a
  chatbot response.
- **FR-010**: System MUST NOT present a knowledge base entry with no resolution content
  (title/description only, no comments) as the basis for a resolution.
- **FR-011**: When no past resolved ticket is a sufficiently close match to the query, system
  MUST tell the user no confident match was found instead of fabricating a resolution, and MUST
  direct the user to raise a ticket manually through the existing ticket-creation flow. The
  chatbot MUST NOT create a ticket automatically on the user's behalf.
- **FR-012**: System MUST keep the knowledge base consistent with the resolved-ticket data it was
  built from — a concurrent chatbot query MUST be served against a coherent knowledge base state,
  never a partially updated one.
- **FR-013**: System MUST return a clear, distinct error when knowledge base retrieval or
  response generation is unavailable, rather than an empty or silently incorrect answer.
- **FR-014**: The chatbot is exposed to external/customer users. System MUST NOT surface
  internal-only ticket or comment content verbatim in a chatbot response; the resolution content
  from matched tickets MUST be reworded into a customer-appropriate answer before being returned.
- **FR-015**: System MUST NOT expose, in a chatbot response, any identifying details of the
  customer(s) or agent(s) associated with the matched past ticket(s) (e.g. names, contact
  information, internal assignee identifiers).
- **FR-016**: System MUST maintain conversation context across multiple turns within the same
  chatbot conversation, so a follow-up query is interpreted in light of earlier turns rather than
  in isolation.
- **FR-017**: System MUST use the ongoing conversation's context, in addition to the current
  query, when retrieving and grounding a follow-up response.
- **FR-018**: System MUST retain a chatbot conversation (its queries and responses) for 90 days
  after the conversation ends, then delete it.
- **FR-019**: System MUST end a chatbot conversation when the user explicitly ends it, or after
  30 minutes with no new query in that conversation, whichever occurs first.

### Key Entities

- **Knowledge Base Entry**: A retrievable representation of one resolved ticket's title and
  description, used to find matches for a chatbot query. Carries a reference back to its source
  ticket and the resolution content (comments) needed to ground an answer.
- **Chatbot Conversation**: An ongoing exchange between one user and the chatbot, spanning one or
  more turns. Attributes: unique identifier, start timestamp, ordered sequence of queries and
  responses that make up its turns, retained for 90 days after the conversation ends then
  deleted.
- **Chatbot Query**: A free-text question or issue description submitted by a user as one turn of
  a conversation. Attributes: the query text, timestamp, the response ultimately produced for it,
  and a reference to its parent conversation.
- **Chatbot Response**: The answer returned to a chatbot query. Attributes: response text, the
  set of source ticket(s) it was grounded in (or an explicit no-match indicator), timestamp.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a query closely matching an existing resolved ticket's issue, the chatbot
  returns a response grounded in that ticket's actual resolution in at least 90% of such queries
  during testing.
- **SC-002**: A newly resolved ticket becomes usable as grounding for a matching chatbot query
  without any manual re-indexing step, verified for 100% of tickets resolved during testing.
- **SC-003**: When no sufficiently similar past ticket exists, the chatbot states it has no
  confident match rather than fabricating one, in 100% of such cases during testing.
- **SC-004**: Users can receive a chatbot response, including which past ticket(s) grounded it,
  in under 5 seconds under normal load, defined as a knowledge base of up to 50,000 resolved
  tickets and up to 100 concurrent chatbot conversations.
- **SC-005**: 100% of chatbot responses grounded in past tickets identify the specific ticket(s)
  used, so a user can verify the source.

## Assumptions

- "Resolved state" for knowledge-base inclusion means the ticket has reached `RESOLVED` (or later
  `CLOSED`) per the existing ticket lifecycle; tickets in `OPEN`, `IN_PROGRESS`, or `CANCELLED`
  are never used as grounding.
- The knowledge base is derived data built from existing ticket/comment records; it does not
  introduce a new source of truth for ticket content — the ticket and its comments remain
  authoritative.
- One resolved ticket can produce at most one knowledge base entry, refreshed if its comments
  change after resolution (e.g. a late clarifying comment), rather than one entry per comment.
- The chatbot is a new, additive capability — it does not change ticket CRUD, comment, status
  lifecycle, or search behavior defined in prior specs.
- The chatbot is customer-facing; "user" throughout this spec means the external/customer user
  submitting a query, not an internal support agent.
- Reworded/customer-safe response content (FR-014) means the resolution steps are conveyed
  without exposing raw internal comment text, internal notes, or people's identifying details
  (FR-015) — the specific rewording mechanism is a planning decision, not a scope decision.
- No new authentication/authorization subsystem is introduced beyond what already exists for this
  project (per the project constitution); the chatbot does not require the user to be signed in
  unless a future amendment says otherwise.
- Rate limiting and abuse protection for the unauthenticated chatbot endpoint are handled outside
  this feature (e.g. at an infrastructure/gateway layer) and are not a functional requirement
  defined by this spec.
