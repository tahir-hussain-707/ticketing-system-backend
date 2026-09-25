# Phase 1 Data Model: Authenticated Ticketing with RAG Resolution Chatbot

## User (NEW)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated at creation. |
| `name` | `VARCHAR(200)` | Not null. |
| `email` | `VARCHAR(320)` | Not null. Unique, case-insensitively (`LOWER(email)` unique index) — FR-002/FR-003. |
| `passwordHash` | `VARCHAR(200)` | Not null. `BCryptPasswordEncoder` output — never exposed by any DTO, never logged (FR-002, SC-005). |
| `role` | `UserRole` enum, string-stored | Not null. Exactly one of `SUPPORT`, `GENERAL`, `ADMIN` (FR-002). |
| `createdAt`/`updatedAt` | `TIMESTAMPTZ` | Set on persist/update. |

No lifecycle here — account creation is out of scope (FR-005); rows are seed data.

## Ticket (EXTENDS existing entity from 001)

| Field | Change |
|---|---|
| `assignee` (was free text) | **Replaced** by `assigneeId` — nullable UUID FK to `users.id`, restricted at the service layer to a `SUPPORT` user (FR-009/FR-010). Nullable per FR-012 (no `SUPPORT` user available). |
| `createdById` | **New** — UUID FK to `users.id`, not null. |
| All other fields | Unchanged from 001. |

**Rules added by this feature**:
- `assigneeId` never accepted from a client on create (FR-009) or general update (FR-013) — only
  set by auto-assignment or the dedicated `ADMIN`-only reassignment endpoint.
- Viewing a single ticket (and, transitively, its comments) is restricted to `createdById`,
  `assigneeId`, or a caller with role `SUPPORT`/`ADMIN` (FR-037) — enforced in `TicketService`,
  not expressible as a column constraint.

**New indexes**: `idx_tickets_assignee_id_status (assignee_id, status)` (auto-assignment
workload query, FR-010); `idx_tickets_created_by_id (created_by_id)` (`scope=mine` listing and
FR-037's creator check).

## Comment (EXTENDS existing entity from 001/002)

| Field | Change |
|---|---|
| `authorId` | **New** — UUID FK to `users.id`, not null. |

**Rule added**: a comment may only be created when `authorId` equals the parent ticket's
`createdById` OR `assigneeId` (FR-014) — enforced in `CommentService` against the ticket's
current state.

## Knowledge Base Entry (NEW)

One row per resolved ticket usable as chatbot grounding (spec Key Entities: Knowledge Base
Entry).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated at creation. |
| `ticketId` | UUID | Not null, unique (one entry per ticket — "at most one knowledge base entry" per spec.md Assumptions). References `tickets.id`. |
| `embedding` | `vector(n)` (`pgvector`) | Not null. Generated from the ticket's title + description (FR-018/FR-019) via `EmbeddingClient`; dimension `n` fixed by the chosen embedding model. |
| `resolutionContent` | `TEXT` | The originating ticket's comment/resolution text retained for grounding (FR-020). Empty/absent when the ticket has no comments — such an entry is indexed (title/description still searchable) but MUST NOT be used as a resolution source (FR-027, Edge Cases). |
| `hasResolutionContent` | `BOOLEAN` | Derived flag backing the FR-027 exclusion filter without re-parsing `resolutionContent` on every query. |
| `createdAt`/`updatedAt` | `TIMESTAMPTZ` | `updatedAt` bumps when a late comment refreshes `resolutionContent` (spec.md Assumptions: "refreshed if its comments change after resolution"). |

**New index**: an HNSW index on `embedding` (cosine distance) backing FR-023's similarity
retrieval at the spec's stated scale (SC-009: ≤50,000 entries).

**Rule**: a row is only created/refreshed when the source ticket is in `RESOLVED` or `CLOSED`
status (FR-018, FR-026); a ticket that leaves a resolved state does not currently retract its
entry mid-flight (Edge Cases: reopening is not part of this feature's scope, per the existing
ticket lifecycle).

## Chatbot Conversation (NEW)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated at creation. |
| `userId` | UUID | Not null. References `users.id` — the logged-in caller who owns it. |
| `startedAt` | `TIMESTAMPTZ` | Not null. |
| `explicitlyEndedAt` | `TIMESTAMPTZ`, nullable | Set when the user explicitly ends the conversation (FR-036). |

**Derived, not stored**: "is this conversation still open" = `explicitlyEndedAt IS NULL AND
(last turn's `createdAt`, or `startedAt` if no turns yet) is within 30 minutes of now`
(research.md "Conversation model"). A new query with no open conversation starts a new row.

**New index**: `(user_id, started_at)` to find a caller's most recent conversation quickly.

**Retention**: rows (cascading their turns) older than 90 days past their effective end are
deleted by `ConversationRetentionJob` (FR-035).

## Chatbot Turn (NEW — implements spec's "Chatbot Query"/"Chatbot Response")

One row per query+response pair within a conversation.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated at creation. |
| `conversationId` | UUID | Not null. References `chatbot_conversations.id`. |
| `queryText` | `TEXT` | Not null, non-blank (FR-022). |
| `responseText` | `TEXT` | Not null — either a grounded resolution (FR-024) or the no-match message (FR-028). |
| `sourceTicketIds` | `UUID[]` | The knowledge base entries' source tickets the response was grounded in (FR-025); empty when the response is a no-match. |
| `createdAt` | `TIMESTAMPTZ` | Not null; also the conversation's "last activity" marker. |

## Relationships

```text
User (1) ──< assigneeId       ── Ticket
User (1) ──< createdById      ── Ticket
User (1) ──< authorId         ── Comment
User (1) ──< userId           ── Chatbot Conversation
Ticket (1) ──< ticketId       ── Comment                      (unchanged from 001)
Ticket (1) ──< ticketId (0..1)── Knowledge Base Entry
Chatbot Conversation (1) ──< conversationId ── Chatbot Turn
```

## Migrations (Flyway, additive/forward-only per constitution Principle IV)

- **`V3__create_users_table.sql`**: `users` table, case-insensitive unique index on `email`,
  index on `role`.
- **`V4__ticket_ownership_and_assignment.sql`**: `tickets.created_by_id` (`NOT NULL`, FK),
  `tickets.assignee_id` (nullable, FK); drops old `tickets.assignee` text column;
  `idx_tickets_assignee_id_status`, `idx_tickets_created_by_id`.
- **`V5__comment_author.sql`**: `comments.author_id` (`NOT NULL`, FK).
- **`V6__enable_pgvector.sql`**: `CREATE EXTENSION IF NOT EXISTS vector;`
- **`V7__create_knowledge_base_entries.sql`**: `knowledge_base_entries` table (including the
  `vector` column) and its HNSW index.
- **`V8__create_chatbot_conversations.sql`**: `chatbot_conversations` table and its
  `(user_id, started_at)` index.
- **`V9__create_chatbot_turns.sql`**: `chatbot_turns` table, FK to `chatbot_conversations`,
  `ON DELETE CASCADE` (so retention cleanup deleting a conversation removes its turns in one
  statement).

Both new not-null foreign keys in `V4`/`V5` are added directly as `NOT NULL` — no ticket/comment
data predates this feature (confirmed: no `User`/auth code exists in the codebase yet).
