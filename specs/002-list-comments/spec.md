# Feature Specification: List Comments

**Feature Branch**: `002-list-comments`

**Created**: 2026-09-21

**Status**: Draft

**Input**: User description: "Please add the listing of comments in the scope of the application."

## Clarifications

### Session 2026-09-21

- Q: Should each comment in the list display its timestamp alongside its content? → A: Yes — each listed comment shows its content and its timestamp.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse a Ticket's Comment History (Priority: P1)

A support agent opens a ticket and wants to review its full comment history — every note
added over the ticket's lifetime, in the order it was written — as its own retrievable list,
independent of the rest of the ticket's fields.

**Why this priority**: Comments are the primary record of what happened on a ticket. Without an
explicit way to list them, that history is only ever a side-effect of fetching everything else
about the ticket, which does not scale as comment volume grows and cannot be paged, sorted, or
requested on its own.

**Independent Test**: Can be fully tested by adding several comments to a ticket, then requesting
that ticket's comment list on its own (without requesting the rest of the ticket record) and
confirming every comment appears, in the order it was added.

**Acceptance Scenarios**:

1. **Given** a ticket with several comments, **When** a support agent requests that ticket's
   comment list, **Then** every comment is returned, ordered from oldest to newest, each showing
   its content and the timestamp it was added.
2. **Given** a ticket with no comments yet, **When** a support agent requests that ticket's
   comment list, **Then** an empty list is returned (not an error).
3. **Given** a ticket with a very large number of comments, **When** a support agent requests
   that ticket's comment list, **Then** the results are returned in manageable pages rather than
   all at once, and the agent can page through the rest.
4. **Given** a ticket identifier that does not exist, **When** a support agent requests its
   comment list, **Then** the request is rejected with a clear "not found" error rather than an
   empty list.

---

### Edge Cases

- What happens when a ticket has thousands of comments? → The system MUST page the results
  rather than return them all in one response (see SC-002).
- What happens when a page number or page size outside the supported range is requested? → The
  system MUST apply a sane bound or default rather than fail.
- What happens when comments are added to a ticket while an agent is paging through its comment
  list? → Each page reflects the comments present at the time of that page's request; the system
  is not required to freeze the list for the duration of paging.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow retrieval of the list of comments belonging to a specific
  ticket, as its own request, independent of retrieving the ticket's other fields.
- **FR-002**: System MUST return a ticket's comments ordered from oldest to newest.
- **FR-002a**: Each comment in the list MUST show its content and the timestamp it was added, so
  an agent can tell what happened and when without opening each comment individually.
- **FR-003**: System MUST return an empty list, not an error, when the ticket has no comments.
- **FR-004**: System MUST reject a comment-list request for a ticket identifier that does not
  exist, with a specific "not found" error, rather than an empty list.
- **FR-005**: System MUST page comment results so that retrieval time does not grow unbounded as
  a ticket accumulates more comments.

### Key Entities

- **Comment**: A timestamped note belonging to exactly one ticket (defined in the
  001-ticket-management-backend feature). This feature adds a way to retrieve a ticket's
  comments as their own list, with each listed comment showing its content and timestamp
  (Clarifications session 2026-09-21); it does not change what a comment is or how one is
  created.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A support agent can retrieve a ticket's complete comment history, correctly
  ordered, in a single request in 100% of attempts.
- **SC-002**: Comment list retrieval for a ticket with up to 1,000 comments returns in under 2
  seconds.
- **SC-003**: 100% of comment-list requests for a non-existent ticket receive a specific
  "not found" error rather than an empty or malformed result.

## Assumptions

- This feature extends 001-ticket-management-backend. Adding a comment and the shape of a
  comment (its content and timestamp) are unchanged and out of scope here — only the ability to
  retrieve a ticket's comments as their own list is added.
- Default and maximum page sizes follow the same convention already established for listing
  tickets in 001-ticket-management-backend, for consistency across the application.
- No new user-facing comment data is introduced; this is a retrieval capability, not a new kind
  of content.
