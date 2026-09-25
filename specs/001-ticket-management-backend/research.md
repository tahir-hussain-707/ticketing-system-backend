# Phase 0 Research: Ticket Management Backend

All Technical Context fields were resolvable from the constitution (fixed stack) and the
clarified spec (functional/performance targets) — no open `NEEDS CLARIFICATION` markers
remained. This document records the design-level decisions still needed to move from those
fixed constraints to a concrete implementation.

## Build tool: Maven

- **Decision**: Maven, with the Maven Wrapper (`mvnw`) committed to the repository.
- **Rationale**: Constitution requires "a single build tool (Maven or Gradle)". Maven is the
  more common default for Spring Boot services on Java LTS and has first-class Spring Boot
  starter/parent support, minimizing setup risk for a greenfield project.
- **Alternatives considered**: Gradle — equally compliant with the constitution and slightly
  faster incremental builds, but rejected here only as a coin-flip default; switching later is
  low-risk since no build-tool-specific code is planned.

## Keyword search implementation

- **Decision**: PostgreSQL `ILIKE` matching against `title` and `description`, backed by a
  `pg_trgm` GIN index on both columns.
- **Rationale**: Meets the ≤2 second target (spec SC-004) at ≥10,000 rows with a simple,
  well-understood mechanism. Satisfies constitution Principle V ("start with the simplest
  design that satisfies the requirement").
- **Alternatives considered**: PostgreSQL full-text search (`tsvector`/`tsquery`) — more
  powerful (stemming, ranking) but adds language-configuration and indexing complexity the spec
  does not ask for (spec only requires keyword/substring matching, FR-006). A dedicated search
  engine (e.g. Elasticsearch) — rejected outright as unjustified complexity at this scale
  (constitution Principle V forbids adding a new service without a named concrete problem it
  solves; none exists here).

## Pagination

- **Decision**: Spring Data `Pageable` on list/search endpoints (`page`, `size` query params;
  default size 20, max 100, enforced in the controller).
- **Rationale**: Constitution Principle IV requires list endpoints to be paginated and to never
  return unbounded result sets; Spring Data's built-in support avoids hand-rolled paging code.
- **Alternatives considered**: Cursor/keyset pagination — better for very high write-concurrency
  feeds, but unnecessary complexity for an internal tool at the stated scale (10k tickets).

## Concurrency conflict handling

- **Decision**: `@Version` optimistic locking on `Ticket`; a global exception handler catches
  `ObjectOptimisticLockingFailureException` and maps it to HTTP `409 Conflict` with error code
  `TICKET_CONFLICT`.
- **Rationale**: Directly mandated by constitution Principle IV. Matches the spec's edge case
  requirement that concurrent edits must not silently produce a mixed, inconsistent result.
- **Alternatives considered**: Pessimistic row locking — rejected; unnecessary contention/latency
  for a low-concurrency internal tool, and not what the constitution specifies.

## State machine implementation

- **Decision**: A plain domain component, `TicketStatusTransitionPolicy`, holding a static
  `Map<TicketStatus, Set<TicketStatus>>` of the five allowed transitions from spec FR-009/FR-010.
  `TicketService` calls it before persisting any status change; it is the only code path that
  may write `Ticket.status`.
- **Rationale**: The full state machine is 5 states and 5 edges — small and fixed. A plain
  lookup table is simplest-possible-thing-that-works (constitution Principle V) and is trivial
  to exhaustively unit-test.
- **Alternatives considered**: A general state-machine library (e.g. Spring Statemachine) —
  rejected as unjustified complexity for a table this small (constitution Principle V); it would
  add a dependency and a learning-curve cost with no matching benefit.

## Error response contract

- **Decision**: A single `ApiError` DTO — `code` (machine-readable, e.g.
  `INVALID_TRANSITION`, `VALIDATION_FAILED`, `TICKET_NOT_FOUND`, `TICKET_CONFLICT`), `message`
  (human-readable), `timestamp`, `path`, and an optional `fieldErrors[]` list of
  `{field, message}` for validation failures — returned by one `@ControllerAdvice` for every
  error path (validation, not-found, illegal transition, conflict).
- **Rationale**: Constitution Principle I mandates one consistent error shape across the service
  with no leaked internals; spec FR-012/SC-005 require every rejection to carry specific,
  descriptive information a UI can render.
- **Alternatives considered**: Spring Boot's default `/error` payload — rejected; inconsistent
  across error types and can leak implementation detail (e.g. exception class names).

## API documentation

- **Decision**: `springdoc-openapi-starter-webmvc-ui`, generating the OpenAPI description
  directly from the annotated controllers/DTOs.
- **Rationale**: Constitution Principle I requires an OpenAPI description kept current with the
  deployed contract; generating it from code is the only way to guarantee it never drifts.
- **Alternatives considered**: Hand-maintained OpenAPI YAML — rejected; drifts from the
  implementation over time with no enforcement mechanism.

## DTO ↔ entity mapping

- **Decision**: Hand-written mapper methods (plain static methods on a small `TicketMapper` /
  `CommentMapper` class), not a mapping framework.
- **Rationale**: Only two entities and a handful of DTOs; constitution Principle V favors the
  simplest design — adding an annotation-processing mapping framework is not justified at this
  surface area.
- **Alternatives considered**: MapStruct — a fine choice at larger DTO surface area, but an
  unjustified build-time dependency here (constitution Principle V requires justifying any new
  dependency by a concrete problem it solves).

## Test data lifecycle (Testcontainers)

- **Decision**: One shared, statically-started PostgreSQL Testcontainer per test JVM
  (singleton-container pattern), with Flyway migrations applied fresh and test data cleaned
  between tests via `@Sql` reset scripts or `@Transactional` test rollback where applicable.
- **Rationale**: Constitution Principle III mandates Testcontainers-backed integration tests;
  a shared container keeps the suite fast while still exercising the real database engine.
- **Alternatives considered**: A fresh container per test class — correctness-equivalent but
  materially slower suite startup with no added confidence.

## Restart-durability proof

- **Decision**: One integration test that writes a ticket (with a comment), closes and rebuilds
  the Spring `ApplicationContext` (simulating an application restart) against the same
  Testcontainers PostgreSQL volume, then re-reads the ticket and asserts full equality.
- **Rationale**: Directly required by constitution Principle III and spec FR-008/SC-003.
- **Alternatives considered**: Asserting durability indirectly via "data exists after a JPA
  session closes" — rejected; does not actually prove restart durability, only session-scoping,
  which is a weaker claim than the requirement.

## Outcome

No unresolved `NEEDS CLARIFICATION` markers remain in the Technical Context. Ready for Phase 1.
