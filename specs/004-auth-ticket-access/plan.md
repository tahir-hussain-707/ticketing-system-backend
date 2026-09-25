# Implementation Plan: Authentication, Auto-Assignment & Scoped Ticket Access

**Branch**: `004-auth-ticket-access` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**⚠️ SUPERSEDED**: This plan was written against 004 in isolation, including a security matcher
for a chatbot endpoint (`/api/v1/chatbot/**`) that was never built — 003 had no plan/tasks of its
own. 003 and 004 are merged into
[`specs/005-auth-rag-chatbot/spec.md`](../005-auth-rag-chatbot/spec.md); run `/speckit-plan`
fresh against that spec instead of using this file.

**Input**: Feature specification from `/specs/004-auth-ticket-access/spec.md`

## Summary

Add email/password login (with `SUPPORT`/`GENERAL`/`ADMIN` roles) as a new authentication layer
in front of the existing ticketing API, then use the now-known caller identity to: gate ticket
creation and the (not-yet-built) chatbot; auto-assign each new ticket to whichever `SUPPORT` user
currently has the fewest `OPEN`/`IN_PROGRESS` tickets, with `ADMIN`-only manual reassignment as an
escape hatch; restrict commenting to a ticket's creator or assignee and record the commenter's
name; and let ticket listing be scoped to "created by me," "assigned to me," or "all I'm allowed
to see." Session-based authentication via Spring Security (already the constitution-fixed
framework family) is used rather than introducing a token library, since no dependency beyond
`spring-boot-starter-security` is needed to satisfy the spec. This feature amends 001 (assignee
becomes a `User` reference, not free text) and 002/003's assumptions (comments now require an
authenticated author; the chatbot now requires login) as recorded in spec.md's Assumptions.

## Technical Context

**Language/Version**: Java 25 (LTS) — unchanged, constitution-fixed.

**Primary Dependencies**: Spring Boot 3.5.16 (Spring Web, Spring Data JPA, Spring Validation,
Actuator) — already present. Adds `spring-boot-starter-security` (session-based authentication,
`BCryptPasswordEncoder` for hashing — both included, no further library needed) and
`spring-security-test` (test scope, for `@WithMockUser`-style integration tests). Flyway and
springdoc-openapi — already present, no version change.

**Storage**: PostgreSQL 16+. Three additive Flyway migrations: a new `users` table; `tickets`
gains `created_by_id`/`assignee_id` UUID foreign keys to `users` and drops the old free-text
`assignee` column; `comments` gains an `author_id` UUID foreign key to `users`. No new database
technology.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL), AssertJ, `spring-security-test`
— same suite as 001/002, extended with authentication/authorization integration tests.

**Target Platform**: Same single Spring Boot microservice; no new deployable, no new service.

**Project Type**: Backend extension to an existing web-service (not a new project). No new
frontend work is in scope — the existing separate UI consumer will need to start sending
credentials and session cookies, but that client is out of this repository.

**Performance Goals**: No new performance target beyond what 001/002 already committed to (2s
search, 2s comment-list); the new auto-assignment lookup is a single indexed aggregate query per
ticket creation, not a new class of workload.

**Constraints**: Reuses constitution-mandated patterns from 001/002: contract-first (OpenAPI
before controller code), constructor injection only, single local `@Transactional` per unit of
work (no SAGA — everything here stays inside this service's own PostgreSQL transaction),
paginated list endpoints, one consistent `ApiError` shape, structured logging with correlation
IDs. New constraint this feature introduces: passwords are never stored, logged, or returned in
plain text (spec SC-005) — enforced via `BCryptPasswordEncoder` and by never adding a password
getter to any response DTO.

**Scale/Scope**: One new entity (`User`), one new controller (`AuthController`), one new security
configuration class, three additive migrations, and modifications to the existing ticket/comment
create/update/list code paths and DTOs. User account creation itself is out of scope (spec
FR-005) — accounts are assumed to already exist as seed data.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | How this plan satisfies it |
|---|---|---|
| I. Contract-First API Design | PASS | New `contracts/auth-api.yaml` (login/logout) and `contracts/ticket-ownership-api.yaml` (modified ticket/comment schemas, new reassignment endpoint, new `scope` query param) written before controller code. All endpoints stay under `/api/v1/...`. DTOs remain dedicated types; no entity is ever serialized directly (the `User` entity's password field in particular is never referenced by any response DTO). |
| II. Domain Invariants Enforced in the Backend | PASS | Comment authorization (creator-or-assignee) and reassignment authorization (`ADMIN`-only) are data-dependent rules validation annotations cannot express, so they are enforced in `CommentService`/`TicketService`, not the controller or client. The ticket status state machine itself is untouched by this feature. |
| III. Test-First, State Machine Covered | PASS | Contract and integration tests for login (success/failure/missing-field), logout, gated ticket creation and comment creation, auto-assignment (including the tie-break rule and the no-`SUPPORT`-user case), `ADMIN`-only reassignment, and each listing scope are written before their implementations, per Phase 2 tasks. |
| IV. PostgreSQL as the Single Source of Truth | PASS | Three additive, forward-only Flyway migrations (`V3`–`V5`); `ddl-auto` stays `validate`; new foreign keys back every new relationship (`created_by_id`, `assignee_id`, `author_id`); new indexes back the auto-assignment workload query and the ownership-scoped list query; ticket status remains a string column, untouched. |
| V. Observability, Secrets Hygiene, Simplicity | PASS | Every login attempt (success and failure, without echoing the password) and every auto-assignment decision is logged with the correlation ID already in place. No password is ever logged, returned, or stored in plain text (`BCryptPasswordEncoder` only). No new service, cache, or message broker is introduced; session-based auth reuses the framework's built-in `HttpSession` support rather than adding a JWT library or a distributed session store, since nothing in the spec requires horizontal statelessness. |
| Design Patterns: constructor injection | PASS | `AuthController`, a new `UserService`/`AuthenticationSuccessHandler` wiring, and the extended `TicketService`/`CommentService` all take dependencies via constructor. |
| Design Patterns: atomicity / SAGA scope | PASS | Ticket creation (including the auto-assignment lookup) stays one local `@Transactional` unit of work in this service's own database — no cross-service call, so SAGA does not apply. |

No violations. Re-checked after Phase 1 design (data-model.md, contracts/) — the new `users`
table, its foreign keys/indexes, and the two new endpoints do not change any row above.
Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/004-auth-ticket-access/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── auth-api.yaml
│   └── ticket-ownership-api.yaml
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
│   └── SecurityConfig.java            # NEW: filter chain, permitAll/authenticated matchers, BCrypt bean
├── domain/
│   ├── user/
│   │   ├── User.java                  # NEW entity
│   │   ├── UserRole.java              # NEW enum: SUPPORT, GENERAL, ADMIN
│   │   ├── UserService.java           # NEW: lookup, auto-assignment candidate selection
│   │   └── CurrentUser.java           # NEW: resolves the authenticated User from SecurityContext
│   ├── auth/
│   │   └── AuthenticationService.java # NEW: login (authenticate + establish session), logout
│   ├── ticket/
│   │   ├── Ticket.java                # updated: assignee/createdBy become User references
│   │   ├── TicketService.java         # updated: auto-assignment on create, ADMIN-only reassign,
│   │   │                              #   ownership-scoped listing
│   │   └── ...                        # TicketStatus, TicketPriority, TicketStatusTransitionPolicy unchanged
│   ├── comment/
│   │   ├── Comment.java               # updated: adds authorId
│   │   └── CommentService.java        # updated: creator-or-assignee authorization check
│   └── exception/
│       ├── InvalidCredentialsException.java  # NEW
│       └── ForbiddenActionException.java     # NEW
├── repository/
│   ├── UserRepository.java            # NEW: findByEmailIgnoreCase, least-loaded SUPPORT query
│   ├── TicketRepository.java          # updated: scoped-listing queries (created-by / assigned / all)
│   └── CommentRepository.java         # unchanged
└── web/
    ├── controller/
    │   ├── AuthController.java        # NEW: POST /api/v1/auth/login, POST /api/v1/auth/logout
    │   ├── TicketController.java      # updated: scope param, new PATCH .../assignee endpoint
    │   └── TicketCommentController.java # unchanged route, updated response shape
    ├── dto/
    │   ├── LoginRequest.java, LoginResponse.java, UserSummary.java   # NEW
    │   ├── ReassignRequest.java                                     # NEW
    │   ├── TicketCreateRequest.java   # updated: assignee field removed
    │   ├── TicketUpdateRequest.java   # updated: assignee field removed
    │   ├── TicketResponse.java, TicketDetailResponse.java           # updated: assignee/createdBy as UserSummary
    │   ├── CommentResponse.java       # updated: adds authorName
    │   └── CommentCreateRequest.java  # unchanged
    └── exception/GlobalExceptionHandler.java  # updated: 401/403 mappings for the new exceptions

src/main/resources/db/migration/
├── V3__create_users_table.sql
├── V4__ticket_ownership_and_assignment.sql
└── V5__comment_author.sql

src/test/java/com/frequency/ticketing/  # contract + integration tests mirroring the above
```

**Structure Decision**: Extends the existing single Spring Boot microservice's layered
`web → domain → repository` structure (constitution-fixed) with one new `domain.user` package and
one new `domain.auth` package, following the same layering as `domain.ticket`/`domain.comment`.
No new module, service, or deployable.

## Complexity Tracking

*No entries — Constitution Check has no violations to justify.*
