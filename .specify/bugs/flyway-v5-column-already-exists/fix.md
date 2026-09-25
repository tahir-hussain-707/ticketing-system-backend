# Bug Fix: Flyway V5__comment_author.sql fails with "column already exists" after manual history reset

- **Slug**: flyway-v5-column-already-exists
- **Fixed**: 2026-09-24
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

User confirmed the "removed v5 from DB" step was deleting the `flyway_schema_history` row for version 5, while `comments.author_id` itself was left in place — exactly the drift scenario the assessment flagged. Applied the assessment's preferred fix: made `V5__comment_author.sql`'s `ADD COLUMN` idempotent with `IF NOT EXISTS`, and added a regression test that simulates the history-row deletion.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `src/main/resources/db/migration/V5__comment_author.sql` | modified | `ALTER TABLE comments ADD COLUMN author_id ...` → `ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_id ...`, with a comment explaining why |
| `src/test/java/com/frequency/ticketing/migration/CommentAuthorBackfillMigrationTest.java` | added test | New `migrationIsSafeToRerunAfterHistoryRowIsDeleted` test: migrates to latest, deletes the `flyway_schema_history` row for version 5, re-migrates, asserts no exception |

## Diff Highlights

```sql
-- IF NOT EXISTS: makes this script safe to re-run if flyway_schema_history's row for this
-- version is ever removed/repaired while the column itself already exists on the target DB.
ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_id UUID REFERENCES users (id);
```

```java
@Test
void migrationIsSafeToRerunAfterHistoryRowIsDeleted() throws Exception {
  Flyway.configure()...load().migrate();
  // DELETE FROM flyway_schema_history WHERE version = '5'
  Flyway rerun = Flyway.configure()...load();
  assertThatCode(rerun::migrate).doesNotThrowAnyException();
}
```

## Tests Added or Updated

- `src/test/java/com/frequency/ticketing/migration/CommentAuthorBackfillMigrationTest.java::migrationIsSafeToRerunAfterHistoryRowIsDeleted` — pins down that deleting V5's `flyway_schema_history` row and letting Flyway re-run the script (with `author_id` already present) succeeds instead of throwing the 42701 "column already exists" error from this bug report.
- Existing `migrationBackfillsPreExistingCommentsInsteadOfFailing` (from `comment-author-null-migration`) is unaffected — `IF NOT EXISTS` doesn't change behavior for the normal (non-drift) migration path.

## Local Verification

- Commands run: `./mvnw -q test-compile` → success, no compile errors.
- Commands attempted, blocked by sandbox (same limitation as `comment-author-null-migration/fix.md`): `./mvnw -Dtest=CommentAuthorBackfillMigrationTest test` → `Could not find a valid Docker environment`; Testcontainers needs Docker socket access, which this sandbox denies (and denied the sandbox-bypass request too). **Neither the new test nor the existing one has actually been executed here** — only compiled. Run in a Docker-enabled environment before merging: `./mvnw -Dtest=CommentAuthorBackfillMigrationTest test`.
- Manual checks: confirmed `ADD COLUMN IF NOT EXISTS` is valid PostgreSQL syntax (supported since PG 9.6) and is a no-op (not an error) when the column already exists, regardless of its current nullability — so it composes correctly with the existing backfill `UPDATE ... WHERE author_id IS NULL` and `ALTER COLUMN ... SET NOT NULL`, both of which are already idempotent.

## Deviations from Assessment

None — user confirmed the exact drift scenario (history row deleted, column left in place) that the assessment's preferred remediation targets. Implemented `IF NOT EXISTS` as proposed; did not pursue the `flyway repair` alternative since that's an operational action on the affected database, not a code change.

## Follow-ups

- Run `CommentAuthorBackfillMigrationTest` (both test methods) in a Docker-enabled environment to confirm they pass — flagged above under Local Verification.
- Per the assessment's Risks section: document for the team that manually deleting `flyway_schema_history` rows is unsafe and `flyway repair` (or a proper rollback migration) should be used instead — this fix makes V5 specifically resilient to it, but other migrations still assume history and schema stay in sync.
