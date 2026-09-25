# Tasks: Authentication, Auto-Assignment & Scoped Ticket Access

**⚠️ SUPERSEDED**: Written against 004 in isolation (see plan.md's superseded note). 003 and 004
are merged into [`specs/005-auth-rag-chatbot/spec.md`](../005-auth-rag-chatbot/spec.md); run
`/speckit-plan` and `/speckit-tasks` fresh against that spec instead of using this file.

**Input**: Design documents from `/specs/004-auth-ticket-access/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md,
contracts/auth-api.yaml, contracts/ticket-ownership-api.yaml, quickstart.md,
`.specify/memory/constitution.md`, and the already-implemented 001/002 codebase this feature
extends and amends.

**Tests**: Constitution Principle III (Test-First) is NON-NEGOTIABLE — test tasks below are
REQUIRED and MUST be written and failing before their corresponding implementation task.

**Organization**: Tasks are grouped by user story (spec.md): US1 = Log In (P1), US2 =
Auto-Assignment (P1), US3 = Comment Authorization (P2), US4 = Scoped Listing (P3). The `User`
entity, the three schema migrations, and the DTO/entity shape changes are shared by every story
(spec.md Key Entities: assignee/comment authorship become real identities everywhere at once), so
they sit in Foundational rather than being split or duplicated per story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 / US2 / US3 / US4 — maps to spec.md's user stories
- All paths under `src/main/java/com/frequency/ticketing/` and
  `src/test/java/com/frequency/ticketing/` (the existing 001/002 codebase)

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Add `spring-boot-starter-security` (compile scope) and `spring-security-test` (test
      scope) dependencies to `pom.xml`, no explicit version (managed by
      `spring-boot-starter-parent` 3.5.16's BOM, same convention as every other Spring Boot
      starter already in the file) — research.md "Authentication mechanism" decision

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The `User` entity, its schema, and the resulting shape changes to `Ticket`/`Comment`
and their DTOs that every user story depends on, plus the security filter chain every story
needs to know "who is calling."

**⚠️ CRITICAL**: No user story work can begin until this phase is complete — nothing else in this
feature compiles against the old `Ticket.assignee: String` / unauthenticated `Comment` shape.

- [ ] T002 Create Flyway migration `src/main/resources/db/migration/V3__create_users_table.sql`:
      `users` table with `id UUID PRIMARY KEY`, `name` ("Not null."), `email` ("Not null. Unique,
      case-insensitively (`LOWER(email)` unique index)"), `password_hash` ("Not null."),
      `role` ("Not null. Exactly one of `SUPPORT`, `GENERAL`, `ADMIN`"), `created_at`/`updated_at`
      `TIMESTAMPTZ NOT NULL`; a unique index on `LOWER(email)`; an index on `role` (data-model.md
      User table; research.md "Seed users")
- [ ] T003 Create Flyway migration
      `src/main/resources/db/migration/V4__ticket_ownership_and_assignment.sql`: add
      `tickets.created_by_id UUID NOT NULL REFERENCES users(id)` ("UUID FK to `users.id`, not
      null. The authenticated caller who created the ticket"), add `tickets.assignee_id UUID
      REFERENCES users(id)` ("nullable UUID FK to `users.id`... Nullable because a ticket MAY be
      created with no `SUPPORT` user available"), drop the old `tickets.assignee VARCHAR(200)`
      column, add index `idx_tickets_assignee_id_status ON tickets (assignee_id, status)` and
      `idx_tickets_created_by_id ON tickets (created_by_id)` (data-model.md Ticket table and New
      indexes; depends on T002 for the `users` table to exist first)
- [ ] T004 Create Flyway migration `src/main/resources/db/migration/V5__comment_author.sql`: add
      `comments.author_id UUID NOT NULL REFERENCES users(id)` ("UUID FK to `users.id`, not null.
      The authenticated caller who wrote the comment") (data-model.md Comment table; depends on
      T002)
- [ ] T005 [P] Create `UserRole` enum (`SUPPORT`, `GENERAL`, `ADMIN`) in
      `src/main/java/com/frequency/ticketing/domain/user/UserRole.java`
- [ ] T006 [P] Create `User` entity in
      `src/main/java/com/frequency/ticketing/domain/user/User.java`: `id` (UUID, generated),
      `name`, `email`, `passwordHash` (never exposed by a getter used in any response DTO —
      "never exposed by any DTO, never logged"), `role` (`UserRole`, `@Enumerated(EnumType.STRING)`
      matching the `Ticket`/`Comment` enum-storage convention), `createdAt`/`updatedAt` via
      `@PrePersist`/`@PreUpdate` (same pattern as `Ticket.java`) — depends on T005
- [ ] T007 [P] Create `UserRepository` in
      `src/main/java/com/frequency/ticketing/repository/UserRepository.java`:
      `Optional<User> findByEmailIgnoreCase(String email)` — depends on T006
- [ ] T008 Update `Ticket` entity in
      `src/main/java/com/frequency/ticketing/domain/ticket/Ticket.java`: remove the `assignee
      String` field; add `assigneeId` (`UUID`, nullable) and `createdById` (`UUID`, not null);
      change the constructor to `Ticket(String title, String description, TicketPriority
      priority, UUID createdById)` (assignee is never constructor-supplied per FR-008); remove
      `assignee` from `updateFields(...)` entirely (FR-016 — the general update path never
      changes the assignee again); add a plain `assignTo(UUID assigneeId)` method for
      `TicketService` to call (data-model.md Ticket "Validation/business rules added by this
      feature"; keeps the plain-UUID-column style `Comment.ticketId` already uses, no JPA
      `@ManyToOne`) — depends on T003
- [ ] T009 Update `Comment` entity in
      `src/main/java/com/frequency/ticketing/domain/comment/Comment.java`: add `authorId` (UUID,
      not null) to the constructor `Comment(UUID ticketId, String content, UUID authorId)` and as
      a field/getter (data-model.md Comment table) — depends on T004
- [ ] T010 [P] Create `UserSummary` DTO (`id`, `name`) in
      `src/main/java/com/frequency/ticketing/web/dto/UserSummary.java` — replaces the free-text
      `assignee` string wherever a user is referenced in a response (contracts/
      ticket-ownership-api.yaml `UserSummary` schema)
- [ ] T011 Update `TicketCreateRequest`/`TicketUpdateRequest` in
      `src/main/java/com/frequency/ticketing/web/dto/TicketCreateRequest.java` and
      `TicketUpdateRequest.java`: remove the `assignee` field from both records entirely (FR-008,
      FR-016; contracts/ticket-ownership-api.yaml `TicketCreateRequest`) — depends on T008
- [ ] T012 Update `TicketResponse`/`TicketDetailResponse`/`TicketMapper` in
      `src/main/java/com/frequency/ticketing/web/dto/TicketResponse.java`,
      `TicketDetailResponse.java`, `TicketMapper.java`: replace the `String assignee` field with
      `UserSummary assignee` (nullable) and add `UserSummary createdBy` (not null); `TicketMapper`
      resolves both via `UserRepository` lookups by `assigneeId`/`createdById` (contracts/
      ticket-ownership-api.yaml `TicketResponse`) — depends on T008, T010, T007
- [ ] T013 Update `CommentResponse`/`CommentMapper` in
      `src/main/java/com/frequency/ticketing/web/dto/CommentResponse.java`,
      `CommentMapper.java`: add `authorName` (resolved via `UserRepository` lookup by
      `authorId`) (contracts/ticket-ownership-api.yaml `CommentResponse`) — depends on T009, T007
- [ ] T014 Update `TicketService.create(...)` in
      `src/main/java/com/frequency/ticketing/domain/ticket/TicketService.java`: signature becomes
      `create(String title, String description, TicketPriority priority, UUID createdById)` (no
      `assignee` parameter); update `TicketController.create(...)` caller accordingly for now
      (auto-assignment itself lands in US2 — T014 only makes the codebase compile against the new
      `Ticket` constructor, leaving the ticket unassigned) — depends on T008
- [ ] T015 Create `SecurityConfig` in
      `src/main/java/com/frequency/ticketing/config/SecurityConfig.java`: `PasswordEncoder` bean
      (`BCryptPasswordEncoder`), `AuthenticationManager` bean, a `UserDetailsService`
      implementation (new `AppUserDetailsService` in
      `src/main/java/com/frequency/ticketing/domain/auth/AppUserDetailsService.java`, loading via
      T007's `findByEmailIgnoreCase` and mapping `role` to a `ROLE_<role>` authority), and the
      filter chain: `permitAll` on `POST /api/v1/auth/login`, `GET /actuator/health`,
      `GET /actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`,
      `GET /api/v1/tickets/{ticketId}`, `GET /api/v1/tickets/{ticketId}/comments`; CSRF disabled
      (research.md "CORS and CSRF"); `anyRequest().authenticated()` as the fallback, which also
      pre-secures `/api/v1/chatbot/**` before that controller exists (research.md "Endpoint access
      rules") — depends on T007
- [ ] T016 Update `CorsConfig` in
      `src/main/java/com/frequency/ticketing/config/CorsConfig.java`: change
      `.allowCredentials(false)` to `.allowCredentials(true)` so the session cookie can be sent/
      received cross-origin (research.md "CORS and CSRF"; origins stay the existing explicit
      allow-list, never a wildcard)
- [ ] T017 [P] Create `CurrentUser` component in
      `src/main/java/com/frequency/ticketing/domain/user/CurrentUser.java`: resolves the
      authenticated caller's `User` (id, role) from `SecurityContextHolder`, for use by
      `TicketService`/`CommentService` in later stories — depends on T006
- [ ] T018 [P] Create a `@Profile("dev")` `CommandLineRunner` seeder (new class, e.g.
      `src/main/java/com/frequency/ticketing/config/DevDataSeeder.java`): one `ADMIN`, two
      `SUPPORT`, one `GENERAL` user with known local-test credentials, hashed via the T015
      `PasswordEncoder` bean — never a Flyway migration (research.md "Seed users": "even a
      'fake' credential shipped in a migration that runs in every environment is exactly the
      pattern Principle V forbids") — depends on T006, T007, T015
- [ ] T019 Update the existing test suite so it compiles and passes again against T002–T014's
      schema/DTO changes before any new story work begins: `TicketCreateContractTest.java`,
      `TicketUpdateContractTest.java`, `TicketMapperTest.java`, `CommentMapperTest.java`,
      `TicketLifecycleIntegrationTest.java`, `TicketSearchFilterIntegrationTest.java`,
      `TicketConcurrencyIntegrationTest.java`, `TicketRestartDurabilityIntegrationTest.java`,
      `TicketStateMachineIntegrationTest.java`, `TicketValidationIntegrationTest.java`,
      `CommentIntegrationTest.java`, `CommentListIntegrationTest.java` — replace any direct
      `Ticket`/`Comment` construction or `TicketService.create(...)` call with the new
      `createdById` signature, using a fixed seeded/test user id where none is under test yet
      (all under `src/test/java/com/frequency/ticketing/`)

**Checkpoint**: Full existing suite green again on the new schema; the security filter chain
enforces authentication everywhere except the explicit `permitAll` list; no login endpoint is
exposed yet (that's US1).

---

## Phase 3: User Story 1 - Log In to Access the System (Priority: P1) 🎯 MVP

**Goal**: A user logs in with email/password and gets an authenticated session; unauthenticated
callers are rejected from ticket creation (and, once it exists, the chatbot); an authenticated
user can explicitly log out, ending that session.

**Independent Test**: Attempt ticket creation without logging in (401). Log in with valid
credentials, then create a ticket (succeeds). Log in with a wrong password (401, generic error,
same as an unregistered email). Log out, then attempt ticket creation again (401).

### Tests for User Story 1 ⚠️ (write first, confirm failing, per constitution Principle III)

- [ ] T020 [P] [US1] Contract test `POST /api/v1/auth/login` (200 `LoginResponse` for valid
      credentials per contracts/auth-api.yaml; 400 `ApiError` for missing `email`/`password`
      FR-004; 401 `ApiError` — same generic message for a wrong password and for an unregistered
      email, FR-003) in
      `src/test/java/com/frequency/ticketing/contract/AuthLoginContractTest.java`
- [ ] T021 [P] [US1] Contract test `POST /api/v1/auth/logout` (204 when authenticated; 401 with
      no session, per contracts/auth-api.yaml) in
      `src/test/java/com/frequency/ticketing/contract/AuthLogoutContractTest.java`
- [ ] T022 [US1] Integration test (Testcontainers PostgreSQL) covering Story 1's full scenario
      set: `POST /api/v1/tickets` without a session is rejected 401 and no ticket is created
      (FR-006, Scenario 4); after login, the same request succeeds (Scenario 1, 6); after
      `POST /api/v1/auth/logout`, the same request is rejected 401 again using the now-invalid
      cookie (FR-017, Scenario 7) in
      `src/test/java/com/frequency/ticketing/integration/AuthFlowIntegrationTest.java`

### Implementation for User Story 1

- [ ] T023 [P] [US1] Create `LoginRequest` (`email` `@NotBlank`, `password` `@NotBlank`) and
      `LoginResponse` (`id`, `name`, `email`, `role`) DTOs in
      `src/main/java/com/frequency/ticketing/web/dto/LoginRequest.java` and
      `LoginResponse.java` (contracts/auth-api.yaml)
- [ ] T024 [US1] Create `InvalidCredentialsException` in
      `src/main/java/com/frequency/ticketing/domain/exception/InvalidCredentialsException.java`
      and map it to `401` in `GlobalExceptionHandler` with one generic message that never reveals
      whether the email exists (FR-003) in
      `src/main/java/com/frequency/ticketing/web/exception/GlobalExceptionHandler.java`
- [ ] T025 [US1] Create `AuthenticationService` in
      `src/main/java/com/frequency/ticketing/domain/auth/AuthenticationService.java`:
      `login(email, password, HttpServletRequest)` authenticates via the T015
      `AuthenticationManager` and saves the resulting `SecurityContext` into the `HttpSession`
      (research.md "Authentication mechanism" — `HttpSessionSecurityContextRepository`),
      throwing `InvalidCredentialsException` on any failure without distinguishing wrong-password
      from unknown-email (FR-003); `logout(HttpServletRequest, HttpServletResponse)` invalidates
      the session (FR-017) — depends on T015
- [ ] T026 [US1] Create `AuthController` in
      `src/main/java/com/frequency/ticketing/web/controller/AuthController.java`:
      `POST /api/v1/auth/login` (`@Valid @RequestBody LoginRequest` → 400 on a missing field
      before T025 is even called, FR-004; delegates to T025; returns `LoginResponse`) and
      `POST /api/v1/auth/logout` (delegates to T025; `204`); `@Operation`/`@ApiResponses`
      annotated to match contracts/auth-api.yaml exactly (constitution Principle I) — depends on
      T023, T025
- [ ] T027 [US1] Add a `loginAs(UserRole role)` helper (returns an authenticated `MockMvc`
      request `RequestPostProcessor`/cookie, logging in as one of T018's seeded users) to
      `src/test/java/com/frequency/ticketing/support/AbstractIntegrationTest.java`, for every
      later story's tests to reuse — depends on T018, T026

**Checkpoint**: User Story 1 fully functional and independently testable — run T020–T022, all
green.

---

## Phase 4: User Story 2 - Tickets Are Auto-Assigned to the Least-Busy Support User (Priority: P1)

**Goal**: Every new ticket is assigned, with no caller input, to whichever `SUPPORT` user
currently has the fewest `OPEN`/`IN_PROGRESS` tickets; ties break deterministically; a ticket
created with no `SUPPORT` user available is left unassigned rather than rejected; an `ADMIN` can
manually reassign afterward.

**Independent Test**: Seed several `SUPPORT` users with different existing `OPEN`/`IN_PROGRESS`
counts, create a ticket, and confirm it lands on the one with the smallest count, with no
assignee in the request body.

### Tests for User Story 2 ⚠️

- [ ] T028 [P] [US2] Contract test `POST /api/v1/tickets`: response `assignee` is populated by
      the server even though the request body has no `assignee` field at all (contracts/
      ticket-ownership-api.yaml `TicketCreateRequest`/`TicketResponse`; FR-008) in
      `src/test/java/com/frequency/ticketing/contract/TicketCreateContractTest.java` (extends
      T019's already-updated file)
- [ ] T029 [US2] Integration test: three `SUPPORT` users with `OPEN`/`IN_PROGRESS` counts
      `[2, 0, 1]` → a new ticket is assigned to the one with `0` (FR-009); two `SUPPORT` users
      tied at the same lowest count → the same one is picked every time (ascending user id,
      FR-010) in
      `src/test/java/com/frequency/ticketing/integration/TicketAutoAssignmentIntegrationTest.java`
- [ ] T030 [US2] Integration test: no `SUPPORT`-role user exists → ticket creation still succeeds
      with `assignee: null` (FR-011, Edge Cases) in the same
      `TicketAutoAssignmentIntegrationTest.java`
- [ ] T031 [US2] Integration test: a ticket assigned to `SUPPORT` user A, transitioned to
      `RESOLVED`, no longer counts toward A's workload for the next assignment decision
      (Scenario 4) in the same `TicketAutoAssignmentIntegrationTest.java`
- [ ] T032 [P] [US2] Contract + integration test `PATCH /api/v1/tickets/{ticketId}/assignee`:
      `ADMIN` reassigns successfully (200, Scenario 5); a non-`ADMIN` caller gets 403 and the
      assignee is unchanged (Scenario 6); an `assigneeId` that is not a `SUPPORT` user gets 400
      (FR-016) in
      `src/test/java/com/frequency/ticketing/contract/TicketReassignContractTest.java`

### Implementation for User Story 2

- [ ] T033 [US2] Add a least-loaded-`SUPPORT`-user query to `UserRepository`: among
      `role = 'SUPPORT'` users, `LEFT JOIN` their tickets filtered to
      `status IN ('OPEN','IN_PROGRESS')`, `GROUP BY` user, `ORDER BY COUNT(...) ASC, u.id ASC`,
      return the first row (research.md "Auto-assignment algorithm"; FR-009, FR-010), backed by
      T003's `idx_tickets_assignee_id_status` index — depends on T007, T003
- [ ] T034 [US2] Update `TicketService.create(...)` to call T033 inside the same
      `@Transactional` unit of work as the insert, set `assigneeId` from the result (or leave it
      `null` per FR-011 when T033 returns none), and set `createdById` from `CurrentUser`
      (research.md's documented concurrency trade-off applies — no additional locking) — depends
      on T014, T017, T033
- [ ] T035 [P] [US2] Create `ReassignRequest` DTO (`assigneeId`, UUID) in
      `src/main/java/com/frequency/ticketing/web/dto/ReassignRequest.java` (contracts/
      ticket-ownership-api.yaml)
- [ ] T036 [US2] Add `TicketService.reassign(UUID ticketId, UUID assigneeId)`: loads the target
      `User`, rejects (400) if their role is not `SUPPORT`, otherwise calls `Ticket.assignTo(...)`
      — depends on T034
- [ ] T037 [US2] Add `PATCH /api/v1/tickets/{ticketId}/assignee` to `TicketController`,
      restricted to `ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`, FR-016), delegating to T036,
      annotated to match contracts/ticket-ownership-api.yaml — depends on T035, T036

**Checkpoint**: User Stories 1 AND 2 both work independently — run T020–T022 and T028–T032, all
green.

---

## Phase 5: User Story 3 - Only the Ticket's Creator or Assignee Can Comment, and Is Named (Priority: P2)

**Goal**: Only a ticket's creator or its currently assigned `SUPPORT` user can add a comment to
it; every comment shows the name of whoever wrote it.

**Independent Test**: As the ticket's creator, comment successfully (name shown). As the assigned
`SUPPORT` user, comment successfully (name shown). As an unrelated logged-in user, get rejected.

### Tests for User Story 3 ⚠️

- [ ] T038 [P] [US3] Contract + integration test `POST /api/v1/tickets/{ticketId}/comments`: the
      creator succeeds with `authorName` set to their name (Scenario 1); the assigned `SUPPORT`
      user succeeds with their name (Scenario 2); an unrelated logged-in user gets 403 and no
      comment is saved (Scenario 3, FR-012); retrieving the ticket afterward shows the comment's
      author name (Scenario 4, FR-013) in
      `src/test/java/com/frequency/ticketing/integration/CommentAuthorizationIntegrationTest.java`
      (builds on an auto-assigned ticket from US2)

### Implementation for User Story 3

- [ ] T039 [US3] Create `ForbiddenActionException` in
      `src/main/java/com/frequency/ticketing/domain/exception/ForbiddenActionException.java` and
      map it to `403` in `GlobalExceptionHandler`
- [ ] T040 [US3] Update `CommentService.addComment(...)` (or equivalent) in
      `src/main/java/com/frequency/ticketing/domain/comment/CommentService.java`: resolve the
      caller via `CurrentUser`, load the parent `Ticket`, and throw
      `ForbiddenActionException` unless the caller's id equals `ticket.createdById` or
      `ticket.assigneeId` (FR-012) before constructing the `Comment` with that caller's id as
      `authorId` — depends on T017, T009
- [ ] T041 [US3] Update `TicketCommentController` in
      `src/main/java/com/frequency/ticketing/web/controller/TicketCommentController.java` to stop
      accepting any author field from the request (there never was one — `CommentCreateRequest`
      stays `content`-only) and rely entirely on T040's `CurrentUser` resolution — depends on T040

**Checkpoint**: User Stories 1, 2, AND 3 all work independently — run T038 green alongside the
earlier stories' tests.

---

## Phase 6: User Story 4 - Filter the Ticket List by Ownership (Priority: P3)

**Goal**: `GET /api/v1/tickets` accepts a `scope` (`mine` / `assigned` / `all`) that narrows the
list to tickets the caller created, tickets assigned to them, or everything they're permitted to
see — combinable with the existing keyword/status filters.

**Independent Test**: As a user who both created some tickets and (if `SUPPORT`) is assigned
others, request each scope and confirm exactly the expected subset comes back for each.

### Tests for User Story 4 ⚠️

- [ ] T042 [P] [US4] Contract + integration test `GET /api/v1/tickets?scope=...`: `mine` returns
      only tickets the caller created (Scenario 1); `assigned` returns only tickets assigned to
      the caller, empty (not an error) for a `GENERAL` caller who is never an assignee (Scenario
      2); `all` returns every ticket for `SUPPORT`/`ADMIN` but narrows to the caller's own created
      tickets for `GENERAL` (research.md "Listing scope semantics"; default with no `scope`
      given, Scenario 3); `scope` combined with an existing `q`/`status` filter returns only
      tickets matching both (Scenario 4, FR-015) in
      `src/test/java/com/frequency/ticketing/integration/TicketScopedListingIntegrationTest.java`

### Implementation for User Story 4

- [ ] T043 [US4] Add scoped query methods to `TicketRepository` in
      `src/main/java/com/frequency/ticketing/repository/TicketRepository.java`: extend the
      existing native `search(...)` query with an optional `createdById`/`assigneeId` predicate
      pair driven by the resolved scope, backed by T003's `idx_tickets_created_by_id` and
      `idx_tickets_assignee_id_status` indexes
- [ ] T044 [US4] Add a `TicketScope` enum (`MINE`, `ASSIGNED`, `ALL`) in
      `src/main/java/com/frequency/ticketing/domain/ticket/TicketScope.java` and update
      `TicketService.search(...)` to resolve it against `CurrentUser`: `ALL` for a `GENERAL`
      caller is rewritten to the same predicate as `MINE` server-side (research.md "Listing scope
      semantics") — depends on T017, T043
- [ ] T045 [US4] Add the `scope` query parameter (default `all`) to `TicketController.list(...)`,
      wired to T044, matching contracts/ticket-ownership-api.yaml's `TicketScope` enum — depends
      on T044

**Checkpoint**: All four user stories independently functional — run T042 green alongside every
earlier story's tests.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T046 [P] Unit test for the T033 least-loaded-`SUPPORT` query's tie-break ordering
      (ascending user id) at the repository/query level, isolated from the full ticket-creation
      flow, in `src/test/java/com/frequency/ticketing/unit/UserRepositoryWorkloadQueryTest.java`
- [ ] T047 [P] Grep-verify no `@Autowired` field injection was introduced by this feature's new
      or edited files (constitution Design Patterns: constructor injection only) — same check
      pattern as 001's T055 / 002's T009
- [ ] T048 [P] Grep-verify `User.passwordHash` never appears in a log statement or a response DTO
      anywhere in the diff (constitution Principle V; spec SC-005)
- [ ] T049 Run `quickstart.md` end-to-end against a local build (`dev` profile, seeded users) and
      record results

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS every user story — the `User` entity,
  schema migrations, and DTO shape changes are shared prerequisites, not story-specific work.
- **User Story 1 (Phase 3)**: Depends on Foundational only.
- **User Story 2 (Phase 4)**: Depends on Foundational AND User Story 1 — ticket creation itself
  now requires an authenticated caller (T034 needs `CurrentUser`, and T027's `loginAs` test
  helper needs T026's login endpoint to exist). This is a real technical dependency, not just
  organizational, despite both stories being P1.
- **User Story 3 (Phase 5)**: Depends on Foundational, User Story 1 (authenticated caller), and
  User Story 2 (a meaningfully-assigned `SUPPORT` user to test the "assignee can comment"
  scenario against).
- **User Story 4 (Phase 6)**: Depends on Foundational and User Story 1 (authenticated caller to
  resolve "mine"/"assigned"). Independent of User Story 3.
- **Polish (Phase 7)**: Depends on whichever stories are in scope for a given delivery being
  complete.

### Within Each User Story

- Tests MUST be written and failing before their implementation task (constitution Principle
  III).
- Repository/query layer before service layer; service layer before controller.

### Parallel Opportunities

- T005–T007, T010, T017, T018 (Foundational) can run in parallel once their own listed
  dependencies are met — different files, no shared edits.
- T020–T021 (US1 tests) in parallel; T023 (US1 DTOs) in parallel with either.
- T028 and T032 (US2 tests) in parallel; T035 (US2 DTO) in parallel with test work.
- T042 (US4) can run in parallel with Phase 5 (US3) once Foundational + US1 are both done — the
  two stories touch disjoint files (`CommentService` vs. `TicketRepository`/`TicketService`
  listing methods).

---

## Parallel Example: Foundational Phase

```bash
# After T002-T004 (migrations) land:
Task: "Create UserRole enum in domain/user/UserRole.java"                      # T005
Task: "Create UserSummary DTO in web/dto/UserSummary.java"                     # T010
Task: "Create CurrentUser component in domain/user/CurrentUser.java"           # T017 (after T006)
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — blocks everything).
3. Complete Phase 3: User Story 1 (login/logout).
4. Complete Phase 4: User Story 2 (auto-assignment) — the two P1 stories together are the
   smallest slice that matches the original request's core ask (login-gated, auto-assigned
   tickets).
5. **STOP and VALIDATE**: run T020–T022 and T028–T032 independently; run `quickstart.md` steps
   1–3, 7.
6. Deploy/demo if ready.

### Incremental Delivery

1. Setup + Foundational → foundation ready, nothing user-visible yet.
2. Add User Story 1 → test independently → login/logout usable on its own (gates ticket
   creation, which is otherwise unchanged from 001).
3. Add User Story 2 → test independently → auto-assignment live (MVP complete).
4. Add User Story 3 → test independently → comment authorization enforced.
5. Add User Story 4 → test independently → scoped listing available.
6. Polish.

---

## Notes

- [P] tasks = different files, no dependency on an incomplete task.
- Constitution Principle III is non-negotiable here: every task above that changes behavior has a
  preceding test task in the same or an earlier phase.
- Foundational (T002–T019) is unusually large for this feature because the `User` entity and the
  `Ticket`/`Comment` shape change are genuinely shared by all four stories — see Task Generation
  Rules ("If entity serves multiple stories: Put in earliest story or Setup phase").
- Commit after each task or logical group; stop at any checkpoint to validate a story
  independently.
