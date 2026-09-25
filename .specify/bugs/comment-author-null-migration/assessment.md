# Bug Assessment: Flyway migration V5__comment_author.sql fails on deploy (NOT NULL violation)

- **Slug**: comment-author-null-migration
- **Created**: 2026-09-24
- **Source**: pasted text (application startup log)
- **Verdict**: valid
- **Severity**: critical

## Report (verbatim or summarized)

Application fails to start. Spring context fails creating `entityManagerFactory` because Flyway migration fails:

```
Script V5__comment_author.sql failed
SQL State  : 23502
Error Code : 0
Message    : ERROR: column "author_id" of relation "comments" contains null values
Location   : db/migration/V5__comment_author.sql
Line       : 2
```

## Symptom

Deploy fails at startup. Flyway cannot apply `V5__comment_author.sql` on a database that already has rows in `comments` from before this migration. Expected: migration applies cleanly and app starts, same as it does on a fresh/empty database.

## Reproduction

1. Start from a DB where `V1__init_schema.sql` already ran and `comments` has ≥1 existing row (no `author_id` value, since column did not exist).
2. Run Flyway migrate up to `V5__comment_author.sql`.
3. Migration adds nullable `author_id` column, then immediately `ALTER TABLE comments ALTER COLUMN author_id SET NOT NULL` — fails with SQL State 23502 because existing rows have `author_id = NULL`.

## Suspected Code Paths

- `src/main/resources/db/migration/V5__comment_author.sql:1-2` — adds `author_id UUID REFERENCES users (id)` then sets it `NOT NULL` with no backfill step in between.
- `src/main/resources/db/migration/V4__ticket_ownership_and_assignment.sql:4-14` — sibling migration that adds `created_by_id` to `tickets` the same way, but backfills existing rows with a system user (`00000000-0000-0000-0000-000000000001`) before the `NOT NULL` constraint. V5 does not follow this established pattern.
- `src/main/java/com/frequency/ticketing/domain/comment/Comment.java:24` — entity declares `authorId` as `nullable = false`, consistent with the intended final schema; not itself the bug.

## Root Cause Hypothesis

Confidence: high. `V5__comment_author.sql` was written without the backfill step that the earlier, analogous migration (`V4`) already established as the safe pattern for adding a `NOT NULL` FK column to a table that may hold pre-existing rows. Any environment where `comments` had rows before V5 ran (staging/prod, or a dev DB seeded before this migration was added) hits the NOT NULL violation and Flyway aborts, blocking app startup entirely.

## Proposed Remediation

**Preferred**: Edit `V5__comment_author.sql` to insert the same system user used by V4 (`ON CONFLICT DO NOTHING`, so it's idempotent whether or not V4 already created it) and `UPDATE comments SET author_id = '00000000-0000-0000-0000-000000000001' WHERE author_id IS NULL` before the `ALTER COLUMN ... SET NOT NULL` statement — mirroring `V4__ticket_ownership_and_assignment.sql:4-13` exactly.

**Alternatives**:
- Backfill each existing comment's `author_id` with the ticket's `created_by_id` (from V4) instead of a generic system user, if attribution accuracy matters more than simplicity. Trade-off: more complex UPDATE (join to tickets), and tickets created before V4 also fall back to the system user, so it only partially improves accuracy.

**Files likely to change**:
- `src/main/resources/db/migration/V5__comment_author.sql`

**Tests to add or update**:
- An integration/Flyway test that seeds a `comments` row (raw insert, no `author_id`) at schema version V4, then migrates to latest and asserts success and that the row's `author_id` equals the system user id. If an existing contract test suite already runs full migrations against a seeded DB, extend it there instead of adding a new test class.

## Risks & Considerations

- Already-broken environments (where V5 partially failed) may have Flyway's schema history table marked as a failed migration; a fixed script alone won't auto-repair those — may need `flyway repair` or a manual history cleanup in addition to shipping the corrected SQL.
- Same system-user id constant appears in two migrations now (V4, V5); if it's ever needed again, worth confirming both stay in sync (no shared constant possible in raw SQL, so this must be kept manually consistent).
- No data-loss risk: backfill only sets a default value for rows that previously had no author, does not alter existing non-null data.

## Open Questions

- None — codebase already documents the correct pattern via V4.
