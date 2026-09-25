# Ticketing System

Authenticated support-ticket management backend with an AI resolution chatbot — Java 25,
Spring Boot 3.5, PostgreSQL (`pgvector`), Ollama.

See [`specs/005-auth-rag-chatbot/`](specs/005-auth-rag-chatbot/) for the current spec, plan, data
model, API contracts, and task list (supersedes `specs/003-rag-resolution-chatbot/` and
`specs/004-auth-ticket-access/` — see that spec's header for why), and
[`.specify/memory/constitution.md`](.specify/memory/constitution.md) for the project's governing
engineering rules.

## Features

- **Role-based login** — email/password authentication with `ADMIN`, `SUPPORT`, and `GENERAL`
  roles; session-cookie based; explicit logout. Every API this service exposes requires
  authentication except login itself.
- **Ticket management** — create, view, update, and list tickets with title/description/priority,
  full status lifecycle enforcement, and pagination.
- **Automatic least-loaded assignment** — new tickets are auto-assigned to the `SUPPORT` user
  with the fewest open (`OPEN`/`IN_PROGRESS`) tickets; ties break by a stable, repeatable rule.
  `ADMIN` can manually reassign afterward; no other role can.
- **Ownership-scoped listing** — filter the ticket list by `mine`, `assigned`, or `all` (narrowed
  to `mine` server-side for `GENERAL` callers), combinable with keyword/status filters.
- **Creator/assignee-only commenting** — only a ticket's creator or its assigned `SUPPORT` user
  can comment; every comment is attributed by name.
- **AI resolution chatbot** — a logged-in user describes an issue in their own words; the chatbot
  retrieves semantically similar *resolved* tickets (via `pgvector` embeddings) and answers with
  an LLM-grounded, customer-safe resolution — never raw internal comment text. Supports
  multi-turn conversations (30-minute inactivity timeout or explicit end), cites the ticket(s) it
  grounded its answer in, and clearly says so when it finds no confident match instead of
  fabricating one.
- **Self-updating knowledge base** — every ticket that reaches `RESOLVED` is automatically indexed
  into the chatbot's knowledge base; no manual curation step.

## Prerequisites

- Java 25 (JDK)
- Docker (for local PostgreSQL and for the Testcontainers-backed test suite)
- PostgreSQL must have the `pgvector` extension available (used by the RAG/chatbot migrations,
  `V6__enable_pgvector.sql` onward) — plain `postgres:16` does not include it; use a pgvector-enabled
  image such as `pgvector/pgvector:pg16` as shown below.
- [Ollama](https://ollama.com) running locally (or reachable at `OLLAMA_BASE_URL`), with the
  embedding and chat models pulled — default `mxbai-embed-large` and `llama3.1`
  (`ollama pull mxbai-embed-large && ollama pull llama3.1`). No API key needed; it's a local/
  self-hosted model, not an external SaaS call.

No local Maven install is required — use the committed wrapper (`./mvnw`).

## Deployment / Run locally

1. Start PostgreSQL (with `pgvector`):

   ```bash
   docker run --name ticketing-postgres -e POSTGRES_DB=ticketing \
     -e POSTGRES_USER=ticketing -e POSTGRES_PASSWORD=ticketing \
     -p 5432:5432 -d pgvector/pgvector:pg16
   ```

2. Start Ollama and pull the models this service uses:

   ```bash
   ollama serve &
   ollama pull mxbai-embed-large
   ollama pull llama3.1
   ```

3. Start the application (Flyway applies the schema automatically on startup):

   ```bash
   ./mvnw spring-boot:run
   ```

4. Confirm it's up:

   ```bash
   curl -s localhost:8080/actuator/health
   ```

5. API docs (Swagger UI): `http://localhost:8080/swagger-ui.html`

See [`specs/005-auth-rag-chatbot/quickstart.md`](specs/005-auth-rag-chatbot/quickstart.md) for a
full curl walkthrough of every user story, including login, ticket creation/assignment,
commenting, reassignment, transitions, and chatbot conversations.

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5432`, `ticketing`, `ticketing`, `ticketing` | PostgreSQL connection |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173` | Allowed browser origins for `/api/v1/**` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server for embeddings/chat |
| `OLLAMA_EMBEDDING_MODEL` | `mxbai-embed-large` | Model used to embed ticket/query text |
| `OLLAMA_CHAT_MODEL` | `llama3.1` | Model used to generate chatbot responses |
| `CHATBOT_SIMILARITY_THRESHOLD` | `0.75` | Minimum similarity to count as a confident match |
| `CHATBOT_RETENTION_DAYS` | `90` | How long ended conversations are retained |
| `CHATBOT_INACTIVITY_MINUTES` | `30` | Idle timeout that ends a conversation |
| `CHATBOT_RETRIEVAL_LIMIT` | `5` | Max resolved tickets retrieved per query |

See `src/main/resources/application.yml` for the full set.

## Run the tests

```bash
./mvnw test
```

Requires Docker — the full suite (contract, integration, and the state-machine/concurrency/
restart-durability/search-performance tests) runs against a real PostgreSQL instance via
Testcontainers (constitution Principle III: no in-memory database substitute). Pure unit tests
(`src/test/.../unit/`) need no Docker.

## Project structure

```text
src/main/java/com/frequency/ticketing/
├── web/            # controllers, DTOs, global exception handling
├── domain/         # entities, the ticket state machine, business rules, chatbot/RAG logic
├── repository/     # Spring Data JPA repositories
└── config/         # cross-cutting config (security, CORS, chatbot, logging, OpenAPI)

src/main/resources/db/migration/   # Flyway-versioned schema (source of truth — no ddl-auto)

src/test/java/com/frequency/ticketing/
├── contract/       # HTTP-contract tests per endpoint
├── integration/    # full-stack tests (Testcontainers PostgreSQL)
└── unit/           # pure unit tests, no Spring context
```

## Ticket management rules

- **Auto-assignment only.** A caller can never supply an assignee at creation time — the system
  always assigns the ticket itself, to whichever `SUPPORT` user currently has the fewest tickets
  in `OPEN`/`IN_PROGRESS` status. If no `SUPPORT` user exists yet, the ticket is still created,
  left unassigned, and that fact is visible on retrieval.
- **`ADMIN`-only reassignment.** Only an `ADMIN` can manually change a ticket's assignee after
  creation; any other role attempting it is rejected with an authorization error.
- **Status changes only via the state machine.** A ticket's status can never be set by a direct
  field update — only through the transition endpoint, and only along an allowed edge (see
  diagram below). Any other transition is rejected with `409 Conflict`.
- **Commenting is restricted.** Only the ticket's creator or its currently assigned `SUPPORT` user
  can add a comment; every other logged-in user is rejected. Every comment is attributed by name.
- **View access is ownership-scoped.** A single ticket (or its comments) can only be viewed by its
  creator, its assignee, or a `SUPPORT`/`ADMIN` user. The `GENERAL` role never sees tickets that
  aren't theirs, even when explicitly requesting `scope=all`.
- **Everything requires login.** Every endpoint in this service — ticket, comment, and chatbot
  APIs alike — rejects unauthenticated callers, except the login request itself.

## Ticket state diagram

Enforced server-side by the single state-machine component
(`TicketStatusTransitionPolicy`) — no other code path may change a ticket's status:

```text
                 ┌──────────────┐
        ┌───────▶│ IN_PROGRESS  │───────┐
        │        └──────────────┘       │
        │                │              │
   ┌────────┐            ▼         ┌─────────┐
   │  OPEN  │       ┌──────────┐   │CANCELLED│
   │        │──────▶│ RESOLVED │   └─────────┘
   └────────┘       └──────────┘
        │                 │
        │                 ▼
        │           ┌──────────┐
        └──────────▶│  CLOSED  │◀── (only reachable from RESOLVED)
                     └──────────┘
```

Allowed transitions only:

| From | To |
|---|---|
| `OPEN` | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | `RESOLVED`, `CANCELLED` |
| `RESOLVED` | `CLOSED` |
| `CLOSED` | *(terminal — no transitions out)* |
| `CANCELLED` | *(terminal — no transitions out)* |

A ticket entering `RESOLVED` is what triggers it becoming part of the chatbot's knowledge base; a
ticket that later leaves `RESOLVED` (there is no path back to it once `CLOSED`) is never used as
grounding while not in that state.

## Engineering rules

This service follows `.specify/memory/constitution.md` — notably: the ticket status lifecycle is
enforced server-side only (never trust client-supplied status), constructor injection only,
Flyway-only schema changes, contract-first API design with a single consistent JSON error shape
for every rejected request, and the backend as sole authority for every domain invariant
including authentication/authorization.
