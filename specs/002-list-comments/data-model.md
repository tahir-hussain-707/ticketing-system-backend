# Phase 1 Data Model: List Comments

No new entity and no field changes. This feature adds a retrieval path onto the `Comment`
entity already defined in `001-ticket-management-backend/data-model.md`.

## Entity (unchanged, referenced for completeness)

### Comment

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | Unchanged from 001. |
| `ticketId` | UUID (FK → `tickets.id`) | Unchanged from 001. |
| `content` | `TEXT` | Unchanged from 001. Now explicitly required to be shown in the list (FR-002a). |
| `createdAt` | `TIMESTAMPTZ` | Unchanged from 001. Now explicitly required to be shown alongside content in the list (FR-002a, Clarifications session 2026-09-21). |

## New response shape

### CommentPage

| Field | Type | Notes |
|---|---|---|
| `content` | `CommentResponse[]` | The page's comments, ordered oldest to newest (FR-002). Each item carries `id`, `ticketId`, `content`, `createdAt` — no new fields on `CommentResponse` itself, it already had all four (001). |
| `page` | integer | Current page number, 0-indexed — same convention as `TicketPage` (001). |
| `size` | integer | Page size in effect. |
| `totalElements` | integer | Total comment count for the ticket. |
| `totalPages` | integer | Total pages available. |

## Persistence Notes

- New Flyway migration `V2__comments_list_index.sql`: composite index `(ticket_id, created_at)`
  on `comments`, additive to the schema created in 001's `V1__init_schema.sql`. No table
  structure change, no data migration.
- `ddl-auto=validate` unchanged (constitution Principle IV) — the new index is created only via
  this migration, never inferred by Hibernate.

## Validation / Error Rules Summary

| Condition | Rule | Error code |
|---|---|---|
| Ticket id does not exist | Reject the list request | `TICKET_NOT_FOUND` (404) — same code/shape as 001, no new error type |
| Ticket exists, has zero comments | Return `CommentPage` with `content: []`, `totalElements: 0` | Not an error (FR-003) |
| `page`/`size` out of supported range | Clamp to a sane bound (same convention as `TicketPage` in 001: size clamped to [1, 100], default 20) | Not an error |
