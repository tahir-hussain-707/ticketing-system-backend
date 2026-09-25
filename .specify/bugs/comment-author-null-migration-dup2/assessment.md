# Bug Assessment: Flyway migration V5__comment_author.sql fails on deploy (duplicate report)

- **Slug**: comment-author-null-migration-dup2
- **Created**: 2026-09-24
- **Source**: pasted text (same startup log pasted a third time)
- **Verdict**: invalid
- **Severity**: n/a

## Report (verbatim or summarized)

Same log as before: Flyway fails on `V5__comment_author.sql` with SQL State 23502, `column "author_id" of relation "comments" contains null values`.

## Symptom

Duplicate of an already-assessed and already-fixed bug.

## Reproduction

N/A — not reproducible against current code; see below.

## Suspected Code Paths

- `src/main/resources/db/migration/V5__comment_author.sql:1-14` — checked current file content: backfill (system-user insert + `UPDATE comments SET author_id = ... WHERE author_id IS NULL`) already present before `ALTER COLUMN author_id SET NOT NULL`. Fix from `.specify/bugs/comment-author-null-migration/fix.md` is applied on this branch.

## Root Cause Hypothesis

Confidence: high. This is the same bug already tracked as `comment-author-null-migration` (assessed, fixed) and re-checked as `migrations-v7-v9-notnull-review`. The log the user pasted is stale — it predates the fix already applied in this working tree. No new defect.

## Proposed Remediation

**Preferred**: None. No code change — the fix is already in place. If this log is being seen from a real deploy (not just re-pasted), the deploy target is likely running an older build/artifact than this branch; rebuild and redeploy from current `HEAD` rather than changing code again.

**Alternatives**: N/A.

**Files likely to change**:
- None.

**Tests to add or update**:
- None new — `CommentAuthorBackfillMigrationTest` (added under `comment-author-null-migration`) already covers this. Note: that test could not be executed in the assessment/fix sandbox (no Docker access) — still needs a real run before full confidence.

## Risks & Considerations

- If this log is from an environment that already has a Flyway schema_history row marking `V5` as failed, redeploying the fixed script won't repair that row automatically — see the `Risks & Considerations` note in `comment-author-null-migration/assessment.md` (may need `flyway repair`).

## Open Questions

- [NEEDS CLARIFICATION: Is this log from a fresh deploy attempt on current code, or a resend of the original failure? If fresh, confirm the deployed artifact actually includes the current `V5__comment_author.sql`.]
