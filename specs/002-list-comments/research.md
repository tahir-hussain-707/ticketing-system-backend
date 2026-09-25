# Phase 0 Research: List Comments

Technical Context has no unresolved `NEEDS CLARIFICATION` — this is a small additive slice onto
an already-decided stack (constitution + 001-ticket-management-backend's own research.md). The
only real decisions are scoped to this endpoint.

## Endpoint shape: dedicated GET vs. extending ticket detail

- **Decision**: A new, dedicated `GET /api/v1/tickets/{ticketId}/comments` endpoint, paginated,
  separate from `GET /api/v1/tickets/{ticketId}`.
- **Rationale**: Directly required by spec FR-001 ("as its own request, independent of
  retrieving the ticket's other fields") and FR-005 (pagination). The existing ticket-detail
  endpoint returns all comments inline and unpaginated — fine for a quick look, but it cannot
  page and forces fetching the whole ticket just to browse comments.
- **Alternatives considered**: Adding `page`/`size` query params to the existing ticket-detail
  endpoint — rejected; conflates two different resources (a ticket, and a ticket's comments) in
  one response shape, and the spec explicitly asks for comments to be requestable "on its own."

## Query implementation

- **Decision**: Add `Page<Comment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId, Pageable
  pageable)` to `CommentRepository` (a Spring Data derived query), backed by a new composite
  index `(ticket_id, created_at)`.
- **Rationale**: The existing `idx_comments_ticket_id` index (from 001) is single-column; a
  query that filters by `ticket_id` AND sorts by `created_at` benefits from a composite index
  covering both, keeping SC-002 (1,000 comments, <2s) comfortably met without introducing any
  new query technology.
- **Alternatives considered**: Reusing the existing unpaginated
  `findByTicketIdOrderByCreatedAtAsc(UUID)` and paginating in memory — rejected; loads the full
  comment set into memory before truncating it, defeating the purpose of pagination and
  violating constitution Principle IV ("list endpoints MUST be paginated... MUST NOT return
  unbounded result sets" — an in-memory slice of an unbounded fetch does not satisfy that).

## Response shape

- **Decision**: A `CommentPage` DTO — `{content: CommentResponse[], page, size, totalElements,
  totalPages}` — mirroring `TicketPage` from 001 exactly, for consistency across the API.
- **Rationale**: `CommentResponse` (from 001) already carries `id`, `ticketId`, `content`, and
  `createdAt` — the timestamp the Clarifications session confirmed must be shown is already
  present; no DTO field changes needed, only a paginated wrapper around a list of them.
- **Alternatives considered**: A bare array response with `Link`/`X-Total-Count` headers for
  pagination metadata — rejected; inconsistent with the header-free, body-based pagination
  convention 001 already established for `TicketPage`, and the constitution favors one
  consistent contract shape over introducing a second convention.

## OpenAPI documentation

- **Decision**: Annotate the new controller method with `@Operation`/`@ApiResponses`/`@Schema`
  at the same time the endpoint is written, not as a follow-up.
- **Rationale**: 001-ticket-management-backend shipped its controllers without these annotations
  initially — a real gap between its own research.md decision ("generated from annotated
  controllers") and what `tasks.md` actually specified — caught only after the fact. This plan
  closes that gap up front instead of repeating it.

## Outcome

No unresolved `NEEDS CLARIFICATION` markers. Ready for Phase 1.
