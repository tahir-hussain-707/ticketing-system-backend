# Phase 1 Data Model: Authentication, Auto-Assignment & Scoped Ticket Access

## User (NEW)

Represents a person who can log in (spec Key Entities: User).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated at creation. |
| `name` | `VARCHAR(200)` | Not null. |
| `email` | `VARCHAR(320)` | Not null. Unique, case-insensitively (`LOWER(email)` unique index) — FR-002/FR-003. Used as the login identifier. |
| `passwordHash` | `VARCHAR(200)` | Not null. `BCryptPasswordEncoder` output — never exposed by any DTO, never logged (FR-002, SC-005). |
| `role` | `UserRole` enum, stored as string | Not null. Exactly one of `SUPPORT`, `GENERAL`, `ADMIN` (FR-002). |
| `createdAt` / `updatedAt` | `TIMESTAMPTZ` | Set on persist/update, same pattern as `Ticket`. |

No lifecycle/state transitions — a `User` row, once created (out of scope for this feature — see
spec FR-005), is read-only from this feature's perspective.

## Ticket (EXTENDS existing entity from 001)

| Field | Change |
|---|---|
| `assignee` (was `VARCHAR(200)` free text) | **Replaced** by `assigneeId` — nullable UUID FK to `users.id`, restricted at the service layer to a `User` whose `role = SUPPORT` (FR-008, FR-009). Nullable because a ticket MAY be created with no `SUPPORT` user available (FR-011, Edge Cases). |
| `createdById` | **New** — UUID FK to `users.id`, not null. The authenticated caller who created the ticket (FR-006, Key Entities). |
| All other fields (`id`, `title`, `description`, `priority`, `status`, `version`, `createdAt`, `updatedAt`) | Unchanged from 001. |

**Validation/business rules added by this feature**:
- `assigneeId`, when set, MUST reference a `User` with `role = SUPPORT` (enforced in
  `TicketService`, not just a bare FK — a FK alone can't express the role constraint).
- `assigneeId` is never accepted from a client on create (FR-008) or on the general update
  endpoint (FR-016) — it is only ever set by the auto-assignment algorithm at creation, or by the
  dedicated `ADMIN`-only reassignment endpoint.
- `createdById` is set once, at creation, from the authenticated caller, and never changes.

**New indexes** (back the auto-assignment query and ownership-scoped listing):
- `idx_tickets_assignee_id_status` on `(assignee_id, status)` — the least-loaded-`SUPPORT`-user
  aggregate (FR-009) and the `scope=assigned` list query both filter/group by this pair.
- `idx_tickets_created_by_id` on `(created_by_id)` — the `scope=mine` list query.

## Comment (EXTENDS existing entity from 001/002)

| Field | Change |
|---|---|
| `authorId` | **New** — UUID FK to `users.id`, not null. The authenticated caller who wrote the comment (FR-013, Key Entities). |
| All other fields (`id`, `ticketId`, `content`, `createdAt`) | Unchanged. |

**Validation/business rule added by this feature**: a comment may only be created when
`authorId` equals the parent ticket's `createdById` OR its `assigneeId` (FR-012) — enforced in
`CommentService` against the ticket's current state, not expressible as a static column
constraint.

## Relationships

```text
User (1) ──< assigneeId  ── Ticket   (a SUPPORT user is assigned 0..N tickets)
User (1) ──< createdById ── Ticket   (a user creates 0..N tickets)
User (1) ──< authorId    ── Comment  (a user authors 0..N comments)
Ticket (1) ──< ticketId  ── Comment  (unchanged from 001)
```

## Migrations (Flyway, additive/forward-only per constitution Principle IV)

- **`V3__create_users_table.sql`**: creates `users` (columns above), a case-insensitive unique
  index on `email`, and an index on `role` (backs the `SUPPORT`-user lookup).
- **`V4__ticket_ownership_and_assignment.sql`**: adds `tickets.created_by_id` (`NOT NULL`,
  FK → `users`) and `tickets.assignee_id` (nullable, FK → `users`); drops the old `tickets.assignee`
  text column; adds `idx_tickets_assignee_id_status` and `idx_tickets_created_by_id`.
- **`V5__comment_author.sql`**: adds `comments.author_id` (`NOT NULL`, FK → `users`).

See research.md's "Data model: assignee and comment authorship become real identities" entry for
why the two `NOT NULL` foreign keys are added directly rather than nullable-then-backfilled.
