# Phase 0 Research: Authenticated Ticketing with RAG Resolution Chatbot

Technical Context has no unresolved `NEEDS CLARIFICATION` — the constitution and 001/002's own
research already fix the base stack. This research covers two groups of decisions: the
authentication/authorization layer (carried over from the now-superseded 004 plan, still valid
against this merged spec, updated for the widened FR-006/new FR-037), and the RAG chatbot itself
(new — 003 was never planned).

## Part A — Authentication & Access Control

### Authentication mechanism

- **Decision**: `spring-boot-starter-security`, with a custom `POST /api/v1/auth/login` JSON
  endpoint that authenticates via `AuthenticationManager` and saves the resulting
  `SecurityContext` into the servlet `HttpSession`, plus `POST /api/v1/auth/logout` to invalidate
  it. Passwords hashed with `BCryptPasswordEncoder`.
- **Rationale**: One dependency already in the Spring Boot BOM satisfies FR-001–004/008. Nothing
  in the spec calls for statelessness, mobile clients, or third-party identity federation.
- **Alternatives considered**: JWT — rejected, solves a horizontal-scalability problem the spec
  never raises. OAuth2/OIDC — rejected; the constitution previously removed its OAuth2 rule by
  explicit amendment.

### Session storage

- **Decision**: The default in-process servlet `HttpSession`.
- **Rationale**: Simplest option; session lifetime/expiry is explicitly a planning decision per
  spec.md Assumptions, and nothing in scope requires more than the single deployable this already
  is.
- **Alternatives considered**: Spring Session/JDBC — rejected for now; the correct upgrade only
  if/when this service is horizontally scaled, which no requirement establishes today.

### Endpoint access rules (updated for the widened FR-006)

- **Decision**: `SecurityConfig` is now genuinely default-deny: `permitAll` on `POST
  /api/v1/auth/login` and the operational endpoints outside this feature's scope (`GET
  /actuator/health`, `GET /actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`); `anyRequest()
  .authenticated()` for everything else — ticket creation, view, list, update, transition,
  reassignment, comment creation and retrieval, and every chatbot endpoint.
- **Rationale**: FR-006 (as amended 2026-09-22) states this directly: "No API this feature
  exposes is reachable by an unauthenticated caller other than login." This removes the
  now-superseded 004 plan's special-cased `permitAll` for `GET /tickets/{id}` and `GET
  /tickets/{id}/comments` — those two endpoints are no longer exceptions.
- **Alternatives considered**: An explicit allow-list naming only the endpoints spec FRs mention
  by name — rejected; FR-006's own wording is the blanket rule, not a per-endpoint enumeration,
  and a narrower matcher would silently miss future endpoints (exactly the gap the amendment was
  written to close).

### View authorization (NEW — FR-037)

- **Decision**: Authentication (via `SecurityConfig`) answers "is there a caller"; a separate,
  data-dependent check in `TicketService.getById`/`getDetailById` (and the comment-list path,
  which loads the parent ticket first) answers "may *this* caller see *this* ticket" — allowed
  when the caller is the ticket's `createdById`, its `assigneeId`, or has role `SUPPORT`/`ADMIN`;
  otherwise a `ForbiddenActionException` (403).
- **Rationale**: Constitution Principle II: business rules a route matcher can't express (which
  specific ticket, which specific caller) belong in the domain layer. Exactly the same pattern
  already used for comment-creation authorization (FR-014) and reassignment authorization
  (FR-013).
- **Alternatives considered**: Restricting `SUPPORT` to only tickets assigned to them (not
  blanket view) — rejected; spec.md's Assumptions already establish `SUPPORT`/`ADMIN` see "every
  ticket" for *listing* (FR-016's "all" scope), so restricting single-ticket *view* more tightly
  than listing would be an inconsistent, unrequested rule.

### Authorization model (general)

- **Decision**: Purely role-based checks (e.g. "must be `ADMIN`" for reassignment) are Spring
  Security matcher/`@PreAuthorize` rules. Checks that depend on *which* ticket is involved
  (comment authorship, view authorization) are enforced in `TicketService`/`CommentService`.
- **Rationale**: Same constitution Principle II reasoning as above.

### Data model: assignee and comment authorship become real identities

- **Decision**: `tickets.assignee` (free-text) is replaced with `assignee_id` (nullable UUID FK
  to `users`); `tickets` gains `created_by_id` (UUID FK, not null); `comments` gains `author_id`
  (UUID FK, not null).
- **Rationale**: Constitution Principle IV ("foreign keys on all relationships"); the only way to
  enforce FR-009/FR-010 (assignee must be a real `SUPPORT` user) and FR-014/FR-015 (comment
  authorship must be a real, named user) at the database level.
- **Migration note**: Both new not-null foreign keys are added directly as `NOT NULL` — this
  codebase has no ticket/comment data predating this feature in any environment the migration
  runs against (only 001/002 have shipped; confirmed no `User`/auth code exists yet).

### Auto-assignment algorithm

- **Decision**: `UserRepository` exposes a query: among `role = 'SUPPORT'` users, count each
  one's tickets where `assignee_id = user.id AND status IN ('OPEN','IN_PROGRESS')`, order by that
  count ascending then `id` ascending, take the first row. Same query backs
  `TicketService.create()`'s assignment step inside the ticket's own `@Transactional` block.
- **Rationale**: Directly implements FR-010 (workload = `OPEN`+`IN_PROGRESS`) and FR-011
  (tie-break by stable, deterministic order). A composite index on `tickets (assignee_id,
  status)` keeps the aggregate cheap.
- **Concurrency trade-off**: Two tickets created in true-concurrent transactions could both read
  the same "least-loaded" user before either commits. Accepted as a soft heuristic, not a
  correctness invariant — SC-002 is verified by sequential test scenarios. Adding row-level
  locking for this is unjustified complexity per constitution Principle V.

### Manual reassignment

- **Decision**: A dedicated `PATCH /api/v1/tickets/{ticketId}/assignee`, `ADMIN`-only, taking
  `{ assigneeId }`. The general `PATCH /api/v1/tickets/{ticketId}` body no longer accepts
  `assignee` at all.
- **Rationale**: FR-013 makes `ADMIN` reassignment the *only* legal way to change an assignee
  after creation; keeping it in the general update DTO would let any ticket-updater silently
  change the assignee too.

### Listing scope semantics

- **Decision**: `GET /api/v1/tickets` gains `scope` (`mine`, `assigned`, `all`; default `all`).
  `all` always means "every ticket the caller is permitted to see" — for `SUPPORT`/`ADMIN` that's
  every ticket; for `GENERAL` it narrows to tickets they created. `assigned` for a `GENERAL`
  caller returns an empty page, not an error.
- **Rationale**: Matches FR-016 and spec.md Assumptions exactly.

### Seed users

- **Decision**: Demo/seed `User` rows (one `ADMIN`, two `SUPPORT`, one `GENERAL`) via a
  `@Profile("dev")` `CommandLineRunner`, never a Flyway migration.
- **Rationale**: Constitution Principle V forbids committing a credential "in any form... test
  fixtures... example config" — a Flyway migration runs identically in every environment,
  including a future production one; a dev-only seeder never executes there.

### CORS and CSRF

- **Decision**: `CorsConfig` → `allowCredentials(true)` (required for the session cookie
  cross-origin); origins stay an explicit allow-list. CSRF disabled — this is a JSON-only API
  with no server-rendered forms; the session cookie's `SameSite` attribute plus the strict CORS
  allow-list mitigate.

### Fixing the actor-logging gap (constitution Principle V)

- **Decision**: `TicketService.applyTransition`'s existing log line
  (`ticket_transition ticketId={} from={} to={}`) gains a fourth field, `actor={}`, populated
  from `CurrentUser`.
- **Rationale**: Constitution Principle V states plainly: "Every ticket state transition MUST be
  logged with ticket identifier, previous status, new status, **and actor**." This was
  unsatisfiable before any authentication existed (001/002 predate it); this feature is what
  makes an actor identity available, so this plan closes the gap rather than carrying it forward
  a third time.

## Part B — RAG Resolution Chatbot

### Vector storage: `pgvector` on the existing PostgreSQL vs. a dedicated vector database

- **Decision**: Enable the `vector` extension on the existing PostgreSQL instance (via the
  `pgvector` Postgres extension and the small `com.pgvector:pgvector` Java type-mapping library),
  and store each `KnowledgeBaseEntry`'s embedding as a native `vector` column with an HNSW index.
- **Rationale**: Constitution Principle IV names PostgreSQL as *the* single source of truth, and
  Principle V forbids a new service without a named, concrete problem it solves. At the spec's
  own stated scale (SC-009: up to 50,000 resolved tickets), `pgvector`'s HNSW index delivers
  sub-second approximate nearest-neighbor search — there is no concrete problem (throughput,
  latency, dataset size) that a dedicated vector database would solve here that `pgvector`
  doesn't already. It also means the knowledge base is backed up, migrated, and transactionally
  consistent with the ticket data it's derived from, using the exact same operational tooling
  (Flyway, `pg_dump`, Testcontainers) the rest of the service already relies on.
- **Alternatives considered**: A standalone vector database (Pinecone, Weaviate, Qdrant, etc.) —
  rejected; would be a new external service requiring its own availability story, its own backup/
  DR story, and its own dependency-vulnerability surface, all to solve a scale problem (50k
  vectors) `pgvector` already solves inside the existing database.

### Embedding generation

- **Decision (amended)**: Call a locally-run Ollama instance's `/api/embed` endpoint over plain
  HTTP (via Spring's `RestClient`, already available) from `HttpEmbeddingClient`, an
  implementation of the `EmbeddingClient` interface. Model: `mxbai-embed-large` (1024 dimensions —
  matches the `vector(1024)` column already migrated in V7, so no schema change was needed for
  this switch).
- **Rationale**: FR-019 requires semantic retrievability. Running the embedding model locally via
  Ollama removes the external-vendor dependency and per-call cost entirely, and keeps all ticket
  content (which may be customer-sensitive) from ever leaving the deployment environment — a
  stronger fit than a hosted API once local inference is acceptable for this deployment's
  hardware.
- **Alternatives considered (superseded)**: Voyage AI's hosted embeddings API — this was the
  original decision (see git history of this file); revisited and replaced with Ollama at the
  project's explicit request. A bespoke local model-serving stack outside Ollama — rejected;
  Ollama already packages model download, serving, and a stable HTTP API, so there is no
  additional "new service" complexity Principle V would need to justify beyond running the Ollama
  daemon itself.

### Response generation (the LLM call)

- **Decision (amended)**: Call the same local Ollama instance's `/api/chat` endpoint from
  `HttpResolutionLlmClient`, an implementation of `ResolutionLlmClient`. Default model:
  `llama3.1` (configurable via `app.ai.ollama.chat-model` with no code change). The system prompt
  constrains the model to: (a) answer only from the matched ticket(s)' comments/resolution
  content passed in as context, (b) never quote internal comment text verbatim — rephrase it
  (FR-031), (c) never include a customer's or agent's name, contact info, or internal identifier
  (FR-032), (d) implicitly stay within the provided context (keeps FR-028 strict — the model
  doesn't get to freelance a "confident match" the retrieval step didn't find, since retrieval
  already decided confidence before this call is ever made).
- **Rationale**: FR-024 requires the response be LLM-grounded on retrieved content, not merely
  keyword-stitched. Ollama's `/api/chat` accepts the same role-based message list shape (system/
  user/assistant) used to carry prior-turn context (FR-033/FR-034), so the client's structure is
  unchanged from the original hosted-API version — only the endpoint and model identifier moved.
- **Alternatives considered (superseded)**: Anthropic's Claude Messages API — this was the
  original decision; revisited and replaced with Ollama at the project's explicit request, for
  the same no-external-dependency/no-per-call-cost/data-locality reasoning as the embedding
  decision above. Quality/consistency of grounded, customer-safe rewording (FR-031) from a local
  model the size Ollama can practically run should be spot-checked before this goes to
  production; the system prompt's constraints are unchanged, but they are only as reliable as the
  model executing them.
- **New operational dependency this introduces**: the Ollama daemon must be running and have both
  models pulled (`ollama pull mxbai-embed-large`, `ollama pull llama3.1`) wherever this service
  runs — dev, CI, and any deployed environment. `app.ai.ollama.base-url` defaults to
  `http://localhost:11434`; point it at a shared Ollama host via `OLLAMA_BASE_URL` if this service
  and Ollama don't run on the same machine.

### Knowledge base indexing trigger

- **Decision**: `TicketService.applyTransition` publishes a `TicketResolvedEvent` (Spring
  `ApplicationEventPublisher`) when a ticket reaches `RESOLVED`. `KnowledgeBaseService` listens
  via `@TransactionalEventListener(phase = AFTER_COMMIT)` and, in a separate `@Async` method,
  calls `EmbeddingClient`, builds/refreshes the `KnowledgeBaseEntry`, and saves it in its own
  transaction.
- **Rationale**: FR-018 requires no manual step, but nothing in the spec requires the embedding
  call to complete before the ticket-transition HTTP response returns — decoupling it keeps a
  slow or briefly-failing embedding call from ever affecting the ticket transition's own latency
  or correctness. `AFTER_COMMIT` guarantees the source ticket's `RESOLVED` state is durable before
  indexing reads it.
- **Alternatives considered**: Synchronous indexing inside the same `@Transactional` block —
  rejected; holds a database connection open for the duration of an external HTTP call, and a
  transient embedding-API failure would then fail the ticket transition itself, which FR-018
  does not require and which would be a worse user experience for no benefit.

### Retrieval & grounding

- **Decision**: `ChatbotService` embeds the incoming query (same `EmbeddingClient`), runs a
  `pgvector` cosine-distance `ORDER BY ... LIMIT k` query via `KnowledgeBaseEntryRepository`
  filtered to entries with non-empty resolution content (FR-027), and treats a result "confident"
  only if its similarity clears a configurable threshold (`ChatbotProperties.similarityThreshold`,
  a conservative default, tunable without a code change). Below threshold → FR-028's no-match
  path, no LLM call made (saves cost, keeps the "honest" behavior server-side rather than
  hoping the prompt enforces it).
- **Rationale**: FR-023, FR-026, FR-027, FR-028 combined. Deciding "confident vs. not" before
  ever calling the LLM is simpler and cheaper than asking the LLM to self-report low confidence.
- **Alternatives considered**: Always calling the LLM and asking it to say "I don't know" when
  the context is weak — rejected; less reliable (an LLM can still fabricate) and strictly more
  expensive (an unnecessary generation call) than a numeric threshold check the code already has
  to compute anyway for ranking.

### Conversation model & the 30-minute inactivity boundary

- **Decision**: A `ChatbotConversation` has `startedAt` and (via its turns) an implicit
  `lastActivityAt`. On each incoming query, `ChatbotService` looks for the caller's open
  conversation (no explicit end, last turn within 30 minutes); if none, it starts a new one. No
  background job marks a conversation "ended" — ending is computed at query time.
- **Rationale**: FR-036's "explicit end or 30-minute inactivity, whichever first" only needs to
  be evaluated when a new query arrives — computing it lazily avoids a second scheduled job on
  top of the retention cleanup, and matches "start with the simplest design."
- **Alternatives considered**: A `@Scheduled` sweep that actively flips conversations to "ended"
  after 30 minutes — rejected; the state of an idle conversation has no observable effect between
  queries, so proactively updating it accomplishes nothing a lazy check doesn't already achieve.

### 90-day conversation retention

- **Decision**: A daily `@Scheduled` `ConversationRetentionJob` deletes `ChatbotConversation`
  rows (cascading their turns) whose effective end time is more than 90 days in the past.
- **Rationale**: FR-035. Spring's built-in `@Scheduled` needs no new dependency or external
  scheduler.

### External-API failure handling

- **Decision**: Both AI clients wrap their HTTP call in a try/catch; any failure (timeout,
  non-2xx, malformed response) is translated to `AiServiceUnavailableException`, mapped to `503`
  with a distinct `ApiError` code (FR-030). One bounded retry (single retry, short timeout) is
  applied to the embedding call only (idempotent, cheap); the generation call is not retried
  automatically (an LLM call is comparatively expensive and a user-visible retry is preferable to
  a silent doubled-cost one).
- **Alternatives considered**: A circuit breaker (e.g. Resilience4j) — rejected as unjustified
  complexity per constitution Principle V; nothing in the spec's scale (100 concurrent
  conversations) demands it, and a single try/catch plus a clear error already satisfies FR-030.

### Testing the AI integration without calling real external APIs

- **Decision**: `EmbeddingClient`/`ResolutionLlmClient` are interfaces; production wiring injects
  the `Http*` implementations, but `@SpringBootTest` configurations for chatbot tests override
  those beans with fixed-response test doubles (deterministic embeddings for known fixture text;
  a canned grounded/no-match response).
- **Rationale**: Constitution Principle III's Testcontainers mandate is specifically about
  *persistence* tests against a real PostgreSQL — it says nothing about third-party network
  calls, which would make CI flaky, slow, and costly if left real. `pgvector` similarity search
  itself **is** tested against the real extension (via the `pgvector/pgvector` Testcontainers
  image), keeping the part this codebase owns fully real while stubbing the part it doesn't.

## Outcome

No unresolved `NEEDS CLARIFICATION` markers. Ready for Phase 1.
