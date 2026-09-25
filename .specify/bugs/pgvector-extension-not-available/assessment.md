# Bug Assessment: V6__enable_pgvector.sql fails — "extension \"vector\" is not available"

- **Slug**: pgvector-extension-not-available
- **Created**: 2026-09-24
- **Source**: pasted text (application startup log)
- **Verdict**: valid
- **Severity**: high

## Report (verbatim or summarized)

Application fails to start. Flyway fails applying `V6__enable_pgvector.sql`:

```
Script V6__enable_pgvector.sql failed
SQL State  : 0A000
Message    : ERROR: extension "vector" is not available
  Detail: Could not open extension control file "/usr/share/postgresql/16/extension/vector.control": No such file or directory.
  Hint: The extension must first be installed on the system where PostgreSQL is running.
Location   : db/migration/V6__enable_pgvector.sql
Line       : 1
```

## Symptom

`CREATE EXTENSION IF NOT EXISTS vector;` fails because the target PostgreSQL server binary does not have the pgvector extension files installed at all (this is a server-side/OS-level gap, not a permissions or "already exists" issue — the `.control` file is simply missing). Expected: the extension is available so migration proceeds and the app starts, as it does against the project's own test/dev setup.

## Reproduction

1. Provision a PostgreSQL 16 server using the plain `postgres:16` image/binary (no pgvector extension compiled in).
2. Deploy this application against it with Flyway enabled (default: `src/main/resources/application.yml:19-21`).
3. Flyway reaches `V6__enable_pgvector.sql` and `CREATE EXTENSION IF NOT EXISTS vector;` fails because the extension's `.control` file doesn't exist on that server.

## Suspected Code Paths

- `src/main/resources/db/migration/V6__enable_pgvector.sql:1` — `CREATE EXTENSION IF NOT EXISTS vector;`. Correct SQL; the extension genuinely isn't installed on the target server, so no `IF NOT EXISTS` guard can help here (unlike the earlier `flyway-v5-column-already-exists` case).
- `README.md:18-24` — the documented **"Run locally"** step still says: `docker run ... -p 5432:5432 -d postgres:16`, i.e. the vanilla Postgres image with no pgvector extension. This is almost certainly how the target database in this report was provisioned — it's the only Postgres provisioning instruction in the repo, and it predates the V6–V9 pgvector/RAG-chatbot migrations.
- `src/test/java/com/frequency/ticketing/support/AbstractIntegrationTest.java:41-46` — by contrast, the test suite already knows this: it explicitly uses `pgvector/pgvector:pg16` (not `postgres:16-alpine`) "so migration V6's `CREATE EXTENSION vector` ... run against the real extension." The fix for local/deploy docs is to bring README in line with what tests already do.
- `src/main/resources/db/migration/V7__create_knowledge_base_entries.sql:7` — depends on the `vector` type existing, so this failure would recur immediately on V7 even if V6 were somehow skipped.

## Root Cause Hypothesis

Confidence: high. The RAG/chatbot feature (V6–V9) added a hard dependency on the pgvector Postgres extension, but the only Postgres provisioning instructions committed to the repo (`README.md`'s local run command) were never updated to use a pgvector-enabled image. Anyone following the documented setup — or any deploy pipeline mirroring it — provisions a vanilla `postgres:16` server that lacks the extension, so Flyway fails at V6 on first boot.

## Proposed Remediation

**Preferred**: Update `README.md`'s "Run locally" `docker run` command to use `pgvector/pgvector:pg16` instead of `postgres:16` (the same image the test suite already relies on), and add a one-line note under **Prerequisites** stating that the PostgreSQL server must have the `pgvector` extension available (not just plain PostgreSQL 16) since the chatbot/RAG feature. This is a documentation-only fix in this repo; it does not change any Flyway script.

**Alternatives**:
- For a real deployed (non-local) environment that isn't provisioned via this README (e.g. a managed cloud Postgres), the actual remediation is infra-side: install the pgvector extension on that server (most managed providers, e.g. RDS/Cloud SQL, support enabling it via their own extension allow-list) — outside this repo's control, so only a Risks/Open-Question note, not a code change.

**Files likely to change**:
- `README.md`

**Tests to add or update**:
- None applicable in this repo — the existing `AbstractIntegrationTest` already provisions `pgvector/pgvector:pg16` and every integration test transitively proves migrations V1–V9 apply cleanly against it. There's no way to unit-test "a real deploy target has the right Postgres image" from within this codebase.

## Risks & Considerations

- This is fundamentally an infrastructure/environment gap, not an application logic bug — fixing `README.md` only prevents it for people following that doc; a deploy target provisioned some other way (Terraform, Helm chart, managed DB) needs the same fix wherever *that* provisioning is defined, which is outside this repository.
- No data risk: this failure happens before any schema exists past V5, so there's no partially-migrated state to reconcile once the correct Postgres image/extension is in place.

## Open Questions

- [NEEDS CLARIFICATION: Is the failing target environment actually provisioned via this README's `docker run` command, or via some other infra (Terraform/Helm/managed cloud Postgres) not present in this repo? If the latter, the fix must happen in that other infra definition, and this README fix alone won't unblock the deploy.]
