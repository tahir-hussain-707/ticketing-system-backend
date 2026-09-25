# Phase 1 Data Model: Ticket Management Backend

Source: spec Key Entities, Functional Requirements FR-001–FR-013, Clarifications session
2026-09-21. Persistence rules per constitution Principle IV.

## Enumerations

### TicketStatus

`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED` — persisted as a `VARCHAR`, never an
ordinal (constitution Principle IV).

### TicketPriority

`LOW`, `MEDIUM`, `HIGH`, `CRITICAL` (Clarifications session 2026-09-21) — persisted as a
`VARCHAR`.

## Entities

### Ticket

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated server-side on creation; immutable. |
| `title` | `VARCHAR(200)` | Required, non-blank (FR-001, FR-011). |
| `description` | `TEXT` | Required, non-blank (FR-001, FR-011). |
| `priority` | `TicketPriority` | Required; one of `LOW`/`MEDIUM`/`HIGH`/`CRITICAL` (FR-011). Defaults to `MEDIUM` if omitted on creation (edge case: unspecified priority must not fail). |
| `status` | `TicketStatus` | Required. Set to `OPEN` on creation; thereafter mutated ONLY via the transition policy (FR-009, FR-010, FR-013) — never by a direct field update. |
| `assignee` | `VARCHAR(200)`, nullable | Free-form identifying value (name or account id); no referential integrity to a user table exists in this feature (spec Assumptions — no auth/user-management subsystem). |
| `version` | `BIGINT` | JPA `@Version` — optimistic locking (constitution Principle IV); `409 Conflict` on a lost update. |
| `createdAt` | `TIMESTAMPTZ` | Set once on creation, UTC (constitution Principle: Time). |
| `updatedAt` | `TIMESTAMPTZ` | Updated on every field change or status transition, UTC. |

Relationships: owns zero or more `Comment` rows (one-to-many, `Comment.ticket_id` FK,
`ON DELETE CASCADE` not required since tickets are never deleted in this feature's scope — no
delete requirement exists in the spec).

Indexes: `pg_trgm` GIN index on `title` and `description` (keyword search, FR-006); B-tree index
on `status` (status filter, FR-007); both combinable in one query (FR-007 requires supporting
keyword + status together).

### Comment

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated server-side on creation; immutable. |
| `ticketId` | UUID (FK → `tickets.id`) | Required; `NOT NULL`; the request path ticket id (FR-005). Rejects with `TICKET_NOT_FOUND` if the referenced ticket does not exist (spec User Story 2, Acceptance Scenario 4). |
| `content` | `TEXT` | Required, non-blank (FR-005, FR-011). |
| `createdAt` | `TIMESTAMPTZ` | Set once on creation, UTC; comments are returned ordered by this field ascending (FR-005, Acceptance Scenario 2). |

No `updatedAt`/edit support — the spec only requires adding comments, not editing or deleting
them.

## State Transitions (Ticket.status)

Owned exclusively by `TicketStatusTransitionPolicy` (research.md). Allowed edges:

| From | To (allowed) |
|---|---|
| `OPEN` | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | `RESOLVED`, `CANCELLED` |
| `RESOLVED` | `CLOSED` |
| `CLOSED` | *(none — terminal)* |
| `CANCELLED` | *(none — terminal)* |

Any transition not in this table — including every attempt to reach `OPEN` from any other
status — MUST be rejected with `409 Conflict`, error code `INVALID_TRANSITION`, and the
ticket's stored status left unchanged (FR-010).

## Validation Rules Summary (maps to FR-011)

| Field | Rule | Violation → error code |
|---|---|---|
| `title` | Required, non-blank, ≤200 chars | `VALIDATION_FAILED` |
| `description` | Required, non-blank | `VALIDATION_FAILED` |
| `priority` | One of the 4 enum values | `VALIDATION_FAILED` |
| `assignee` | Optional; if present, non-blank | `VALIDATION_FAILED` |
| `comment.content` | Required, non-blank | `VALIDATION_FAILED` |
| status transition target | Must be a value in the transition table | `INVALID_TRANSITION` |
| ticket id (path) | Must reference an existing ticket | `TICKET_NOT_FOUND` |
| concurrent conflicting update | Detected via `@Version` mismatch | `TICKET_CONFLICT` |

## Persistence Notes

- Schema created by Flyway `V1__init_schema.sql`: `tickets` and `comments` tables, `NOT NULL`
  constraints per the tables above, FK from `comments.ticket_id` to `tickets.id`, the two search
  indexes, and the `pg_trgm` extension enabled.
- `ddl-auto=validate` in every environment — Hibernate never generates schema (constitution
  Principle IV).
