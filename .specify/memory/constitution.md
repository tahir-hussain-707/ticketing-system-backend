<!--
SYNC IMPACT REPORT (temporary scratch material — remove before committing the amended file)

Version change: 1.0.0 → 1.1.0
Bump rationale: MINOR. This amendment folds in a "Design Patterns" subsection under Technology
Stack & Constraints (constructor DI, local-transaction atomicity, scoped SAGA usage) that had
been added to the working copy without a governance version bump, and simultaneously — per
explicit user direction in this amendment — removes the OAuth2 authentication/authorization
rule that subsection had also carried, and narrows the SAGA rule so it applies only to
genuinely multi-service/distributed operations rather than any I/O. Because none of this content
had previously shipped in a ratified version, and no plan/design artifact yet depends on the
removed auth rule, this is treated as net new/refined guidance (MINOR) rather than a breaking
removal (MAJOR).

Modified sections:
- Technology Stack & Constraints → added "Design Patterns" subsection (constructor injection;
  atomicity scoped to local `@Transactional`, SAGA restricted to cross-service operations)
- Removed: authentication/authorization (OAuth2 + per-endpoint roles) rule — descoped by
  explicit user instruction; no auth/authz requirement remains in this constitution

Added sections: none beyond the Design Patterns subsection above
Removed sections: none (Design Patterns subsection retained, only its auth bullet removed)

Follow-up TODOs: none — no placeholders deferred. If authentication/authorization is needed
later, it MUST be reintroduced via a fresh constitution amendment before being assumed by any
plan.
-->

# Ticketing System Constitution

## Core Principles

### I. Contract-First API Design

Every externally reachable capability MUST be exposed as a versioned REST endpoint whose
contract is defined before the implementation. Rules:

- The HTTP contract (path, method, request/response schema, status codes, error shape) MUST be
  written and reviewed before controller or service code is written.
- All endpoints MUST live under a version prefix (`/api/v1/...`). Breaking changes to an existing
  version are FORBIDDEN; introduce a new version instead.
- Request and response bodies MUST use dedicated DTO types. JPA entities MUST NOT be serialized
  directly to or bound directly from HTTP.
- Errors MUST use a single consistent JSON error body across the whole service, carrying at
  minimum a machine-readable code, a human-readable message, and per-field validation details
  when applicable. Stack traces and internal identifiers MUST NOT be returned to clients.
- An OpenAPI description MUST be generated and kept current with the deployed contract.

Rationale: The UI is a separate consumer that must render meaningful errors. A stable, explicit
contract is the only way clients can depend on the backend without reading its source.

### II. Domain Invariants Enforced in the Backend (NON-NEGOTIABLE)

The backend is the sole authority for validity. Rules:

- The ticket lifecycle MUST be enforced server-side as an explicit state machine permitting
  exactly: `OPEN → IN_PROGRESS`, `IN_PROGRESS → RESOLVED`, `RESOLVED → CLOSED`,
  `OPEN → CANCELLED`, `IN_PROGRESS → CANCELLED`. Every other transition MUST be rejected with a
  `409 Conflict` and a machine-readable code naming the attempted transition.
- Transition legality MUST be decided by one domain component. Controllers, schedulers, and
  event handlers MUST call that component; they MUST NOT re-implement or bypass transition
  checks.
- Every request payload MUST be validated with Jakarta Bean Validation at the boundary, and
  business rules that validation annotations cannot express MUST be enforced in the domain layer.
  Rejections MUST return `400 Bad Request` with per-field details.
- Client-supplied state MUST NOT be trusted. UI-side validation is a convenience only and never
  substitutes for backend checks.
- Status changes MUST be persisted atomically with their side effects in a single transaction.

Rationale: The state machine is the product's core correctness requirement. Enforcing it in one
server-side place makes invalid states unreachable regardless of which client calls the API.

### III. Test-First, State Machine Covered (NON-NEGOTIABLE)

Tests are written before the code they verify. Rules:

- For every new behavior, a failing test MUST exist before the implementing code is written, and
  the Red-Green-Refactor cycle MUST be followed.
- Every legal transition MUST have a passing integration test, and every illegal transition
  (including `CLOSED → OPEN`, `RESOLVED → OPEN`, `CANCELLED → OPEN`) MUST have an integration
  test asserting rejection and the exact error response.
- Integration tests MUST run against a real PostgreSQL instance via Testcontainers. In-memory
  substitutes such as H2 MUST NOT be used for persistence tests.
- Persistence MUST be proven durable by a test that writes data, restarts the application
  context, and reads the data back.
- The full test suite MUST pass before merge. Disabling, ignoring, or deleting a failing test to
  achieve a green build is FORBIDDEN unless the behavior it covers was intentionally removed.

Rationale: State machine violations are silent data corruption. Only executable tests against
the real database engine prove the rules hold.

### IV. PostgreSQL as the Single Source of Truth

All durable state lives in PostgreSQL, with the schema under version control. Rules:

- Schema changes MUST be applied exclusively by versioned Flyway migrations checked into the
  repository. Hibernate `ddl-auto` MUST be `validate` or `none` in every environment.
- Migrations MUST be forward-only and immutable once merged. A merged migration MUST NOT be
  edited; correct it with a new migration.
- Database constraints MUST back the domain model: `NOT NULL` on required columns, foreign keys
  on all relationships, and indexes on every column used for search or filtering.
- Ticket status MUST be persisted as a stable string value, never as an enum ordinal.
- Concurrent updates to a ticket MUST be guarded by optimistic locking (`@Version`), returning
  `409 Conflict` on a lost update.
- Repository access MUST be free of N+1 query patterns; list endpoints MUST be paginated and
  MUST NOT return unbounded result sets.

Rationale: The database outlives every deployment. Versioned migrations and real constraints
make the durability requirement verifiable rather than assumed.

### V. Observability, Secrets Hygiene, and Simplicity

The service MUST be debuggable in production and MUST leak nothing. Rules:

- Logging MUST be structured (JSON) and MUST carry a correlation identifier propagated from the
  inbound request across all log lines for that request.
- Every ticket state transition MUST be logged with ticket identifier, previous status, new
  status, and actor.
- Liveness and readiness endpoints MUST be exposed via Spring Boot Actuator; readiness MUST
  reflect actual database connectivity.
- Secrets — database credentials, tokens, keys — MUST come from environment variables or a
  secrets manager. Committing a secret in any form, including test fixtures and example config,
  is FORBIDDEN.
- Logs and error responses MUST NOT contain credentials, tokens, or full request bodies that may
  carry sensitive content.
- Start with the simplest design that satisfies the requirement. Caches, message brokers,
  additional services, and abstraction layers MUST NOT be introduced without a written
  justification in the feature plan naming the concrete problem being solved.

Rationale: Operational visibility and secret containment are cheap when built in and expensive
to retrofit. Unjustified complexity is the main long-term cost in a small service.

## Technology Stack & Constraints

The following are fixed for this project and MUST NOT be changed without a constitution
amendment:

- **Language**: Java 25 (LTS). The build MUST target release 25. Preview features MUST NOT be
  enabled in production code.
- **Framework**: Spring Boot 3.x with Spring Web, Spring Data JPA, Spring Validation, and Spring
  Boot Actuator.
- **Database**: PostgreSQL 16 or later, accessed through the official JDBC driver and HikariCP
  connection pooling.
- **Migrations**: Flyway.
- **Build**: A single build tool (Maven or Gradle), with a committed wrapper so builds are
  reproducible without a local toolchain install.
- **Testing**: JUnit 5, Spring Boot Test, Testcontainers for PostgreSQL, and AssertJ or Hamcrest
  for assertions.
- **Architecture**: A single deployable microservice with layered packages — `web` (controllers,
  DTOs, exception handling), `domain` (entities, state machine, business rules), `repository`
  (Spring Data interfaces), `config`. Dependencies MUST point inward: `web → domain → repository`.
  `domain` MUST NOT depend on `web`.
- **Configuration**: Externalized per environment through Spring profiles. No environment-specific
  values hardcoded in source.
- **Time**: All timestamps MUST be stored and exchanged in UTC using `Instant` or
  `timestamptz`.
  
## Design Patterns

- **Dependency injection**: All dependency injection MUST be done via constructor injection.
  Field injection via `@Autowired` MUST NOT be used.
- **Atomicity**: Every API MUST be atomic within its own service, using local transaction
  management (`@Transactional` wrapping a single database unit of work). The SAGA pattern
  (orchestration with explicit compensating actions) MUST be used ONLY when a single business
  operation spans multiple independent services or external systems that cannot share one local
  database transaction. SAGA MUST NOT be applied to an operation that stays within this
  service's own PostgreSQL transaction — a local `@Transactional` boundary is sufficient and
  required for those.

Language-level modernization is encouraged where it improves clarity: records for DTOs, sealed
types and pattern matching for domain modeling, and virtual threads where they measurably help.

## Development Workflow & Quality Gates

- **Branching**: Work happens on feature branches. Direct pushes to `main` are FORBIDDEN.
- **Review**: Every change MUST be reviewed before merge. The reviewer MUST explicitly confirm
  that the change complies with this constitution, and MUST reject any change that weakens the
  state machine, bypasses backend validation, or introduces an unjustified dependency.
- **Automated gates** — all of the following MUST pass before merge:
  1. Compilation on Java 25 with no warnings introduced by the change.
  2. Full unit and integration test suite green, Testcontainers-backed tests included.
  3. Flyway migrations apply cleanly against an empty database and against the current schema.
  4. Static analysis and formatting checks clean.
  5. Dependency vulnerability scan with no new high or critical findings.
  6. Secret scan clean across the diff.
- **Definition of done**: A feature is done only when its contract is documented, its tests pass,
  its migrations are committed, its errors render meaningfully to the client, and its state
  transitions are covered by integration tests for both the legal and the illegal cases.
- **Complexity review**: Any new module, dependency, or abstraction MUST be justified in the
  feature plan. Unjustified complexity is grounds for rejecting a change on its own.

## Governance

This constitution supersedes all other development practices, conventions, and habits in this
repository. Where a style guide, tool default, or prior code pattern conflicts with it, this
document wins.

**Amendment procedure**: Amendments MUST be proposed as a pull request that modifies this file,
states the motivation, and describes the migration path for any existing code rendered
non-compliant. An amendment is adopted when the pull request is approved and merged. Adopted
amendments take effect immediately for all new work.

**Versioning policy**: This constitution is versioned with semantic versioning.

- **MAJOR**: A principle is removed or redefined in a backward-incompatible way, or governance is
  restructured such that previously compliant work becomes non-compliant.
- **MINOR**: A new principle or section is added, or existing guidance is materially expanded.
- **PATCH**: Clarifications, wording, and typo fixes that do not change what is required.

**Compliance review**: Compliance is verified at every code review and enforced by the automated
gates above. Non-compliant code MUST NOT be merged. A violation discovered after merge MUST be
recorded as a defect and remediated, not normalized. Any accepted deviation MUST be documented in
the relevant feature plan with its justification and, where the deviation is temporary, the
condition for removing it.

**Runtime guidance**: Day-to-day agent and developer guidance lives in `CLAUDE.md` and the
feature artifacts under `.specify/`. Those documents MUST remain consistent with this
constitution; where they diverge, this constitution governs and they MUST be corrected.

**Version**: 1.1.0 | **Ratified**: 2026-09-20 | **Last Amended**: 2026-09-21
