# Tasks: Ticket Management Backend

**Input**: Design documents from `/specs/001-ticket-management-backend/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/tickets-api.yaml, quickstart.md, `.specify/memory/constitution.md`

**Tests**: Constitution Principle III (Test-First, State Machine Covered) is NON-NEGOTIABLE for
this project — test tasks below are REQUIRED, not optional, and MUST be written and failing
before their corresponding implementation task.

**Organization**: Tasks are grouped by user story (spec.md priorities P1/P2/P3) so each story is
independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 — maps to spec.md user stories
- Base package: `com.frequency.ticketing` under `src/main/java/com/frequency/ticketing/`;
  tests under `src/test/java/com/frequency/ticketing/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization per plan.md Project Structure

- [X] T001 Create Maven project skeleton: `pom.xml` at repo root with `spring-boot-starter-parent`,
      `<java.version>25</java.version>`, groupId `com.frequency`, artifactId `ticketing-system`
      (research.md: Maven)
- [X] T002 [P] Add Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`) at repo root
- [X] T003 Add dependencies to `pom.xml`: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
      `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `flyway-core`,
      `flyway-database-postgresql`, `org.postgresql:postgresql`,
      `org.springdoc:springdoc-openapi-starter-webmvc-ui`, and test-scope
      `spring-boot-starter-test`, `org.testcontainers:junit-jupiter`,
      `org.testcontainers:postgresql`, `org.assertj:assertj-core` (research.md decisions)
- [X] T004 [P] Configure Spotless (google-java-format) in `pom.xml` for lint/format
- [X] T005 [P] Create `src/main/resources/application.yml` (server port, `spring.datasource.*`
      from env vars, `spring.jpa.hibernate.ddl-auto: validate`, `spring.flyway.enabled: true`,
      management endpoints `health,info` exposed) per constitution Principle IV/V
- [X] T006 [P] Create `src/test/resources/application-test.yml` (test profile; datasource
      overridden by Testcontainers at runtime)
- [X] T007 Create `src/main/java/com/frequency/ticketing/TicketingApplication.java`
      (`@SpringBootApplication` main class)

**Checkpoint**: `./mvnw spring-boot:run` starts an empty Spring Boot app.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, entities, DTOs, and cross-cutting infrastructure every user story needs.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T008 Create Flyway migration `src/main/resources/db/migration/V1__init_schema.sql`:
      enable `pg_trgm` extension; `tickets` table (`id UUID PK`, `title VARCHAR(200) NOT NULL`,
      `description TEXT NOT NULL`, `priority VARCHAR NOT NULL`, `status VARCHAR NOT NULL`,
      `assignee VARCHAR(200)`, `version BIGINT NOT NULL DEFAULT 0`,
      `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL`); `comments` table
      (`id UUID PK`, `ticket_id UUID NOT NULL REFERENCES tickets(id)`, `content TEXT NOT NULL`,
      `created_at TIMESTAMPTZ NOT NULL`); GIN `pg_trgm` indexes on `tickets.title` and
      `tickets.description`; B-tree index on `tickets.status` (data-model.md Persistence Notes)
- [X] T009 [P] Create `TicketStatus` enum in
      `src/main/java/com/frequency/ticketing/domain/ticket/TicketStatus.java`:
      `OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED` (data-model.md)
- [X] T010 [P] Create `TicketPriority` enum in
      `src/main/java/com/frequency/ticketing/domain/ticket/TicketPriority.java`:
      `LOW, MEDIUM, HIGH, CRITICAL` (Clarifications session 2026-09-21)
- [X] T011 Create `Ticket` JPA entity in
      `src/main/java/com/frequency/ticketing/domain/ticket/Ticket.java`: `id` (UUID PK,
      generated), `title` (required, non-blank, max 200 chars), `description` (required,
      non-blank), `priority` (required, `TicketPriority`, stored as string not ordinal),
      `status` (required, `TicketStatus`, stored as string not ordinal, mutated only via
      transition policy — never a public setter used outside it), `assignee` (nullable,
      free-form string), `version` (`@Version` optimistic locking), `createdAt`/`updatedAt`
      (`Instant`, UTC) (data-model.md Ticket table)
- [X] T012 Create `Comment` JPA entity in
      `src/main/java/com/frequency/ticketing/domain/comment/Comment.java`: `id` (UUID PK),
      `ticketId` (FK, required), `content` (required, non-blank), `createdAt` (`Instant`, UTC),
      no `updatedAt` (data-model.md Comment table)
- [X] T013 [P] Create `TicketRepository` in
      `src/main/java/com/frequency/ticketing/repository/TicketRepository.java`
      (`JpaRepository<Ticket, UUID>`, `Pageable`-returning finder methods)
- [X] T014 [P] Create `CommentRepository` in
      `src/main/java/com/frequency/ticketing/repository/CommentRepository.java`
      (`JpaRepository<Comment, UUID>`, `findByTicketIdOrderByCreatedAtAsc`)
- [X] T015 Create `ApiError`/`ApiFieldError` DTOs in
      `src/main/java/com/frequency/ticketing/web/dto/ApiError.java`: `code` (one of
      `VALIDATION_FAILED, TICKET_NOT_FOUND, INVALID_TRANSITION, TICKET_CONFLICT`), `message`,
      `timestamp`, `path`, `fieldErrors[]` of `{field, message}` (contracts/tickets-api.yaml
      `ApiError` schema; constitution Principle I: one consistent error shape)
- [X] T016 [P] Create domain exceptions in
      `src/main/java/com/frequency/ticketing/domain/exception/`: `TicketNotFoundException`,
      `InvalidTransitionException`, `TicketConflictException`
- [X] T017 Create `GlobalExceptionHandler` (`@ControllerAdvice`) in
      `src/main/java/com/frequency/ticketing/web/exception/GlobalExceptionHandler.java`:
      `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` with `fieldErrors`;
      `TicketNotFoundException` → 404 `TICKET_NOT_FOUND`; `InvalidTransitionException` → 409
      `INVALID_TRANSITION`; `TicketConflictException` /
      `ObjectOptimisticLockingFailureException` → 409 `TICKET_CONFLICT`; no stack traces or
      internal identifiers leaked (constitution Principle I)
- [X] T018 [P] Create correlation-id logging filter + structured JSON logging config in
      `src/main/java/com/frequency/ticketing/config/LoggingConfig.java` and
      `src/main/resources/logback-spring.xml` (constitution Principle V)
- [X] T019 [P] Configure Actuator readiness to reflect DB connectivity in `application.yml`
      (constitution Principle V)
- [X] T020 [P] Create `OpenApiConfig` in
      `src/main/java/com/frequency/ticketing/config/OpenApiConfig.java` wiring springdoc
      (constitution Principle I)

**Checkpoint**: Schema migrates cleanly; app starts; `/actuator/health` returns `UP`. User story
work can now begin.

---

## Phase 3: User Story 1 - Manage Ticket Lifecycle (Priority: P1) 🎯 MVP

**Goal**: Create, list, view, and update tickets, and enforce the status state machine so
illegal transitions (especially any reversal to `OPEN`) are rejected server-side.

**Independent Test**: Create a ticket via the API, retrieve it individually and in the list,
update its fields, drive it through the full legal transition path to `CLOSED`, and confirm
`CLOSED → OPEN` (and every other disallowed move) is rejected with `409 INVALID_TRANSITION`.

### Tests for User Story 1 ⚠️ (write first, confirm failing, per constitution Principle III)

- [X] T021 [P] [US1] Contract test `POST /api/v1/tickets` (201, body echoes
      `TicketResponse`, status `OPEN`) in
      `src/test/java/com/frequency/ticketing/contract/TicketCreateContractTest.java`
- [X] T022 [P] [US1] Contract test `GET /api/v1/tickets` (200, `TicketPage` shape) in
      `src/test/java/com/frequency/ticketing/contract/TicketListContractTest.java`
- [X] T023 [P] [US1] Contract test `GET /api/v1/tickets/{id}` (200 `TicketDetailResponse` with
      `comments: []`; 404 `ApiError` for unknown id) in
      `src/test/java/com/frequency/ticketing/contract/TicketDetailContractTest.java`
- [X] T024 [P] [US1] Contract test `PATCH /api/v1/tickets/{id}` (200 on valid partial update;
      confirm request body accepting a `status` field is ignored/rejected — status is
      unreachable via this endpoint per FR-013) in
      `src/test/java/com/frequency/ticketing/contract/TicketUpdateContractTest.java`
- [X] T025 [P] [US1] Contract test `POST /api/v1/tickets/{id}/transitions` (200 on legal
      transition; 409 `INVALID_TRANSITION` on illegal) in
      `src/test/java/com/frequency/ticketing/contract/TicketTransitionContractTest.java`
- [X] T026 [P] [US1] Unit test `TicketStatusTransitionPolicy`: all 5 legal edges succeed;
      exhaustively assert every other (from, to) pair — including `CLOSED→OPEN`,
      `RESOLVED→OPEN`, `CANCELLED→OPEN` — is rejected (FR-009, FR-010) in
      `src/test/java/com/frequency/ticketing/unit/TicketStatusTransitionPolicyTest.java`
- [X] T027 [US1] Integration test (Testcontainers PostgreSQL): full transition matrix through
      the HTTP API — `OPEN→IN_PROGRESS→RESOLVED→CLOSED`, `OPEN→CANCELLED`,
      `IN_PROGRESS→CANCELLED`, plus rejected reversals — asserting the ticket's persisted
      status is unchanged after each rejection (SC-002, SC-006) in
      `src/test/java/com/frequency/ticketing/integration/TicketStateMachineIntegrationTest.java`
- [X] T028 [US1] Integration test: create → list → view → update happy path, asserting
      submitted fields are intact on every read (SC-001) in
      `src/test/java/com/frequency/ticketing/integration/TicketLifecycleIntegrationTest.java`
- [X] T029 [US1] Integration test: write a ticket, rebuild the Spring `ApplicationContext`
      (simulated restart) against the same Testcontainers volume, re-read, assert full equality
      (FR-008, SC-003) in
      `src/test/java/com/frequency/ticketing/integration/TicketRestartDurabilityIntegrationTest.java`
- [X] T030 [US1] Integration test: blank title, blank description, and invalid priority value
      each rejected with 400 `VALIDATION_FAILED` and per-field detail before any row is written
      (FR-011, SC-005) in
      `src/test/java/com/frequency/ticketing/integration/TicketValidationIntegrationTest.java`
- [X] T031 [US1] Integration test (Testcontainers PostgreSQL): load the same ticket into two
      separate persistence contexts, update and save the first (its `version` advances), then
      update and save the second stale copy — assert the second save is rejected with 409
      `TICKET_CONFLICT` and the ticket's persisted state matches only the first, successful
      update (constitution Principle IV: optimistic locking; spec Edge Cases: concurrent
      update; SC-005) in
      `src/test/java/com/frequency/ticketing/integration/TicketConcurrencyIntegrationTest.java`

### Implementation for User Story 1

- [X] T032 [P] [US1] Create `TicketCreateRequest`/`TicketUpdateRequest` DTOs in
      `src/main/java/com/frequency/ticketing/web/dto/` with Bean Validation: `title`
      `@NotBlank @Size(max = 200)`, `description` `@NotBlank`, `priority` `@NotNull` on create
      (defaults applied in service if omitted), `assignee` optional (data-model.md Validation
      Rules Summary)
- [X] T033 [P] [US1] Create `TicketResponse`/`TicketDetailResponse`/`TicketPage`/
      `TicketTransitionRequest` DTOs in `src/main/java/com/frequency/ticketing/web/dto/`
      matching `contracts/tickets-api.yaml` schemas exactly
- [X] T034 [US1] Create `TicketMapper` (hand-written, per research.md) in
      `src/main/java/com/frequency/ticketing/web/dto/TicketMapper.java` (depends on T032, T033)
- [X] T035 [US1] Implement `TicketStatusTransitionPolicy` in
      `src/main/java/com/frequency/ticketing/domain/ticket/TicketStatusTransitionPolicy.java`:
      static `Map<TicketStatus, Set<TicketStatus>>` exactly matching data-model.md's State
      Transitions table; throws `InvalidTransitionException` for anything else (depends on T009,
      T016)
- [X] T036 [US1] Implement `TicketService` in
      `src/main/java/com/frequency/ticketing/domain/ticket/TicketService.java`: `create`
      (defaults `priority` to `MEDIUM` when omitted per data-model.md edge case), `getById`
      (throws `TicketNotFoundException`), `list` (paginated), `update` (title/description/
      priority/assignee only — never touches `status`, per FR-013), `applyTransition`
      (delegates to `TicketStatusTransitionPolicy`, is the ONLY method that writes `status`);
      every method wrapped in one local `@Transactional` boundary (constitution Design
      Patterns: Atomicity); constructor injection only (depends on T011, T013, T035)
- [X] T037 [US1] Implement `TicketController` in
      `src/main/java/com/frequency/ticketing/web/controller/TicketController.java`:
      `POST /api/v1/tickets`, `GET /api/v1/tickets`, `GET /api/v1/tickets/{id}`,
      `PATCH /api/v1/tickets/{id}`, `POST /api/v1/tickets/{id}/transitions`, matching
      `contracts/tickets-api.yaml` request/response shapes exactly; constructor injection only
      (depends on T034, T036)
- [X] T038 [US1] Add ticket-transition audit logging (ticket id, previous status, new status) in
      `TicketService.applyTransition` (constitution Principle V) (depends on T036)

**Checkpoint**: User Story 1 fully functional and independently testable — run T021-T031, all
green.

---

## Phase 4: User Story 2 - Collaborate Through Comments (Priority: P2)

**Goal**: Add timestamped comments to an existing ticket and retrieve them, in order, as part
of the ticket's detail view.

**Independent Test**: Create a ticket, add several comments, retrieve the ticket and confirm
the comments appear in the order added; confirm empty content and an unknown ticket id are both
rejected.

### Tests for User Story 2 ⚠️ (write first, confirm failing)

- [X] T039 [P] [US2] Contract test `POST /api/v1/tickets/{id}/comments` (201 on valid content;
      400 `VALIDATION_FAILED` on empty content; 404 `TICKET_NOT_FOUND` on unknown ticket id) in
      `src/test/java/com/frequency/ticketing/contract/CommentCreateContractTest.java`
- [X] T040 [US2] Integration test: add 3 comments to a ticket, retrieve ticket detail, assert
      comments returned in ascending `createdAt` order with content intact (FR-005) in
      `src/test/java/com/frequency/ticketing/integration/CommentIntegrationTest.java`

### Implementation for User Story 2

- [X] T041 [P] [US2] Create `CommentCreateRequest`/`CommentResponse` DTOs in
      `src/main/java/com/frequency/ticketing/web/dto/`: `content` `@NotBlank`
      (data-model.md Comment validation)
- [X] T042 [P] [US2] Create `CommentMapper` in
      `src/main/java/com/frequency/ticketing/web/dto/CommentMapper.java` (depends on T041)
- [X] T043 [US2] Implement `CommentService` in
      `src/main/java/com/frequency/ticketing/domain/comment/CommentService.java`: `addComment`
      verifies the parent ticket exists (else `TicketNotFoundException`) before persisting,
      single local `@Transactional` boundary, constructor injection (depends on T012, T014, T016)
- [X] T044 [US2] Implement `TicketCommentController` in
      `src/main/java/com/frequency/ticketing/web/controller/TicketCommentController.java`:
      `POST /api/v1/tickets/{id}/comments` (depends on T042, T043)
- [X] T045 [US2] Update `TicketMapper`/`TicketDetailResponse` to populate the ordered `comments`
      list on ticket detail retrieval (depends on T034, T042)

**Checkpoint**: User Stories 1 AND 2 both independently functional.

---

## Phase 5: User Story 3 - Find Tickets by Keyword and Status (Priority: P3)

**Goal**: Search tickets by keyword across title/description, filter by status, and combine
both in one request.

**Independent Test**: Seed several tickets with distinct titles/descriptions and statuses;
issue a keyword search and a status filter and confirm each returns exactly the expected
subset; confirm a no-match keyword returns an empty list, not an error.

### Tests for User Story 3 ⚠️ (write first, confirm failing)

- [X] T046 [P] [US3] Contract test `GET /api/v1/tickets?q=...` and `?status=...` (200,
      `TicketPage` shape for both, and combined) in
      `src/test/java/com/frequency/ticketing/contract/TicketSearchFilterContractTest.java`
- [X] T047 [US3] Integration test: keyword match on title/description, empty result for a
      non-matching keyword (not an error), status-only filter, and keyword+status combined
      (FR-006, FR-007) in
      `src/test/java/com/frequency/ticketing/integration/TicketSearchFilterIntegrationTest.java`
- [X] T048 [US3] Integration test: seed ≥10,000 tickets, assert keyword search and status
      filter each return correct results in under 2 seconds (SC-004) in
      `src/test/java/com/frequency/ticketing/integration/TicketSearchPerformanceIntegrationTest.java`

### Implementation for User Story 3

- [X] T049 [US3] Add a `pg_trgm`-backed search query to `TicketRepository`: a `@Query` combining
      case-insensitive `ILIKE` on `title`/`description` with an optional exact `status` match,
      returning a `Page<Ticket>` (research.md: keyword search decision) (depends on T013)
- [X] T050 [US3] Extend `TicketService` with a `search(keyword, status, pageable)` method
      delegating to the new repository query (depends on T036, T049)
- [X] T051 [US3] Extend `TicketController`'s `GET /api/v1/tickets` to accept optional `q` and
      `status` query params (plus existing `page`/`size`, default size 20, max 100) and
      delegate to `TicketService.search` (depends on T037, T050)

**Checkpoint**: All three user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Quality gates from constitution Development Workflow & Quality Gates.

- [X] T052 [P] Unit tests for `TicketMapper`/`CommentMapper` field-by-field mapping in
      `src/test/java/com/frequency/ticketing/unit/`
- [X] T053 [P] Unit tests for Bean Validation constraints (blank title, oversized title,
      invalid priority/status values) in `src/test/java/com/frequency/ticketing/unit/`
- [X] T054 Run `quickstart.md` end-to-end manually against a local build and record results
- [X] T055 [P] Grep-verify no `@Autowired` field injection exists anywhere in
      `src/main/java/` (constitution Design Patterns: constructor injection only)
- [X] T056 [P] Write `README.md` at repo root: local run instructions (Docker Postgres, Flyway,
      `./mvnw spring-boot:run`, `./mvnw test`)
- [ ] T057 Run a dependency vulnerability scan (e.g. OWASP Dependency-Check Maven plugin) and
      resolve any new high/critical finding (constitution Development Workflow gate 5)
- [X] T058 Run a secret scan across the working tree and confirm clean (constitution
      Development Workflow gate 6; spec "No secrets are committed")

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational only
- **User Story 2 (Phase 4)**: Depends on Foundational; reuses `Ticket` (T011) and
  `GlobalExceptionHandler` (T017) from Foundational, and `TicketDetailResponse`/`TicketMapper`
  (T033/T034) from User Story 1 for T045 — otherwise independently testable via T039/T040
- **User Story 3 (Phase 5)**: Depends on Foundational; extends `TicketRepository` (T013),
  `TicketService` (T036), and `TicketController` (T037) from User Story 1 — otherwise
  independently testable via T046-T048
- **Polish (Phase 6)**: Depends on all three user stories being complete

### Within Each User Story

- Tests (T021-T031, T039-T040, T046-T048) MUST be written and failing before their matching
  implementation task (constitution Principle III)
- Entities/DTOs before services; services before controllers
- Story complete and its checkpoint green before starting the next priority

### Parallel Opportunities

- Setup: T002, T003 (after T001), T004, T005, T006 in parallel
- Foundational: T009, T010 in parallel; T013, T014 in parallel (after T011/T012); T016, T018,
  T019, T020 in parallel
- User Story 1 tests: T021-T026 in parallel (different files); T027-T031 sequential (share the
  same Testcontainers integration test fixtures/state)
- User Story 1 implementation: T032, T033 in parallel
- User Story 2 tests: T039 parallel with nothing else in its phase (T040 depends on the
  implementation tasks below it existing to fail meaningfully against)
- User Story 2 implementation: T041, T042 in parallel
- Once Foundational is done, User Story 2's and User Story 3's test-writing (T039, T046) can
  start in parallel with User Story 1 if staffed — their implementation tasks depend on User
  Story 1's T013/T036/T037 landing first, per the Phase Dependencies above
- Polish: T052, T053, T055, T056 in parallel

---

## Parallel Example: User Story 1

```bash
# Launch all contract/unit tests for User Story 1 together:
Task: "Contract test POST /api/v1/tickets in src/test/java/.../contract/TicketCreateContractTest.java"
Task: "Contract test GET /api/v1/tickets in src/test/java/.../contract/TicketListContractTest.java"
Task: "Contract test GET /api/v1/tickets/{id} in src/test/java/.../contract/TicketDetailContractTest.java"
Task: "Contract test PATCH /api/v1/tickets/{id} in src/test/java/.../contract/TicketUpdateContractTest.java"
Task: "Contract test POST /api/v1/tickets/{id}/transitions in src/test/java/.../contract/TicketTransitionContractTest.java"
Task: "Unit test TicketStatusTransitionPolicy in src/test/java/.../unit/TicketStatusTransitionPolicyTest.java"

# Launch DTO creation together:
Task: "Create TicketCreateRequest/TicketUpdateRequest DTOs in src/main/java/.../web/dto/"
Task: "Create TicketResponse/TicketDetailResponse/TicketPage/TicketTransitionRequest DTOs in src/main/java/.../web/dto/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (blocks everything)
3. Complete Phase 3: User Story 1 — this alone delivers a working ticket system with the
   enforced state machine, the product's core correctness requirement
4. **STOP and VALIDATE**: run T021-T031 green, then `quickstart.md` §3
5. Demo/deploy if ready

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. User Story 1 → validate independently → MVP
3. User Story 2 → validate independently (comments layer on top, nothing in US1 changes)
4. User Story 3 → validate independently (search/filter layers on top of US1's list endpoint)

### Task Count Summary

- Phase 1 (Setup): 7 tasks
- Phase 2 (Foundational): 13 tasks
- Phase 3 (User Story 1): 18 tasks (11 tests + 7 implementation)
- Phase 4 (User Story 2): 7 tasks (2 tests + 5 implementation)
- Phase 5 (User Story 3): 6 tasks (3 tests + 3 implementation)
- Phase 6 (Polish): 7 tasks
- Post-implementation fix: 1 task
- **Total: 59 tasks (T001-T058, T059)**

---

## Post-Implementation Fixes

Found and fixed after the original 58 tasks were complete — appended rather than inserted, to
avoid renumbering an already-executed task list.

- [X] T059 Add CORS policy so a browser-hosted frontend on a different origin can call
      `/api/v1/**` (found via `/speckit-analyze`: Spring Boot has no CORS policy by default, so
      every cross-origin browser request — e.g. from a local frontend dev server — was silently
      blocked, not just for ticket creation but every endpoint). Added
      `app.cors.allowed-origins` (env-var `CORS_ALLOWED_ORIGINS`, dev default
      `http://localhost:5173,http://localhost:3000`) in
      `src/main/resources/application.yml`, and `CorsConfig` (`WebMvcConfigurer`, constructor
      injection, no wildcard origin) in
      `src/main/java/com/frequency/ticketing/config/CorsConfig.java`; contract test in
      `src/test/java/com/frequency/ticketing/contract/CorsConfigContractTest.java` (allowed
      origin gets `Access-Control-Allow-Origin`, disallowed origin does not). Verified live
      against a running instance: preflight `OPTIONS` and actual `POST` from
      `Origin: http://localhost:5173` both succeed with the correct header; `Origin:
      http://evil.example.com` gets no such header.

---

## Notes

- [P] tasks touch different files with no unmet dependency
- [Story] label maps every user-story-phase task to spec.md's US1/US2/US3 for traceability
- Every constraint quoted from data-model.md is binding — do not relax at implementation time
- Verify each test fails before writing its implementation (constitution Principle III)
- Commit after each task or logical group
- Stop at each checkpoint to validate that story independently before continuing
