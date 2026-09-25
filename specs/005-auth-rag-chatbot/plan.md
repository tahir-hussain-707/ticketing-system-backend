# Implementation Plan: Authenticated Ticketing with RAG Resolution Chatbot

**Branch**: `005-auth-rag-chatbot` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-auth-rag-chatbot/spec.md`

## Summary

Add email/password login (`SUPPORT`/`GENERAL`/`ADMIN` roles) and make it the single gate in
front of every API this service exposes except login itself (FR-006, widened by this spec's
2026-09-22 amendment) — including, newly, single-ticket/comment view, which additionally gets a
row-level ownership check (FR-037: creator, assignee, or `SUPPORT`/`ADMIN`). On top of that
authenticated foundation: auto-assign each new ticket to the least-loaded `SUPPORT` user with
`ADMIN`-only manual override; restrict commenting to a ticket's creator or assignee with named
attribution; add ownership-scoped ticket listing; and build the RAG chatbot — a knowledge base of
resolved tickets, embedded and stored in the existing PostgreSQL database via the `pgvector`
extension (no new service), semantically retrieved per query, and answered by an LLM call that
grounds its response in the matched ticket(s)' actual resolution while staying customer-safe
(FR-031/032) — with multi-turn conversation support, a 90-day retention window, and a 30-minute
inactivity boundary. This is a single plan for a single merged spec (005), replacing two earlier,
disconnected efforts: 003 (chatbot) was never planned, and 004 (auth) was planned in isolation
against a chatbot endpoint that didn't exist — both spec.md files carry a superseded notice
pointing here.

## Technical Context

**Language/Version**: Java 25 (LTS) — unchanged, constitution-fixed.

**Primary Dependencies**: Spring Boot 3.5.16 (Spring Web, Spring Data JPA, Spring Validation,
Actuator) — already present. Adds `spring-boot-starter-security` (session-based authentication,
`BCryptPasswordEncoder`) and `spring-security-test` (test scope). Adds `pgvector` (the
`com.pgvector:pgvector` Java library — a small Hibernate/JDBC type mapper, not a database
driver) so a `Ticket`-derived embedding can be stored as a native PostgreSQL `vector` column
through JPA like any other field. No vector-database client library, no message broker, no LLM
SDK — the two external AI calls (embeddings, response generation) are plain HTTPS JSON calls made
through Spring's existing `RestClient` (already available via `spring-boot-starter-web`), wrapped
behind two small interfaces (`EmbeddingClient`, `ResolutionLlmClient`) so tests substitute a
deterministic stub instead of calling a real third-party API.

**Storage**: PostgreSQL 16+. The `vector` extension (via `pgvector`) is enabled on the same
database — no separate vector database service. New tables: `users`; `knowledge_base_entries`
(one row per resolved ticket, carrying its embedding); `chatbot_conversations`; `chatbot_turns`
(query + response per turn). `tickets` gains `created_by_id`/`assignee_id` (replacing the old
free-text `assignee`); `comments` gains `author_id`. All additive, forward-only Flyway migrations
(`V3`–`V9`).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL — including a `pgvector/pgvector`
Testcontainers image so the vector column and similarity queries are tested against the real
extension, not skipped), AssertJ, `spring-security-test`. `EmbeddingClient`/`ResolutionLlmClient`
are swapped for a fixed-response test double in `@SpringBootTest` configuration — no test ever
calls a real external AI API (nondeterministic, costly, and not what Testcontainers' "real
PostgreSQL" mandate is about — that mandate is specifically for *persistence*, not third-party
network calls).

**Target Platform**: Same single Spring Boot microservice; no new deployable, no new service.

**Project Type**: Backend extension to an existing web-service. No frontend work in scope.

**Performance Goals**: Chatbot response (retrieval + generation) under 5 seconds under normal
load — up to 50,000 resolved tickets in the knowledge base, up to 100 concurrent conversations
(SC-009). Existing 2-second ticket-search/comment-list targets (001/002) unchanged. 50,000 vectors
is comfortably within `pgvector`'s HNSW-index range for sub-second similarity search, so response
time is dominated by the external LLM call, not retrieval.

**Constraints**: Reuses constitution-mandated patterns: contract-first (OpenAPI before controller
code), constructor injection only, single local `@Transactional` per unit of work (no SAGA — every
call in this feature, including the AI calls, stays inside this service's own request; there is
no cross-*service* call, since the AI providers are external APIs consumed synchronously, the
same category as any other outbound HTTP call this codebase already makes to nothing today but
would to Flyway/Postgres-adjacent tooling), paginated list endpoints, one consistent `ApiError`
shape, structured logging with correlation IDs. This plan additionally closes a gap the prior
(now-superseded) 004 plan left open: `TicketService`'s transition log line never included the
acting user, even though constitution Principle V requires it — `CurrentUser` (this feature's own
addition) makes that fixable, and it is fixed here (see research.md).

**Scale/Scope**: Two new controllers (`AuthController`, `ChatbotController`), five new domain
packages (`user`, `auth`, `knowledgebase`, `chatbot`, `ai`), seven additive migrations, and
modifications to every existing ticket/comment code path (create, view, list, update, transition,
comment) to add authentication, ownership authorization, and named attribution. User account
creation itself stays out of scope (spec FR-005) — accounts are seed data.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | How this plan satisfies it |
|---|---|---|
| I. Contract-First API Design | PASS | `contracts/auth-api.yaml`, `contracts/ticket-ownership-api.yaml`, and `contracts/chatbot-api.yaml` are written before controller code. All endpoints stay under `/api/v1/...`. No entity (`User` least of all — its password field) is ever serialized directly. |
| II. Domain Invariants Enforced in the Backend | PASS | Comment authorization (creator-or-assignee), single-ticket/comment view authorization (FR-037: creator/assignee/`SUPPORT`/`ADMIN`), and reassignment authorization (`ADMIN`-only) are data-dependent rules enforced in `TicketService`/`CommentService`, not the controller or client. The ticket status state machine itself is untouched. |
| III. Test-First, State Machine Covered | PASS | Contract and integration tests for login/logout, the now-blanket authentication gate (including the two view endpoints that were previously open), auto-assignment, `ADMIN`-only reassignment, comment authorization, listing scope, knowledge-base indexing, chatbot retrieval/grounding/no-match/conversation behavior, and retention cleanup are written before their implementations. |
| IV. PostgreSQL as the Single Source of Truth | PASS | Seven additive, forward-only Flyway migrations (`V3`–`V9`); `ddl-auto` stays `validate`; every new relationship gets a foreign key; every new lookup path gets an index (assignment workload, ownership scope, vector similarity via an HNSW index). The knowledge base's vectors live in this same PostgreSQL database via `pgvector` — no second datastore, so this feature introduces no new "source of truth" to reconcile. |
| V. Observability, Secrets Hygiene, Simplicity | PASS | Every login attempt, auto-assignment decision, and now every ticket status transition (including its actor — the gap the prior plan left open) is logged with the existing correlation ID. No password is ever logged/returned/stored in plain text. The two new external calls are the only new "service-like" dependency, and they are unavoidable (an LLM has to run *somewhere*) rather than optional infrastructure; `pgvector` was chosen specifically *to avoid* adding a vector-database service, per this principle's "no unjustified complexity" — the named concrete problem (semantic retrieval at ≤50k rows) does not require one. |
| Design Patterns: constructor injection | PASS | Every new class (`AuthController`, `ChatbotController`, `EmbeddingClient`/`ResolutionLlmClient` implementations, services) takes dependencies via constructor. |
| Design Patterns: atomicity / SAGA scope | PASS | Ticket creation (including auto-assignment) stays one local `@Transactional` unit of work. Knowledge-base indexing runs *after* the resolving transaction commits (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`), so a slow or failed embedding call can never roll back or block the ticket transition itself — deliberately decoupled, not a SAGA (no compensating action is needed; a missed index entry is simply picked up by the next resolution event, and is not a correctness failure of the ticket record). |

No violations. Re-checked after Phase 1 design (data-model.md, contracts/) — the new tables,
their indexes/foreign keys, and the three endpoint groups do not change any row above.
Complexity Tracking is empty — the one new "service-like" dependency (`pgvector`, not a new
service) is justified above.

## Project Structure

### Documentation (this feature)

```text
specs/005-auth-rag-chatbot/
├── plan.md               # This file
├── research.md           # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/
│   ├── auth-api.yaml
│   ├── ticket-ownership-api.yaml
│   └── chatbot-api.yaml
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
src/main/java/com/frequency/ticketing/
├── config/
│   ├── CorrelationIdFilter.java       # unchanged
│   ├── CorsConfig.java                # updated: allowCredentials(true), no wildcard origin
│   ├── OpenApiConfig.java             # unchanged
│   ├── SecurityConfig.java            # NEW: default-deny filter chain (permitAll: login only),
│   │                                  #   BCrypt bean
│   └── ChatbotProperties.java         # NEW: @ConfigurationProperties for similarity threshold,
│                                       #   retention days, inactivity timeout, AI endpoint config
├── domain/
│   ├── user/
│   │   ├── User.java, UserRole.java   # NEW
│   │   ├── UserService.java           # NEW: lookup, least-loaded-SUPPORT selection
│   │   └── CurrentUser.java           # NEW: resolves the authenticated User from SecurityContext
│   ├── auth/
│   │   └── AuthenticationService.java # NEW: login (authenticate + establish session), logout
│   ├── ticket/
│   │   ├── Ticket.java                # updated: assignee/createdBy become User references
│   │   ├── TicketService.java         # updated: auth-gated view (FR-037), auto-assignment,
│   │   │                              #   ADMIN-only reassign, ownership-scoped listing, actor
│   │   │                              #   added to the transition log line
│   │   └── ...                        # TicketStatus, TicketPriority, TransitionPolicy unchanged
│   ├── comment/
│   │   ├── Comment.java               # updated: adds authorId
│   │   └── CommentService.java        # updated: creator-or-assignee authorization
│   ├── knowledgebase/
│   │   ├── KnowledgeBaseEntry.java    # NEW entity (vector column via pgvector)
│   │   └── KnowledgeBaseService.java  # NEW: builds/refreshes an entry on ticket resolution
│   ├── chatbot/
│   │   ├── ChatbotConversation.java, ChatbotTurn.java   # NEW entities
│   │   ├── ChatbotService.java        # NEW: retrieval + grounding + no-match handling +
│   │   │                              #   conversation lifecycle (30-min boundary)
│   │   └── ConversationRetentionJob.java  # NEW: @Scheduled 90-day cleanup
│   ├── ai/
│   │   ├── EmbeddingClient.java, ResolutionLlmClient.java   # NEW interfaces
│   │   └── impl/
│   │       ├── HttpEmbeddingClient.java, HttpResolutionLlmClient.java  # NEW (production)
│   └── exception/
│       ├── InvalidCredentialsException.java, ForbiddenActionException.java   # NEW
│       └── AiServiceUnavailableException.java                                # NEW
├── repository/
│   ├── UserRepository.java            # NEW: email lookup, least-loaded-SUPPORT query
│   ├── KnowledgeBaseEntryRepository.java  # NEW: vector similarity query
│   ├── ChatbotConversationRepository.java, ChatbotTurnRepository.java  # NEW
│   ├── TicketRepository.java          # updated: scoped-listing + view-authorization queries
│   └── CommentRepository.java         # unchanged
└── web/
    ├── controller/
    │   ├── AuthController.java        # NEW: POST /api/v1/auth/login, /logout
    │   ├── ChatbotController.java     # NEW: POST /api/v1/chatbot/conversations,
    │   │                              #   POST /api/v1/chatbot/conversations/{id}/messages
    │   ├── TicketController.java      # updated: scope param, PATCH .../assignee, view auth
    │   └── TicketCommentController.java  # updated: response shape, view auth
    ├── dto/
    │   ├── LoginRequest.java, LoginResponse.java, UserSummary.java, ReassignRequest.java  # NEW
    │   ├── ChatbotQueryRequest.java, ChatbotResponseDto.java, ChatbotConversationResponse.java  # NEW
    │   ├── TicketCreateRequest.java, TicketUpdateRequest.java   # updated: assignee removed
    │   ├── TicketResponse.java, TicketDetailResponse.java       # updated: UserSummary fields
    │   └── CommentResponse.java       # updated: adds authorName
    └── exception/GlobalExceptionHandler.java  # updated: 401/403/503 mappings

src/main/resources/db/migration/
├── V3__create_users_table.sql
├── V4__ticket_ownership_and_assignment.sql
├── V5__comment_author.sql
├── V6__enable_pgvector.sql
├── V7__create_knowledge_base_entries.sql
├── V8__create_chatbot_conversations.sql
└── V9__create_chatbot_turns.sql

src/test/java/com/frequency/ticketing/  # contract + integration tests mirroring the above
```

**Structure Decision**: Extends the existing single Spring Boot microservice's layered
`web → domain → repository` structure with `domain.user`, `domain.auth`, `domain.knowledgebase`,
`domain.chatbot`, and `domain.ai` packages, following the same layering already used by
`domain.ticket`/`domain.comment`. No new module, service, or deployable — the RAG chatbot is a
capability inside this one service, not a separate system.

## Complexity Tracking

*No entries — Constitution Check has no violations to justify. `pgvector` and the two external
AI calls are the feature's unavoidable core requirement (spec FR-018–036), not optional
complexity layered on top of a simpler alternative.*
