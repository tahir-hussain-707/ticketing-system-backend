# Bug Assessment: Flyway V5__comment_author.sql fails with "column already exists" after manual history reset

- **Slug**: flyway-v5-column-already-exists
- **Created**: 2026-09-24
- **Source**: pasted text (application startup log)
- **Verdict**: likely valid, needs reproduction
- **Severity**: high

## Report (verbatim or summarized)

User: "I had removed v5 from DB and redeployed." Application now fails to start with a different error than before:

```
Script V5__comment_author.sql failed
SQL State  : 42701
Error Code : 0
Message    : ERROR: column "author_id" of relation "comments" already exists
Location   : db/migration/V5__comment_author.sql
Line       : 1
```

## Symptom

`ALTER TABLE comments ADD COLUMN author_id ...` (line 1 of `V5__comment_author.sql`) fails because `comments.author_id` already exists in the target database, even though Flyway believes `V5` has not yet been applied (otherwise it would not attempt to re-run the script). Expected: migration applies cleanly, or fails clearly with actionable guidance when Flyway's history and the actual schema disagree.

## Reproduction

1. Deploy code containing `V5__comment_author.sql` and let it either succeed, or partially apply the `author_id` column to `comments` (e.g. an earlier attempt, or a manual `ALTER TABLE` run to unblock the earlier NOT NULL failure — see `comment-author-null-migration`).
2. "Remove V5 from DB" — [NEEDS CLARIFICATION: exact action taken. Most likely, delete the `flyway_schema_history` row for version 5 (a common manual "undo" so Flyway will retry the script), without actually reverting the schema (`DROP COLUMN author_id`).]
3. Redeploy. Flyway sees no history row for V5, so it re-executes `V5__comment_author.sql` from line 1: `ALTER TABLE comments ADD COLUMN author_id ...` fails with 42701 because the column is already there.

## Suspected Code Paths

- `src/main/resources/db/migration/V5__comment_author.sql:1` — `ALTER TABLE comments ADD COLUMN author_id UUID REFERENCES users (id);` is not idempotent: it always assumes the column does not yet exist, with no `IF NOT EXISTS` guard.
- Postgres DDL is transactional by default and Flyway runs each migration script in a single transaction (no `spring.flyway.*` override for non-transactional execution in `src/main/resources/application.yml:19-21`), so a genuine mid-script failure (like the original NOT NULL violation) should have rolled the `ADD COLUMN` back automatically. The fact that the column now exists despite no recorded successful V5 run means either (a) an earlier run of this script did succeed and only the history row was later removed, or (b) the column was added by hand outside Flyway. Both point to the database's actual schema and Flyway's `flyway_schema_history` table having been made to disagree with each other by a manual intervention, not by the application code.

## Root Cause Hypothesis

Confidence: medium. This is not a new defect in the SQL logic itself — the previously-reported NOT NULL bug is already fixed (`comment-author-null-migration`). The trigger here is environment/state drift: something (most plausibly manually deleting the V5 row from `flyway_schema_history`) made Flyway attempt to re-run a migration whose DDL had already taken effect on this specific database. `V5__comment_author.sql` has no defense against being re-run against a schema that already has the column, so it fails hard instead of no-op'ing or giving an actionable message.

## Proposed Remediation

**Preferred**: Do not fix this by editing `flyway_schema_history` by hand again — that is what caused the drift. Instead:
1. Inspect the target database's actual `comments` table shape and its `flyway_schema_history` rows before touching anything further, to confirm whether `author_id` is fully populated/NOT NULL already (i.e., the migration's *effects* already happened) or only the column exists without the backfill/NOT NULL step.
2. If the migration's effects are already fully present, use `flyway repair` (or `FlywayMigrationStrategy`/manual `INSERT` of the correct history row) to make Flyway's history match reality, rather than re-running the script.
3. Separately, harden `V5__comment_author.sql` to be safe to re-run in a history-drift scenario: change line 1 to `ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_id UUID REFERENCES users (id);`. The subsequent backfill `UPDATE ... WHERE author_id IS NULL` and `ALTER COLUMN ... SET NOT NULL` are already idempotent (a no-op if there's nothing to update / the column is already NOT NULL), so this one change makes the whole script safely re-runnable.

**Alternatives**:
- Leave the script as-is and treat this purely as an operational/runbook issue (document "never delete `flyway_schema_history` rows manually — use `flyway repair`"). Trade-off: doesn't protect against the next person making the same mistake, and the fix is a one-line, low-risk change.

**Files likely to change**:
- `src/main/resources/db/migration/V5__comment_author.sql` (add `IF NOT EXISTS` to the `ADD COLUMN` statement)

**Tests to add or update**:
- Extend `CommentAuthorBackfillMigrationTest` (or add a sibling test) that runs `V5__comment_author.sql` twice against the same database — once as part of the normal chain, then re-applies just V5's SQL directly (simulating a history-row deletion) — and asserts it does not throw.

## Risks & Considerations

- This assessment cannot confirm the exact state of the affected database (its `flyway_schema_history` contents or whether `author_id` is fully backfilled) without direct DB access, which was not provided — see Open Questions.
- Adding `IF NOT EXISTS` treats a symptom (Flyway/schema drift) rather than the root cause (someone bypassing Flyway's own recovery tooling). The team should confirm how the "removed V5 from DB" step was actually performed so this doesn't recur on other migrations that lack similar guards.
- If `author_id` already exists but is only partially backfilled (some legacy rows still NULL), the `IF NOT EXISTS` fix alone is enough — the existing backfill `UPDATE ... WHERE author_id IS NULL` still runs and the `SET NOT NULL` still applies correctly.

## Open Questions

- [NEEDS CLARIFICATION: What exactly did "removed v5 from DB" involve — deleting the `flyway_schema_history` row for version 5, dropping the `author_id` column, or something else? This determines whether the fix is the `IF NOT EXISTS` guard, a `flyway repair`, or both.]
- [NEEDS CLARIFICATION: Is `comments.author_id` in this database currently NOT NULL and fully backfilled, or only added (nullable, no backfill)? Confirms whether any data is still at risk.]
