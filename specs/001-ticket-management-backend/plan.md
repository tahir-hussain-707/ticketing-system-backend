# Implementation Plan: Ticket Management Backend

**Branch**: `001-ticket-management-backend` | **Date**: 2026-09-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-ticket-management-backend/spec.md`

## Summary

Deliver the backend for a Support Ticket Management System: create/list/view/update tickets,
add comments, search by keyword, filter by status, and enforce the ticket status lifecycle
(`OPEN → IN_PROGRESS → RESOLVED → CLOSED`, plus `OPEN/IN_PROGRESS → CANCELLED`) as the sole
source of truth for validity. Technical approach: a single Spring Boot 3.x microservice on
Java 25, backed by PostgreSQL via Flyway-versioned schema and Spring Data JPA, with a
dedicated domain-layer transition policy enforcing the state machine independently of any
client input, and a consistent JSON error contract so a UI (out of this feature's scope) can
render meaningful errors.

## Technical Context

**Language/Version**: Java 25 (LTS), no preview features (constitution: Technology Stack)

**Primary Dependencies**: Spring Boot 3.x — Spring Web, Spring Data JPA, Spring Validation,
Spring Boot Actuator; Flyway (migrations); springdoc-openapi (OpenAPI generation, constitution
Principle I)

**Storage**: PostgreSQL 16+, HikariCP connection pooling

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL module), AssertJ

**Target Platform**: Linux server (containerized), single deployable microservice

**Project Type**: web-service (backend only — no frontend in this feature's scope)

**Performance Goals**: Keyword search and status filtering return correct results for at least
10,000 tickets in under 2 seconds (spec SC-004). No high-throughput requirement stated; this is
a low-volume internal support tool — correctness of the state machine and data integrity take
priority over raw throughput.

**Constraints**: Schema changes only via Flyway migrations, `ddl-auto=validate` (constitution
Principle IV); ticket status persisted as string, not ordinal; optimistic locking (`@Version`)
on Ticket with `409 Conflict` on lost update; list endpoints paginated, no unbounded result
sets; JPA entities never serialized directly (DTOs only, constitution Principle I); one
consistent JSON error body service-wide; constructor-only dependency injection; every write
operation atomic within a single local `@Transactional` boundary — SAGA is explicitly out of
scope for this feature since it makes no calls to other services or external systems
(constitution Design Patterns).

**Scale/Scope**: 3 prioritized user stories (ticket lifecycle/CRUD + state machine; comments;
keyword search + status filter), 2 persisted entities (Ticket, Comment), target volume ≥10,000
tickets.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | How this plan satisfies it |
|---|---|---|
| I. Contract-First API Design | PASS | Versioned `/api/v1` REST contract defined in Phase 1 (`contracts/tickets-api.yaml`) before any controller code; DTOs distinct from entities; single `ApiError` shape; OpenAPI generated via springdoc from the same annotated controllers. |
| II. Domain Invariants Enforced in Backend (NON-NEGOTIABLE) | PASS | A single `TicketStatusTransitionPolicy` domain component owns the allowed-transition table; all status changes route through it; illegal transitions return `409` with a machine-readable code; Bean Validation at the DTO boundary plus domain-layer checks for rules validation can't express. |
| III. Test-First, State Machine Covered (NON-NEGOTIABLE) | PASS | tasks.md (Phase 2, not this command) will sequence failing tests before implementation; every legal and illegal transition pair gets an integration test against a real Testcontainers PostgreSQL instance; a restart-durability test is planned. |
| IV. PostgreSQL as Single Source of Truth | PASS | Flyway migration `V1__init_schema.sql` creates `tickets`/`comments` with `NOT NULL`, FK, and search indexes; `ddl-auto=validate`; status stored as `VARCHAR`; `@Version` optimistic locking; paginated list/search endpoints. |
| V. Observability, Secrets Hygiene, Simplicity | PASS | Structured JSON logging with a correlation id filter; every transition logged (ticket id, from/to status, actor field if supplied); Actuator health/readiness wired to DB connectivity; no secrets in source (DB credentials via environment/Spring profile placeholders); no cache, broker, or extra service introduced — none is needed at this scope. |
| Design Patterns: constructor injection | PASS | All Spring components use constructor injection exclusively; no `@Autowired` fields. |
| Design Patterns: atomicity / SAGA scope | PASS | Every write is a single local `@Transactional` operation against one PostgreSQL database. This feature has no multi-service or external-system call, so SAGA is not applicable and is not used. |
| Authentication/Authorization | N/A | Constitution v1.1.0 carries no auth/authz requirement (removed by amendment); this feature accordingly defines no auth layer, matching spec Assumptions. |

No violations. Complexity Tracking table below is empty — no deviation to justify.

## Project Structure

### Documentation (this feature)

```text
specs/001-ticket-management-backend/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── tickets-api.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
pom.xml
mvnw / mvnw.cmd / .mvn/wrapper/

src/main/java/com/frequency/ticketing/
├── TicketingApplication.java
├── web/
│   ├── controller/          # TicketController, TicketCommentController
│   ├── dto/                 # TicketRequest, TicketResponse, TicketUpdateRequest,
│   │                         # TicketTransitionRequest, CommentRequest, CommentResponse,
│   │                         # ApiError, ApiFieldError
│   └── exception/           # GlobalExceptionHandler (@ControllerAdvice), domain exception ->
│                             # HTTP status mapping
├── domain/
│   ├── ticket/               # Ticket (JPA entity), TicketStatus (enum), TicketPriority (enum),
│   │                         # TicketService, TicketStatusTransitionPolicy
│   ├── comment/               # Comment (JPA entity), CommentService
│   └── exception/             # TicketNotFoundException, InvalidTransitionException,
│                               # TicketConflictException
├── repository/                # TicketRepository, CommentRepository (Spring Data JPA)
└── config/                    # OpenApiConfig, JacksonConfig, loggingConfig (correlation id filter)

src/main/resources/
├── application.yml
├── application-test.yml
└── db/migration/
    └── V1__init_schema.sql

src/test/java/com/frequency/ticketing/
├── contract/                  # Controller-level contract tests per endpoint (request/response shape)
├── integration/               # Full-stack tests incl. state-machine transition matrix,
│                               # search/filter, restart-durability (Testcontainers)
└── unit/                      # TicketStatusTransitionPolicy, validation, mapper unit tests
```

**Structure Decision**: Single project (backend microservice only — no frontend in this
feature's scope, matching the spec's "backend scoped requirements" framing). Layered packages
follow the constitution's mandated dependency direction `web → domain → repository`; `domain`
has no dependency on `web`. Base package `com.frequency.ticketing`; build tool Maven (with
committed wrapper), chosen over Gradle as the more common enterprise default — either satisfies
the constitution's "single build tool" requirement, so this is a reversible, low-risk pick.

## Complexity Tracking

*No entries — no Constitution Check violation requires justification.*
