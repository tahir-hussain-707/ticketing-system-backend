# Bug Fix: Flyway migration V5__comment_author.sql fails on deploy (NOT NULL violation)

- **Slug**: comment-author-null-migration
- **Fixed**: 2026-09-24
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

`V5__comment_author.sql` now backfills existing `comments` rows with a system user before enforcing `NOT NULL` on `author_id`, mirroring the pattern already used by `V4__ticket_ownership_and_assignment.sql`. A regression test was added that runs the real Flyway migration chain against a pre-existing (legacy) comment row.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `src/main/resources/db/migration/V5__comment_author.sql` | modified | Insert system user (`00000000-0000-0000-0000-000000000001`, `ON CONFLICT DO NOTHING`) and `UPDATE comments SET author_id = ... WHERE author_id IS NULL` before `ALTER COLUMN author_id SET NOT NULL` |
| `src/test/java/com/frequency/ticketing/migration/CommentAuthorBackfillMigrationTest.java` | added | Migrates a fresh Postgres container to V4, inserts a legacy ticket+comment row (no `author_id`), migrates to latest, asserts no exception and `author_id` backfilled to the system user |

## Diff Highlights

```sql
ALTER TABLE comments ADD COLUMN author_id UUID REFERENCES users (id);

-- Backfill pre-existing comment rows with the system user before enforcing NOT NULL,
-- so this migration is safe on environments that already have comment data
-- (same pattern as V4__ticket_ownership_and_assignment.sql).
INSERT INTO users (id, name, email, password_hash, role, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'System', 'system@frequency.local', 'N/A', 'ADMIN', now(), now())
ON CONFLICT (id) DO NOTHING;

UPDATE comments
SET author_id = '00000000-0000-0000-0000-000000000001'
WHERE author_id IS NULL;

ALTER TABLE comments ALTER COLUMN author_id SET NOT NULL;
```

## Tests Added or Updated

- `src/test/java/com/frequency/ticketing/migration/CommentAuthorBackfillMigrationTest.java::migrationBackfillsPreExistingCommentsInsteadOfFailing` — pins down that migrating a database with pre-existing `comments` rows (created before `author_id` existed) succeeds and backfills `author_id` to the system user, instead of throwing the NOT NULL violation from the bug report.

## Local Verification

- Commands run: `./mvnw -q test-compile` → success, no compile errors in the new test or the migration change.
- Commands attempted, blocked by sandbox: `./mvnw -Dtest=CommentAuthorBackfillMigrationTest test` → failed with `Could not find a valid Docker environment` (Testcontainers needs the Docker socket; this sandbox denies both direct socket access and the sandbox-bypass needed to reach it). **The new test has not actually been executed** — only compiled. Please run it in an environment with Docker access before merging: `./mvnw -Dtest=CommentAuthorBackfillMigrationTest test`.
- Manual checks: traced the SQL by hand against the V1/V3/V4 schema states (see assessment) — the backfill UPDATE and INSERT statements are syntactically and semantically identical in shape to the already-working V4 pattern.

## Deviations from Assessment

None — implemented the assessment's preferred remediation as written (system-user backfill), not the alternative (attribute to ticket's `created_by_id`).

## Follow-ups

- Run `CommentAuthorBackfillMigrationTest` in CI or any Docker-enabled environment to confirm it passes before this is considered verified (flagged above under Local Verification).
- If any environment already failed partway through the old `V5__comment_author.sql` (Flyway schema history shows it as failed), the corrected script alone won't repair that history — may need `flyway repair` or manual schema_history cleanup there, per the assessment's Risks section.
