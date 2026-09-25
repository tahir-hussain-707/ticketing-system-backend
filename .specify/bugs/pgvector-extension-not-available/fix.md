# Bug Fix: V6__enable_pgvector.sql fails — "extension \"vector\" is not available"

- **Slug**: pgvector-extension-not-available
- **Fixed**: 2026-09-24
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

The failure was caused by a stale README instruction to run plain `postgres:16` for local Postgres, which lacks the pgvector extension the V6–V9 (RAG/chatbot) migrations require. Updated `README.md` to provision `pgvector/pgvector:pg16` instead, and added a Prerequisites note calling out the pgvector requirement — matching what the test suite already uses (`AbstractIntegrationTest`).

## Changes

| File | Change | Notes |
|------|--------|-------|
| `README.md` | modified | Prerequisites: added a bullet noting the pgvector extension requirement since `V6__enable_pgvector.sql`. "Run locally" step 1: `docker run ... -d postgres:16` → `docker run ... -d pgvector/pgvector:pg16` |

## Diff Highlights

```diff
 - Java 25 (JDK)
 - Docker (for local PostgreSQL and for the Testcontainers-backed test suite)
+- PostgreSQL must have the `pgvector` extension available (used by the RAG/chatbot migrations,
+  `V6__enable_pgvector.sql` onward) — plain `postgres:16` does not include it; use a pgvector-enabled
+  image such as `pgvector/pgvector:pg16` as shown below.
```

```diff
   docker run --name ticketing-postgres -e POSTGRES_DB=ticketing \
     -e POSTGRES_USER=ticketing -e POSTGRES_PASSWORD=ticketing \
-    -p 5432:5432 -d postgres:16
+    -p 5432:5432 -d pgvector/pgvector:pg16
```

## Tests Added or Updated

- None, per the assessment: this is a documentation-only fix in this repo, and the existing `AbstractIntegrationTest` (which already provisions `pgvector/pgvector:pg16` and runs every integration test against migrations V1–V9) already proves the migrations apply cleanly given the correct image. There's no way to test "a real deploy target uses the right Postgres image" from within this codebase.

## Local Verification

- Commands run: none applicable — no code/test files changed, only `README.md` prose. Manually re-read the edited sections to confirm the command is syntactically valid Docker CLI and consistent with `AbstractIntegrationTest.java:44-46`'s image choice.
- Manual checks: confirmed `pgvector/pgvector:pg16` is the exact image string already used by `src/test/java/com/frequency/ticketing/support/AbstractIntegrationTest.java:45`, so local dev and the test suite are now consistent.

## Deviations from Assessment

None. Implemented the assessment's preferred remediation as written (README-only fix); did not attempt the infra-side alternative (installing pgvector on a non-README-provisioned deploy target), since that's outside this repository's control per the assessment's Risks section.

## Follow-ups

- If the actual failing deploy target is provisioned via infra not present in this repo (Terraform, Helm, managed cloud Postgres — see the assessment's Open Question), this README fix alone won't unblock it. Confirm how that environment is actually provisioned and apply the equivalent change there (enable/install the pgvector extension on that Postgres instance).
