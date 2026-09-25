# Tasks: List Comments

**Input**: Design documents from `/specs/002-list-comments/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/comments-list-api.yaml,
quickstart.md, `.specify/memory/constitution.md`, and the already-implemented
001-ticket-management-backend codebase this feature extends.

**Tests**: Constitution Principle III (Test-First) is NON-NEGOTIABLE — test tasks below are
REQUIRED and MUST be written and failing before their corresponding implementation task.

**Organization**: This feature has a single user story (spec.md P1). Foundational work is the
shared DB index and response DTO; everything else lives in that one story's phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 — maps to spec.md's single user story
- All paths under `src/main/java/com/frequency/ticketing/` and
  `src/test/java/com/frequency/ticketing/` (the existing 001 codebase)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization.

No new setup required — this feature extends the existing 001-ticket-management-backend
project unchanged (same `pom.xml`, same Spring Boot/Java version, no new dependency, same
`application.yml`). Proceed directly to Foundational.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared index and response shape every part of this feature needs.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 Create Flyway migration
      `src/main/resources/db/migration/V2__comments_list_index.sql`: additive composite index
      `(ticket_id, created_at)` on the existing `comments` table — no table structure change, no
      data migration (data-model.md Persistence Notes; research.md Query implementation
      decision)
- [X] T002 [P] Create `CommentPage` DTO in
      `src/main/java/com/frequency/ticketing/web/dto/CommentPage.java`: fields `content`
      (`List<CommentResponse>`), `page`, `size`, `totalElements`, `totalPages`, plus a static
      `from(Page<CommentResponse>)` factory — mirrors `TicketPage` exactly (data-model.md
      CommentPage table; research.md Response shape decision)

**Checkpoint**: Migration applies cleanly; `CommentPage` compiles. User story work can begin.

---

## Phase 3: User Story 1 - Browse a Ticket's Comment History (Priority: P1) 🎯 MVP

**Goal**: Retrieve a ticket's comments as their own paginated, ordered list — independent of
fetching the rest of the ticket — with each comment showing its content and timestamp.

**Independent Test**: Add several comments to a ticket, request its comment list on its own
(not via ticket detail), and confirm every comment appears in order with content and timestamp
populated; confirm an empty ticket returns `[]` not an error, and an unknown ticket returns 404.

### Tests for User Story 1 ⚠️ (write first, confirm failing, per constitution Principle III)

- [X] T003 [P] [US1] Contract test `GET /api/v1/tickets/{ticketId}/comments` (200 `CommentPage`
      shape with `content`/`createdAt` on every item per contracts/comments-list-api.yaml; 404
      `TICKET_NOT_FOUND` `ApiError` for an unknown ticket id — FR-004) in
      `src/test/java/com/frequency/ticketing/contract/CommentListContractTest.java`
- [X] T004 [US1] Integration test (Testcontainers PostgreSQL): add 3 comments to a ticket and
      list them, asserting oldest-to-newest order with `content` and `createdAt` populated for
      each (FR-002, FR-002a); a ticket with zero comments returns `content: []` and
      `totalElements: 0`, not an error (FR-003); a ticket with more comments than one page
      returns the correct subset and counts for `page=0` and `page=1` (FR-005) in
      `src/test/java/com/frequency/ticketing/integration/CommentListIntegrationTest.java`

### Implementation for User Story 1

- [X] T005 [US1] Add a paginated finder to `CommentRepository`:
      `Page<Comment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId, Pageable pageable)`, backed
      by the T001 composite index, in
      `src/main/java/com/frequency/ticketing/repository/CommentRepository.java` (depends on T001)
- [X] T006 [US1] Extend `CommentService` with
      `listByTicket(UUID ticketId, Pageable pageable)`: verifies the ticket exists (else
      `TicketNotFoundException`, FR-004), delegates to T005's finder, maps results to
      `CommentPage` via `CommentMapper`/`CommentPage.from`; single
      `@Transactional(readOnly = true)` unit of work (constitution Design Patterns: Atomicity —
      no cross-service call, so SAGA does not apply); constructor injection only, in
      `src/main/java/com/frequency/ticketing/domain/comment/CommentService.java` (depends on
      T002, T005)
- [X] T007 [US1] Add `GET /api/v1/tickets/{ticketId}/comments` to `TicketCommentController`:
      accepts `page`/`size` query params (default size 20, max 100 — same clamping convention as
      `TicketController`), delegates to T006, and is annotated with
      `@Operation`/`@ApiResponses`/`@Schema` matching contracts/comments-list-api.yaml exactly
      (constitution Principle I — written now, not retrofitted, per research.md's OpenAPI
      documentation decision) in
      `src/main/java/com/frequency/ticketing/web/controller/TicketCommentController.java`
      (depends on T006)

**Checkpoint**: User Story 1 fully functional and independently testable — run T003-T004, both
green.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Quality gates from constitution Development Workflow & Quality Gates.

- [X] T008 [P] Unit test for `CommentPage.from` (page/size/totalElements/totalPages mapped
      correctly from a Spring `Page`) in
      `src/test/java/com/frequency/ticketing/unit/CommentPageTest.java`
- [X] T009 [P] Grep-verify no `@Autowired` field injection was introduced by this feature's new
      or edited files (constitution Design Patterns: constructor injection only) — re-run the
      same check as 001-ticket-management-backend T055
- [X] T010 Run `quickstart.md` end-to-end against a local build and record results

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Nothing to do — proceed directly to Foundational
- **Foundational (Phase 2)**: T001, T002 — BLOCKS User Story 1
- **User Story 1 (Phase 3)**: Depends on Foundational only; reuses `Comment` (entity),
  `CommentRepository`, `CommentService`, `TicketCommentController`, and `CommentMapper` from
  001-ticket-management-backend
- **Polish (Phase 4)**: Depends on User Story 1 being complete

### Within User Story 1

- Tests (T003, T004) MUST be written and failing before T005-T007 (constitution Principle III)
- Repository (T005) before service (T006) before controller (T007)

### Parallel Opportunities

- Foundational: T001, T002 in parallel (different files)
- User Story 1 tests: T003 in parallel with T004 (different files)
- Polish: T008, T009 in parallel

---

## Parallel Example: Foundational + User Story 1 tests

```bash
# Foundational, launched together:
Task: "Create V2__comments_list_index.sql in src/main/resources/db/migration/"
Task: "Create CommentPage DTO in src/main/java/.../web/dto/CommentPage.java"

# User Story 1 tests, launched together (after Foundational):
Task: "Contract test GET /api/v1/tickets/{ticketId}/comments in src/test/java/.../contract/CommentListContractTest.java"
Task: "Integration test comment ordering/empty/pagination in src/test/java/.../integration/CommentListIntegrationTest.java"
```

---

## Implementation Strategy

### MVP First (and only)

1. Complete Phase 2: Foundational
2. Complete Phase 3: User Story 1 — this is the whole feature
3. **STOP and VALIDATE**: run T003-T004 green, then `quickstart.md`
4. Complete Phase 4: Polish

### Task Count Summary

- Phase 1 (Setup): 0 tasks — nothing new to set up
- Phase 2 (Foundational): 2 tasks
- Phase 3 (User Story 1): 5 tasks (2 tests + 3 implementation)
- Phase 4 (Polish): 3 tasks
- **Total: 10 tasks (T001-T010)**

---

## Notes

- [P] tasks touch different files with no unmet dependency
- Every constraint quoted from data-model.md is binding — do not relax at implementation time
- Verify each test fails before writing its implementation (constitution Principle III)
- Commit after each task or logical group
- This feature reuses 001-ticket-management-backend's `Comment` entity, `CommentMapper`, and
  `AbstractIntegrationTest` test base unchanged — no duplication, only extension
