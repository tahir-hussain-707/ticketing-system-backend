# Implementation Plan: List Comments

**Branch**: `002-list-comments` | **Date**: 2026-09-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-list-comments/spec.md`

## Summary

Add a standalone, paginated way to retrieve a ticket's comment history —
`GET /api/v1/tickets/{ticketId}/comments` — independent of fetching the rest of the ticket
record. Each returned comment shows its content and timestamp (Clarifications session
2026-09-21). This extends 001-ticket-management-backend: no new entity, no schema change beyond
an added index for the sort this endpoint relies on at scale; it reuses the existing `Comment`
entity, `CommentRepository`, `CommentService`, and `TicketCommentController` from that feature.

## Technical Context

**Language/Version**: Java 25 (LTS) — unchanged from 001, constitution-fixed.

**Primary Dependencies**: Spring Boot 3.5.16 (Spring Web, Spring Data JPA, Spring Validation,
Actuator), Flyway, springdoc-openapi — all already present in the codebase from
001-ticket-management-backend; no new dependency required.

**Storage**: PostgreSQL 16+, same `comments` table from 001. One additive migration: a composite
index on `(ticket_id, created_at)` to back this endpoint's sort at the volumes SC-002 targets
(the existing `idx_comments_ticket_id` index is single-column and does not cover the ordering).

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), AssertJ — same suite,
extended with tests for the new endpoint.

**Target Platform**: Same single Spring Boot microservice; no new deployable, no new service.

**Project Type**: Backend extension to an existing web-service (not a new project).

**Performance Goals**: Comment-list retrieval for a ticket with up to 1,000 comments in under 2
seconds (spec SC-002) — same order of magnitude as 001's search target, met the same way
(indexed query + pagination, not full-table scans).

**Constraints**: Reuses constitution-mandated patterns already established in 001: list
endpoints paginated (constitution Principle IV), one consistent `ApiError` shape for the
not-found case (constitution Principle I), constructor injection only, single local
`@Transactional(readOnly = true)` read (no SAGA — no cross-service call here). This plan closes
a gap found during 001's implementation: every new endpoint MUST carry real
`@Operation`/`@ApiResponse` OpenAPI annotations from the start, not added after the fact.

**Scale/Scope**: One new read endpoint, one new DTO (`CommentPage`), one additive index
migration. No new entities, no change to how comments are created (out of scope per spec
Assumptions).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | How this plan satisfies it |
|---|---|---|
| I. Contract-First API Design | PASS | New endpoint added to `contracts/comments-list-api.yaml` before controller code; reuses the existing `CommentResponse`/`ApiError` DTOs; annotated with `@Operation`/`@ApiResponse` from the first commit (closing 001's gap). |
| II. Domain Invariants Enforced in Backend | N/A | This feature is read-only; it introduces no new state or transition. |
| III. Test-First, State Machine Covered | PASS | Contract test for the new endpoint (200 paginated shape, 404 unknown ticket, empty-list case) and an ordering/pagination integration test are written before the implementation, per Phase 2 tasks. |
| IV. PostgreSQL as Single Source of Truth | PASS | New Flyway migration `V2__comments_list_index.sql` adds the composite `(ticket_id, created_at)` index; `ddl-auto=validate` unchanged; the endpoint is paginated (`Pageable`), never returns an unbounded result set. |
| V. Observability, Secrets Hygiene, Simplicity | PASS | Reuses existing structured logging and correlation-id filter; no new dependency, no cache, no new service — a paginated repository query is the simplest thing that satisfies SC-002 at the stated scale. |
| Design Patterns: constructor injection | PASS | `CommentService`/`TicketCommentController` extended, not replaced; constructor injection unchanged. |
| Design Patterns: atomicity / SAGA scope | PASS | Single local `@Transactional(readOnly = true)` read; no multi-service call, so SAGA does not apply. |

No violations. Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-list-comments/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── comments-list-api.yaml
└── checklists/
    └── requirements.md
```

### Source Code (repository root — extends the existing 001 codebase, no new module)

```text
src/main/resources/db/migration/
└── V2__comments_list_index.sql          # NEW

src/main/java/com/frequency/ticketing/
├── web/
│   ├── dto/
│   │   └── CommentPage.java             # NEW
│   └── controller/
│       └── TicketCommentController.java # EXTENDED: + GET /api/v1/tickets/{id}/comments
├── domain/comment/
│   └── CommentService.java              # EXTENDED: + listByTicket(ticketId, pageable)
└── repository/
    └── CommentRepository.java           # EXTENDED: + paginated finder

src/test/java/com/frequency/ticketing/
├── contract/
│   └── CommentListContractTest.java     # NEW
└── integration/
    └── CommentListIntegrationTest.java  # NEW
```

**Structure Decision**: No new project or package — this is an additive slice inside the
existing single microservice from 001-ticket-management-backend, following the same
`web → domain → repository` layering already established there.

## Complexity Tracking

*No entries — no Constitution Check violation requires justification.*
