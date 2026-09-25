# Tasks: Authenticated Ticketing with RAG Resolution Chatbot

**Input**: Design documents from `/specs/005-auth-rag-chatbot/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/auth-api.yaml,
contracts/ticket-ownership-api.yaml, contracts/chatbot-api.yaml, quickstart.md,
`.specify/memory/constitution.md`, and the already-implemented 001/002 codebase this feature
extends and amends. (003 was never planned; 004 was planned in isolation and is superseded —
neither contributes existing code.)

**Tests**: Constitution Principle III (Test-First) is NON-NEGOTIABLE — test tasks below are
REQUIRED and MUST be written and failing before their corresponding implementation task.

**Organization**: Tasks are grouped by user story (spec.md): US1 = Log In + access gating (P1),
US2 = Auto-Assignment (P1), US3 = AI-Suggested Resolution (P1), US4 = Knowledge Base Stays
Current (P2), US5 = Comment Authorization (P2), US6 = Chatbot Honest No-Match (P3), US7 = Scoped
Listing (P3). `User`, the schema migrations, the `Ticket`/`Comment` shape changes, and the
knowledge-base/chatbot entities are shared by multiple stories, so they sit in Foundational per
Task Generation Rules ("if an entity serves multiple stories, put it in Setup/Foundational").

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US7 — maps to spec.md's user stories
- All paths under `src/main/java/com/frequency/ticketing/` and
  `src/test/java/com/frequency/ticketing/` (the existing 001/002 codebase)

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add `spring-boot-starter-security` (compile), `spring-security-test` (test), and
      `com.pgvector:pgvector` (compile — the Hibernate/JDBC `vector` type mapper, not a database
      driver) to `pom.xml`, no explicit version except `pgvector` (managed by
      `spring-boot-starter-parent`'s BOM for the rest) — research.md "Authentication mechanism",
      "Vector storage"

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: `User`, the schema every story depends on, the now-blanket security filter chain,
and the knowledge-base/chatbot entities shared by US3/US4/US6.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 Create Flyway migration `src/main/resources/db/migration/V3__create_users_table.sql`:
      `users` table — `id UUID PRIMARY KEY`, `name` ("Not null."), `email` ("Not null. Unique,
      case-insensitively (`LOWER(email)` unique index)"), `password_hash` ("Not null."), `role`
      ("Not null. Exactly one of `SUPPORT`, `GENERAL`, `ADMIN`"), `created_at`/`updated_at`
      `TIMESTAMPTZ NOT NULL`; unique index on `LOWER(email)`; index on `role`
- [X] T003 Create Flyway migration
      `src/main/resources/db/migration/V4__ticket_ownership_and_assignment.sql`: add
      `tickets.created_by_id UUID NOT NULL REFERENCES users(id)`, add `tickets.assignee_id UUID
      REFERENCES users(id)` (nullable — "a ticket MAY be created with no `SUPPORT` user
      available"), drop the old `tickets.assignee VARCHAR(200)` column, add
      `idx_tickets_assignee_id_status ON tickets (assignee_id, status)` and
      `idx_tickets_created_by_id ON tickets (created_by_id)` (depends on T002)
- [X] T004 Create Flyway migration `src/main/resources/db/migration/V5__comment_author.sql`: add
      `comments.author_id UUID NOT NULL REFERENCES users(id)` (depends on T002)
- [X] T005 Create Flyway migration `src/main/resources/db/migration/V6__enable_pgvector.sql`:
      `CREATE EXTENSION IF NOT EXISTS vector;`
- [X] T006 Create Flyway migration
      `src/main/resources/db/migration/V7__create_knowledge_base_entries.sql`:
      `knowledge_base_entries` table — `id UUID PRIMARY KEY`, `ticket_id UUID NOT NULL UNIQUE
      REFERENCES tickets(id)`, `embedding vector(n) NOT NULL` (dimension `n` fixed by the chosen
      embedding model), `resolution_content TEXT`, `has_resolution_content BOOLEAN NOT NULL`,
      `created_at`/`updated_at TIMESTAMPTZ NOT NULL`; an HNSW index on `embedding` using cosine
      distance (depends on T005)
- [X] T007 Create Flyway migration
      `src/main/resources/db/migration/V8__create_chatbot_conversations.sql`:
      `chatbot_conversations` table — `id UUID PRIMARY KEY`, `user_id UUID NOT NULL REFERENCES
      users(id)`, `started_at TIMESTAMPTZ NOT NULL`, `explicitly_ended_at TIMESTAMPTZ`; index on
      `(user_id, started_at)` (depends on T002)
- [X] T008 Create Flyway migration `src/main/resources/db/migration/V9__create_chatbot_turns.sql`:
      `chatbot_turns` table — `id UUID PRIMARY KEY`, `conversation_id UUID NOT NULL REFERENCES
      chatbot_conversations(id) ON DELETE CASCADE`, `query_text TEXT NOT NULL`, `response_text
      TEXT NOT NULL`, `source_ticket_ids UUID[] NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`
      (depends on T007)
- [X] T009 [P] Create `UserRole` enum (`SUPPORT`, `GENERAL`, `ADMIN`) in
      `src/main/java/com/frequency/ticketing/domain/user/UserRole.java`
- [X] T010 [P] Create `User` entity in
      `src/main/java/com/frequency/ticketing/domain/user/User.java`: `id`, `name`, `email`,
      `passwordHash` (never exposed by a getter used in any response DTO), `role`
      (`@Enumerated(EnumType.STRING)`, matching `Ticket`/`Comment`'s convention),
      `createdAt`/`updatedAt` via `@PrePersist`/`@PreUpdate` — depends on T009
- [X] T011 [P] Create `UserRepository` in
      `src/main/java/com/frequency/ticketing/repository/UserRepository.java`:
      `Optional<User> findByEmailIgnoreCase(String email)` — depends on T010
- [X] T012 Update `Ticket` entity in
      `src/main/java/com/frequency/ticketing/domain/ticket/Ticket.java`: remove `assignee
      String`; add `assigneeId` (UUID, nullable) and `createdById` (UUID, not null); constructor
      becomes `Ticket(String title, String description, TicketPriority priority, UUID
      createdById)`; remove `assignee` from `updateFields(...)` entirely; add a plain
      `assignTo(UUID assigneeId)` method — depends on T003
- [X] T013 Update `Comment` entity in
      `src/main/java/com/frequency/ticketing/domain/comment/Comment.java`: add `authorId` (UUID,
      not null) to the constructor and as a field/getter — depends on T004
- [X] T014 [P] Create `UserSummary` DTO (`id`, `name`) in
      `src/main/java/com/frequency/ticketing/web/dto/UserSummary.java`
- [X] T015 Update `TicketCreateRequest`/`TicketUpdateRequest` in
      `src/main/java/com/frequency/ticketing/web/dto/`: remove the `assignee` field from both
      records entirely — depends on T012
- [X] T016 Update `TicketResponse`/`TicketDetailResponse`/`TicketMapper` in
      `src/main/java/com/frequency/ticketing/web/dto/`: replace `String assignee` with nullable
      `UserSummary assignee` and add non-null `UserSummary createdBy`, resolved via
      `UserRepository` lookups — depends on T012, T014, T011
- [X] T017 Update `CommentResponse`/`CommentMapper` in
      `src/main/java/com/frequency/ticketing/web/dto/`: add `authorName` resolved via
      `UserRepository` — depends on T013, T011
- [X] T018 Update `TicketService.create(...)` signature to `create(String title, String
      description, TicketPriority priority, UUID createdById)` (no `assignee` param); update its
      `TicketController` caller so the codebase compiles (auto-assignment itself lands in US2) —
      depends on T012
- [X] T019 Create `SecurityConfig` in
      `src/main/java/com/frequency/ticketing/config/SecurityConfig.java`: `PasswordEncoder` bean
      (`BCryptPasswordEncoder`), `AuthenticationManager` bean, a `UserDetailsService`
      implementation (new `AppUserDetailsService` in `domain/auth/`, via T011's
      `findByEmailIgnoreCase`, mapping `role` to a `ROLE_<role>` authority), and a genuinely
      default-deny filter chain: `permitAll` ONLY on `POST /api/v1/auth/login`,
      `GET /actuator/health`, `GET /actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`;
      `anyRequest().authenticated()` for everything else (FR-006 as amended — no more
      `permitAll` for `GET /tickets/{id}` or `GET /tickets/{id}/comments`); CSRF disabled —
      depends on T011
- [X] T020 Update `CorsConfig` in `src/main/java/com/frequency/ticketing/config/CorsConfig.java`:
      `.allowCredentials(false)` → `.allowCredentials(true)`
- [X] T021 [P] Create `CurrentUser` component in
      `src/main/java/com/frequency/ticketing/domain/user/CurrentUser.java`: resolves the
      authenticated caller's `User` (id, role) from `SecurityContextHolder` — depends on T010
- [X] T022 [P] Create a `@Profile("dev")` `CommandLineRunner` seeder in
      `src/main/java/com/frequency/ticketing/config/DevDataSeeder.java`: one `ADMIN`, two
      `SUPPORT`, one `GENERAL` user, hashed via T019's `PasswordEncoder` bean — never a Flyway
      migration (research.md "Seed users") — depends on T010, T011, T019
- [X] T023 Fix the constitution Principle V actor-logging gap: update
      `TicketService.applyTransition`'s log line in
      `src/main/java/com/frequency/ticketing/domain/ticket/TicketService.java` from
      `ticket_transition ticketId={} from={} to={}` to include `actor={}`, sourced from
      `CurrentUser` (research.md "Fixing the actor-logging gap") — depends on T021
- [X] T024 [P] Create `KnowledgeBaseEntry` entity in
      `src/main/java/com/frequency/ticketing/domain/knowledgebase/KnowledgeBaseEntry.java`: `id`,
      `ticketId` ("Not null, unique... one entry per ticket"), `embedding` (`vector`, via the
      `pgvector` Java type, "Not null"), `resolutionContent` (`TEXT`, nullable — "Empty/absent
      when the ticket has no comments"), `hasResolutionContent` (`BOOLEAN`, not null),
      `createdAt`/`updatedAt`
- [X] T025 [P] Create `KnowledgeBaseEntryRepository` in
      `src/main/java/com/frequency/ticketing/repository/KnowledgeBaseEntryRepository.java`: a
      cosine-distance nearest-neighbor query (`ORDER BY embedding <=> :queryEmbedding LIMIT :k`)
      filtered to `has_resolution_content = true`, backed by T006's HNSW index — depends on T024
- [X] T026 [P] Create `ChatbotConversation` entity in
      `src/main/java/com/frequency/ticketing/domain/chatbot/ChatbotConversation.java`: `id`,
      `userId` (not null), `startedAt` (not null), `explicitlyEndedAt` (nullable)
- [X] T027 [P] Create `ChatbotTurn` entity in
      `src/main/java/com/frequency/ticketing/domain/chatbot/ChatbotTurn.java`: `id`,
      `conversationId` (not null), `queryText` (not null, "non-blank"), `responseText` (not
      null), `sourceTicketIds` (`UUID[]`), `createdAt` (not null)
- [X] T028 [P] Create `ChatbotConversationRepository` (find by `userId` ordered by `startedAt`
      descending) and `ChatbotTurnRepository` (find latest turn by `conversationId`) in
      `src/main/java/com/frequency/ticketing/repository/` — depends on T026, T027
- [X] T029 [P] Create `EmbeddingClient` and `ResolutionLlmClient` interfaces in
      `src/main/java/com/frequency/ticketing/domain/ai/`: `EmbeddingClient.embed(String text):
      float[]`; `ResolutionLlmClient.generate(String query, List<GroundingSource> sources):
      String` where each source carries a ticket reference and its resolution content
      (research.md "Embedding generation", "Response generation")
- [X] T030 [P] Create `HttpEmbeddingClient` (local Ollama `/api/embed`, amended from the original
      Voyage AI decision) and `HttpResolutionLlmClient` (local Ollama `/api/chat`, amended from
      the original Anthropic Claude decision) implementations in
      `src/main/java/com/frequency/ticketing/domain/ai/impl/`, using Spring's `RestClient`,
      `app.ai.ollama.*` config (no API key — local); the
      generation client's system prompt enforces: answer only from provided context, never quote
      internal comment text verbatim (FR-031), never include names/contact/internal identifiers
      (FR-032), explicitly decline to answer outside the provided context — depends on T029
- [X] T031 [P] Create test-double implementations of `EmbeddingClient`/`ResolutionLlmClient`
      (deterministic fixed responses for known fixture text) as a `@TestConfiguration` in
      `src/test/java/com/frequency/ticketing/support/ChatbotTestClients.java`, wired to override
      the real beans in `@SpringBootTest` chatbot tests (research.md "Testing the AI
      integration") — depends on T029
- [X] T032 [P] Create `ChatbotProperties` (`@ConfigurationProperties(prefix = "app.chatbot")`) in
      `src/main/java/com/frequency/ticketing/config/ChatbotProperties.java`:
      `similarityThreshold` (conservative default), `retentionDays` (default `90`, FR-035),
      `inactivityMinutes` (default `30`, FR-036)
- [X] T033 Update the existing test suite so it compiles and passes again against T002–T023's
      schema/DTO/security changes before any new story work begins:
      `TicketCreateContractTest.java`, `TicketUpdateContractTest.java`, `TicketMapperTest.java`,
      `CommentMapperTest.java`, `TicketLifecycleIntegrationTest.java`,
      `TicketSearchFilterIntegrationTest.java`, `TicketConcurrencyIntegrationTest.java`,
      `TicketRestartDurabilityIntegrationTest.java`, `TicketStateMachineIntegrationTest.java`,
      `TicketValidationIntegrationTest.java`, `CommentIntegrationTest.java`,
      `CommentListIntegrationTest.java` — replace direct `Ticket`/`Comment` construction or
      `TicketService.create(...)` calls with the new `createdById` signature, and authenticate
      each request using `spring-security-test`'s `@WithMockUser`/manual `SecurityContextHolder`
      setup against a fixed seeded test user id (NOT a real login call — `AuthController` doesn't
      exist until US1) — under `src/test/java/com/frequency/ticketing/`

**Checkpoint**: Full existing suite green again; the security filter chain default-denies
everything except login and operational endpoints; knowledge-base/chatbot tables exist but
nothing writes/reads them yet; no login or chatbot endpoint is exposed yet.

---

## Phase 3: User Story 1 - Log In to Access the System (Priority: P1) 🎯 MVP

**Goal**: Login establishes an authenticated session; every other API this feature exposes,
including the two view endpoints that were previously open, rejects an unauthenticated or
unauthorized caller.

**Independent Test**: Attempt every gated action (ticket create/view/list/update/transition,
comment create/list, chatbot message) without logging in — all rejected. Log in, retry — all
succeed (subject to FR-037 ownership). Log in with a wrong password — rejected, generic error.
Log out — gated actions reject again.

### Tests for User Story 1 ⚠️ (write first, confirm failing, per constitution Principle III)

- [X] T034 [P] [US1] Contract test `POST /api/v1/auth/login` (200 `LoginResponse`; 400 missing
      `email`/`password`, FR-004; 401 — same generic error for wrong password and unregistered
      email, FR-003) in
      `src/test/java/com/frequency/ticketing/contract/AuthLoginContractTest.java`
- [X] T035 [P] [US1] Contract test `POST /api/v1/auth/logout` (204 authenticated; 401 with no
      session) in `src/test/java/com/frequency/ticketing/contract/AuthLogoutContractTest.java`
- [X] T036 [US1] Integration test sweeping every gated endpoint (ticket create, list, view by id,
      update, transition, reassign, comment create, comment list, chatbot message) with no
      session and confirming each returns 401 (FR-006, Scenario 8) in
      `src/test/java/com/frequency/ticketing/integration/AuthenticationGateIntegrationTest.java`
- [X] T037 [US1] Integration test for the full login/logout scenario set: login succeeds and
      enables ticket creation and chatbot use (Scenarios 1, 6); wrong password / unregistered
      email both rejected with the same error (Scenarios 2–3); logout ends the session so a
      subsequent gated call is 401 again (Scenario 7) in
      `src/test/java/com/frequency/ticketing/integration/AuthFlowIntegrationTest.java`
- [X] T038 [P] [US1] Integration test for FR-037 view authorization: the ticket's creator can
      `GET` it and its comments; an unrelated `GENERAL` user gets 403 on both; a `SUPPORT`/`ADMIN`
      user can view any ticket (Scenario 9) in
      `src/test/java/com/frequency/ticketing/integration/TicketViewAuthorizationIntegrationTest.java`

### Implementation for User Story 1

- [X] T039 [P] [US1] Create `LoginRequest` (`email`/`password` `@NotBlank`) and `LoginResponse`
      (`id`, `name`, `email`, `role`) DTOs in
      `src/main/java/com/frequency/ticketing/web/dto/LoginRequest.java`, `LoginResponse.java`
- [X] T040 [US1] Create `InvalidCredentialsException` in `domain/exception/` and map it to `401`
      in `GlobalExceptionHandler` with a single generic message that never reveals whether the
      email exists (FR-003)
- [X] T041 [US1] Create `ForbiddenActionException` in `domain/exception/` and map it to `403` in
      `GlobalExceptionHandler` (shared by this story's view-authorization check and US5's comment
      authorization)
- [X] T042 [US1] Create `AuthenticationService` in `domain/auth/AuthenticationService.java`:
      `login(email, password, HttpServletRequest)` authenticates via T019's
      `AuthenticationManager` and saves the `SecurityContext` into the `HttpSession`, throwing
      `InvalidCredentialsException` on any failure; `logout(...)` invalidates the session
      (FR-008) — depends on T019
- [X] T043 [US1] Create `AuthController` in `web/controller/AuthController.java`:
      `POST /api/v1/auth/login` (`@Valid @RequestBody LoginRequest` → 400 on a missing field
      before T042 runs, FR-004) and `POST /api/v1/auth/logout`, `@Operation`/`@ApiResponses`
      matching contracts/auth-api.yaml — depends on T039, T042
- [X] T044 [US1] Add the FR-037 authorization check to `TicketService.getById`/`getDetailById` in
      `domain/ticket/TicketService.java`: allowed when `CurrentUser`'s id equals `createdById` or
      `assigneeId`, or `CurrentUser`'s role is `SUPPORT`/`ADMIN`; otherwise throw
      `ForbiddenActionException` — depends on T021, T041
- [X] T045 [US1] Apply the same T044 check to the `GET /api/v1/tickets/{ticketId}/comments` path
      in `TicketCommentController`/`CommentService` (load the parent ticket first, reuse the
      check) — depends on T044
- [X] T046 [US1] Add a `loginAs(UserRole role)` helper (authenticated `MockMvc`
      `RequestPostProcessor`/cookie against T022's seeded users) to
      `src/test/java/com/frequency/ticketing/support/AbstractIntegrationTest.java`, for every
      later story's tests — depends on T022, T043

**Checkpoint**: User Story 1 fully functional and independently testable — T034–T038 all green.

---

## Phase 4: User Story 2 - Tickets Are Auto-Assigned to the Least-Busy Support User (Priority: P1)

**Goal**: Every new ticket auto-assigns to the `SUPPORT` user with the fewest `OPEN`/
`IN_PROGRESS` tickets, ties break deterministically, no-`SUPPORT`-available leaves it unassigned,
and `ADMIN` can manually reassign afterward.

**Independent Test**: Seed `SUPPORT` users with different loads, create a ticket, confirm it
lands on the smallest-load one with no assignee in the request body.

### Tests for User Story 2 ⚠️

- [X] T047 [P] [US2] Contract test `POST /api/v1/tickets`: response `assignee` populated though
      the request body has no `assignee` field (FR-009) in
      `src/test/java/com/frequency/ticketing/contract/TicketCreateContractTest.java` (extends
      T033's already-updated file)
- [X] T048 [US2] Integration test: three `SUPPORT` users with counts `[2, 0, 1]` → new ticket goes
      to the `0` one (FR-010); two tied at the lowest count → the same one every time, by
      ascending user id (FR-011) in
      `src/test/java/com/frequency/ticketing/integration/TicketAutoAssignmentIntegrationTest.java`
- [X] T049 [US2] Integration test: no `SUPPORT` user exists → ticket still created,
      `assignee: null` (FR-012) in the same file
- [X] T050 [US2] Integration test: a ticket assigned to `SUPPORT` A, transitioned to `RESOLVED`,
      no longer counts toward A's workload for the next assignment (Scenario 4) in the same file
- [X] T051 [P] [US2] Contract + integration test `PATCH /api/v1/tickets/{ticketId}/assignee`:
      `ADMIN` succeeds (Scenario 5); non-`ADMIN` gets 403, assignee unchanged (Scenario 6); a
      non-`SUPPORT` target gets 400 (FR-013) in
      `src/test/java/com/frequency/ticketing/contract/TicketReassignContractTest.java`

### Implementation for User Story 2

- [X] T052 [US2] Add the least-loaded-`SUPPORT` query to `UserRepository`: among
      `role = 'SUPPORT'` users, `LEFT JOIN` tickets filtered to
      `status IN ('OPEN','IN_PROGRESS')`, `GROUP BY` user, `ORDER BY COUNT(...) ASC, u.id ASC`,
      return the first row, backed by T003's `idx_tickets_assignee_id_status` — depends on T011,
      T003
- [X] T053 [US2] Update `TicketService.create(...)` to call T052 inside the same
      `@Transactional` block, set `assigneeId` (or leave `null` per FR-012), and set
      `createdById` from `CurrentUser` — depends on T018, T021, T052
- [X] T054 [P] [US2] Create `ReassignRequest` DTO (`assigneeId`, UUID) in `web/dto/`
- [X] T055 [US2] Add `TicketService.reassign(UUID ticketId, UUID assigneeId)`: 400 if the target
      user's role is not `SUPPORT`, otherwise `Ticket.assignTo(...)` — depends on T053
- [X] T056 [US2] Add `PATCH /api/v1/tickets/{ticketId}/assignee` to `TicketController`,
      `@PreAuthorize("hasRole('ADMIN')")` (FR-013), delegating to T055 — depends on T054, T055

**Checkpoint**: User Stories 1 AND 2 both independently green.

---

## Phase 5: User Story 3 - Get an AI-Suggested Resolution from Past Tickets (Priority: P1)

**Goal**: A logged-in user's chatbot query returns a response grounded in the actual resolution
of the most similar resolved ticket(s), citing the source, with multi-turn follow-up support.

**Independent Test**: Seed a resolved ticket with a specific issue/resolution, query the chatbot
with a differently-worded description of the same issue, confirm the grounded response and cited
source ticket.

### Tests for User Story 3 ⚠️

- [X] T057 [P] [US3] Contract test `POST /api/v1/chatbot/messages` happy path: 200,
      `ChatbotTurnResponse` with `sourceTicketIds` populated and `confidentMatch: true` per
      contracts/chatbot-api.yaml in
      `src/test/java/com/frequency/ticketing/contract/ChatbotMessageContractTest.java`
- [X] T058 [US3] Integration test (using T031's stub AI clients): a resolved ticket's title/
      description are embedded and indexed (via US4's mechanism, seeded directly for this test),
      a differently-worded query returns a response consistent with that ticket's resolution and
      cites its id (Scenarios 1–3, FR-023/024/025) in
      `src/test/java/com/frequency/ticketing/integration/ChatbotResolutionIntegrationTest.java`
- [X] T059 [US3] Integration test: blank/empty query → 400, no `EmbeddingClient`/
      `ResolutionLlmClient` call made (FR-022, Scenario 4) in the same file
- [X] T060 [US3] Integration test: a follow-up query in the same conversation (supplying
      `conversationId`) has its retrieval/response account for the earlier turn's context
      (Scenario 5, FR-033/034) in the same file
- [X] T061 [P] [US3] Contract test `POST /api/v1/chatbot/conversations/{conversationId}/end`
      (204 when the conversation belongs to the caller; 404 when not found, not owned by the
      caller, or already ended, FR-036) per contracts/chatbot-api.yaml in
      `src/test/java/com/frequency/ticketing/contract/ChatbotEndConversationContractTest.java`
- [X] T062 [US3] Integration test: after explicitly ending a conversation, a subsequent message
      with no `conversationId` starts a brand-new conversation rather than resuming the ended one
      (FR-036, Edge Cases) in
      `src/test/java/com/frequency/ticketing/integration/ChatbotConversationLifecycleIntegrationTest.java`
- [X] T063 [US3] Integration test: when `EmbeddingClient` or `ResolutionLlmClient` fails (using a
      failing variant of T031's test doubles), `POST /api/v1/chatbot/messages` returns `503` with
      an `AiServiceUnavailableException`-mapped `ApiError`, not an empty or silently wrong
      response (FR-030) in
      `src/test/java/com/frequency/ticketing/integration/ChatbotServiceUnavailableIntegrationTest.java`

### Implementation for User Story 3

- [X] T064 [US3] Add `ChatbotService.endConversation(UUID userId, UUID conversationId)` in
      `domain/chatbot/ChatbotService.java`: loads the conversation, throws a not-found error
      unless it belongs to `userId` and has no `explicitlyEndedAt` yet, otherwise sets
      `explicitlyEndedAt` to now (FR-036) — depends on T021, T026, T028
- [X] T065 [US3] Create `ChatbotService.handleQuery(UUID userId, String query, UUID
      conversationId)` in `domain/chatbot/ChatbotService.java`: reject blank query (FR-022);
      resolve or start the conversation (find the caller's open one — no `explicitlyEndedAt`,
      last turn within `ChatbotProperties.inactivityMinutes` — else start new, research.md
      "Conversation model"); load the conversation's prior turns via `ChatbotTurnRepository`
      (empty for a new conversation) and build the embedding input from the current query plus
      that prior context (FR-033/034 — a follow-up is embedded together with what came before it,
      not in isolation); embed via `EmbeddingClient`; run T025's similarity query — depends on
      T021, T025, T028, T029, T032
- [X] T066 [US3] Extend `ChatbotService` to call `ResolutionLlmClient.generate(...)` with the
      matched entries' `resolutionContent` as grounding when the top match clears
      `similarityThreshold`, building the `GroundingSource` list with ticket references only (no
      internal-only data, FR-025), AND passing the same prior-turns context T065 loaded so the
      generated answer is worded consistently with the earlier turns of the conversation
      (FR-034); wrap the `EmbeddingClient`/`ResolutionLlmClient` calls in try/catch, translating
      any failure (timeout, non-2xx, malformed response) to `AiServiceUnavailableException`
      (new, in `domain/exception/`), mapped to `503` in `GlobalExceptionHandler` with a distinct
      `ApiError` code (FR-030); apply one bounded retry to the embedding call only, none to
      generation (research.md "External-API failure handling") — depends on T065, T029, T030
- [X] T067 [P] [US3] Create `ChatbotQueryRequest`/`ChatbotTurnResponse` DTOs in `web/dto/` per
      contracts/chatbot-api.yaml
- [X] T068 [US3] Create `ChatbotController` in `web/controller/ChatbotController.java`:
      `POST /api/v1/chatbot/messages` (delegating to T066) and
      `POST /api/v1/chatbot/conversations/{conversationId}/end` (delegating to T064),
      `@Operation`/`@ApiResponses` matching contracts/chatbot-api.yaml — depends on T064, T066,
      T067
- [X] T069 [US3] Persist each turn (`ChatbotTurn`: query, response, `sourceTicketIds`) under its
      conversation via `ChatbotTurnRepository`/`ChatbotConversationRepository` — depends on T028,
      T066

**Checkpoint**: User Stories 1, 2, AND 3 all independently green.

---

## Phase 6: User Story 4 - Knowledge Base Stays Current as Tickets Are Resolved (Priority: P2)

**Goal**: A ticket reaching `RESOLVED` automatically becomes chatbot-groundable with no manual
re-indexing step.

**Independent Test**: Resolve a ticket describing a new issue, without any manual action query
the chatbot about it, confirm the newly resolved ticket now grounds the response.

### Tests for User Story 4 ⚠️

- [X] T070 [P] [US4] Integration test: resolving a ticket (with a resolution comment) results,
      without manual action, in a knowledge base entry usable by a matching chatbot query
      (Scenario 1, FR-018) — awaits the async indexing (e.g. `Awaitility`) before asserting — in
      `src/test/java/com/frequency/ticketing/integration/KnowledgeBaseIndexingIntegrationTest.java`
- [X] T071 [US4] Integration test: an unresolved ticket is never used as chatbot grounding
      (Scenario 2, FR-026) in the same file
- [X] T072 [US4] Integration test: a resolved ticket with no comments is indexed (title/
      description searchable) but never presented as a resolution source
      (`hasResolutionContent = false`, FR-027, Edge Cases) in the same file

### Implementation for User Story 4

- [X] T073 [US4] Add a `TicketResolvedEvent` (Spring `ApplicationEvent`) published by
      `TicketService.applyTransition` when the new status is `RESOLVED` — depends on T023
- [X] T074 [US4] Create `KnowledgeBaseService` in
      `domain/knowledgebase/KnowledgeBaseService.java`: `@TransactionalEventListener(phase =
      AFTER_COMMIT)` + `@Async` handler for `TicketResolvedEvent` — embeds title+description via
      `EmbeddingClient`, builds/refreshes the ticket's `KnowledgeBaseEntry`, sets
      `resolutionContent`/`hasResolutionContent` from the ticket's comments (empty/false when
      none), saves in its own transaction (research.md "Knowledge base indexing trigger") —
      depends on T024, T029, T073

**Checkpoint**: User Stories 1–4 all independently green.

---

## Phase 7: User Story 5 - Only the Ticket's Creator or Assignee Can Comment, and Is Named (Priority: P2)

**Goal**: Only a ticket's creator or its assigned `SUPPORT` user may comment; every comment shows
who wrote it.

**Independent Test**: Creator comments (succeeds, named). Assignee comments (succeeds, named).
Unrelated logged-in user comments (rejected).

### Tests for User Story 5 ⚠️

- [X] T075 [P] [US5] Integration test: creator succeeds with `authorName` (Scenario 1); assigned
      `SUPPORT` succeeds with their name (Scenario 2); an unrelated logged-in user gets 403, no
      comment saved (Scenario 3); retrieving the ticket shows the author's name (Scenario 4) in
      `src/test/java/com/frequency/ticketing/integration/CommentAuthorizationIntegrationTest.java`
      (builds on an auto-assigned ticket from US2)

### Implementation for User Story 5

- [X] T076 [US5] Update `CommentService.addComment(...)` in `domain/comment/CommentService.java`:
      resolve the caller via `CurrentUser`, load the parent ticket, throw
      `ForbiddenActionException` (T041) unless the caller's id equals `createdById` or
      `assigneeId` (FR-014), then construct the `Comment` with that caller's id as `authorId` —
      depends on T021, T041, T013
- [X] T077 [US5] Update `TicketCommentController` to stop accepting any author field (there never
      was one) and rely entirely on T076's `CurrentUser` resolution — depends on T076

**Checkpoint**: User Stories 1–5 all independently green.

---

## Phase 8: User Story 6 - Chatbot Is Honest When It Has No Good Match (Priority: P3)

**Goal**: When no past ticket is a close enough match, the chatbot says so and directs the user
to raise a ticket manually — it never fabricates or auto-creates one.

**Independent Test**: Query the chatbot about an issue with no similar ticket anywhere in the
knowledge base; confirm the no-match response and that no ticket was created.

### Tests for User Story 6 ⚠️

- [X] T078 [P] [US6] Integration test: a query with no sufficiently similar knowledge base entry
      gets `confidentMatch: false`, empty `sourceTicketIds`, and a response directing the user to
      raise a ticket manually — and confirms no ticket was created as a side effect (FR-028,
      Scenarios 1–2) in
      `src/test/java/com/frequency/ticketing/integration/ChatbotNoMatchIntegrationTest.java`

### Implementation for User Story 6

- [X] T079 [US6] Update `ChatbotService` (T065/T066) so a below-`similarityThreshold` result skips
      the `ResolutionLlmClient` call entirely and returns the fixed no-match response text
      (research.md "Retrieval & grounding" — deciding confidence before ever calling the LLM) —
      depends on T066

**Checkpoint**: User Stories 1–6 all independently green.

---

## Phase 9: User Story 7 - Filter the Ticket List by Ownership (Priority: P3)

**Goal**: `GET /api/v1/tickets` accepts `scope` (`mine`/`assigned`/`all`), combinable with the
existing keyword/status filters.

**Independent Test**: As a user with both created and (if `SUPPORT`) assigned tickets, request
each scope and confirm exactly the expected subset.

### Tests for User Story 7 ⚠️

- [X] T080 [P] [US7] Integration test: `mine` returns only created tickets (Scenario 1);
      `assigned` returns only assigned tickets, empty (not an error) for a `GENERAL` caller
      (Scenario 2); `all` returns everything for `SUPPORT`/`ADMIN` but narrows to `mine` for
      `GENERAL` (default, Scenario 3); `scope` combined with `q`/`status` returns only tickets
      matching both (Scenario 4, FR-017) in
      `src/test/java/com/frequency/ticketing/integration/TicketScopedListingIntegrationTest.java`

### Implementation for User Story 7

- [X] T081 [US7] Add scoped query methods to `TicketRepository`: extend the existing native
      `search(...)` query with an optional `createdById`/`assigneeId` predicate pair, backed by
      T003's `idx_tickets_created_by_id`/`idx_tickets_assignee_id_status`
- [X] T082 [US7] Add a `TicketScope` enum (`MINE`, `ASSIGNED`, `ALL`) in `domain/ticket/` and
      update `TicketService.search(...)` to resolve it against `CurrentUser`: `ALL` for a
      `GENERAL` caller rewrites to the same predicate as `MINE` (research.md "Listing scope
      semantics") — depends on T021, T081
- [X] T083 [US7] Add the `scope` query parameter (default `all`) to `TicketController.list(...)`,
      wired to T082 — depends on T082

**Checkpoint**: All seven user stories independently functional.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T084 [P] Unit test for T052's tie-break ordering (ascending user id), isolated from the
      full ticket-creation flow, in
      `src/test/java/com/frequency/ticketing/unit/UserRepositoryWorkloadQueryTest.java`
- [X] T085 [P] Unit test for T065/T079's similarity-threshold boundary (confident vs. no-match)
      using T031's stub clients, in
      `src/test/java/com/frequency/ticketing/unit/ChatbotServiceThresholdTest.java`
- [X] T086 [P] Create `ConversationRetentionJob` (`@Scheduled`, daily) in
      `domain/chatbot/ConversationRetentionJob.java`: deletes `ChatbotConversation` rows (and
      their turns, via `ON DELETE CASCADE` from T008) whose effective end is more than
      `ChatbotProperties.retentionDays` in the past (FR-035), plus an integration test seeding an
      old conversation and asserting it's removed after the job runs
- [X] T087 [P] Grep-verify no `@Autowired` field injection was introduced by this feature's new
      or edited files (constitution Design Patterns: constructor injection only)
- [X] T088 [P] Grep-verify `User.passwordHash` and the AI provider API keys never appear in a log
      statement or a response DTO anywhere in the diff (constitution Principle V; spec SC-005)
- [ ] T089 Run `quickstart.md` end-to-end against a local build (`dev` profile, seeded users,
      real or stubbed AI clients per its prerequisites) and record results, including the
      actor-logging spot-check

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS every user story.
- **User Story 1 (Phase 3)**: Depends on Foundational only.
- **User Story 2 (Phase 4)**: Depends on Foundational AND User Story 1 — ticket creation itself
  requires an authenticated caller (`CurrentUser`, and T046's `loginAs` helper needs T043's login
  endpoint). Real technical dependency despite both being P1.
- **User Story 3 (Phase 5)**: Depends on Foundational AND User Story 1 (authenticated caller for
  the chatbot). Independent of User Story 2 — can be built in parallel with it once US1 is done,
  seeding its own knowledge base entries directly for its tests rather than waiting on US4's
  indexing mechanism.
- **User Story 4 (Phase 6)**: Depends on Foundational, User Story 1, AND User Story 3 (needs
  `ChatbotService`/`KnowledgeBaseEntryRepository` from US3 to verify indexed entries are
  retrievable, not just stored).
- **User Story 5 (Phase 7)**: Depends on Foundational, User Story 1, AND User Story 2 (a
  meaningfully-assigned `SUPPORT` user to test the "assignee can comment" scenario against).
- **User Story 6 (Phase 8)**: Depends on Foundational, User Story 1, AND User Story 3 (extends
  `ChatbotService`).
- **User Story 7 (Phase 9)**: Depends on Foundational and User Story 1. Independent of US2/US3/
  US4/US5/US6.
- **Polish (Phase 10)**: Depends on whichever stories are in scope for a given delivery.

### Within Each User Story

- Tests MUST be written and failing before their implementation task (constitution Principle
  III).
- Repository/query layer before service layer; service layer before controller.

### Parallel Opportunities

- T009–T011, T014, T021, T022, T024–T032 (Foundational) can run in parallel once their own listed
  dependencies are met.
- US3 (Phase 5) and US2 (Phase 4) can proceed in parallel once US1 is done — disjoint files
  (`ChatbotService`/`ChatbotController` vs. `TicketService` assignment logic).
- US7 (Phase 9) can proceed in parallel with US2/US3/US4/US5/US6 once US1 is done — disjoint
  files (`TicketRepository` scoped queries vs. everything else).

---

## Parallel Example: Foundational Phase

```bash
# After T002-T008 (migrations) land:
Task: "Create UserRole enum in domain/user/UserRole.java"                       # T009
Task: "Create KnowledgeBaseEntry entity in domain/knowledgebase/..."            # T024
Task: "Create ChatbotConversation entity in domain/chatbot/..."                 # T026
Task: "Create EmbeddingClient/ResolutionLlmClient interfaces in domain/ai/"     # T029
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 + 3 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — blocks everything).
3. Complete Phase 3: User Story 1 (login, blanket gating, view authorization).
4. Complete Phase 4: User Story 2 (auto-assignment) and Phase 5: User Story 3 (chatbot
   resolution) — in parallel if staffed; both are P1 and both only depend on US1.
5. **STOP and VALIDATE**: run T034–T038, T047–T051, T057–T063 independently; run `quickstart.md`
   steps 1–7 and 8's manual-seeding variant (skip the async-indexing wait by seeding a
   `KnowledgeBaseEntry` directly, since US4 isn't built yet at this point).
6. Deploy/demo if ready.

### Incremental Delivery

1. Setup + Foundational → foundation ready, nothing user-visible yet.
2. Add User Story 1 → login/logout usable on its own; every endpoint now gated.
3. Add User Stories 2 + 3 → auto-assignment and chatbot resolution both live (MVP complete).
4. Add User Story 4 → knowledge base indexing becomes automatic (chatbot no longer needs manually
   seeded entries).
5. Add User Story 5 → comment authorization enforced.
6. Add User Story 6 → chatbot honest no-match behavior.
7. Add User Story 7 → scoped listing available.
8. Polish.

---

## Notes

- [P] tasks = different files, no dependency on an incomplete task.
- Constitution Principle III is non-negotiable: every task above that changes behavior has a
  preceding test task in the same or an earlier phase.
- Foundational (T002–T033) is unusually large because `User`, the `Ticket`/`Comment` shape
  change, and the knowledge-base/chatbot entities are genuinely shared by multiple stories — see
  Task Generation Rules ("if an entity serves multiple stories, put it in Setup/Foundational").
- Commit after each task or logical group; stop at any checkpoint to validate a story
  independently.
