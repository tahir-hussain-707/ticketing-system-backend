# Bug Fix: `GET /api/v1/tickets` returns 500 for an invalid `scope` value

- **Slug**: ticket-scope-invalid-value-500
- **Fixed**: 2026-09-25
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

`TicketController.list` parsed the `scope` query param manually with
`TicketScope.valueOf(scope.toUpperCase())`, so an unrecognized value threw an unhandled
`IllegalArgumentException` and surfaced as a bare `500`. Changed `scope` to bind directly as a
`TicketScope` enum (matching how `status` already binds), and added a `GlobalExceptionHandler`
mapping for Spring's own enum-binding failure (`MethodArgumentTypeMismatchException`) so any
invalid `scope` or `status` value now returns `400` with the standard `ApiError` body instead.

The report's `[NEEDS CLARIFICATION]` item is resolved: the user confirmed the actual request used
`scope=created` and the `scoped=` in the pasted URL was a transcription typo in the report itself,
not a second bug. No further investigation needed there.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `src/main/java/com/frequency/ticketing/web/controller/TicketController.java` | modified | `scope` now binds as `TicketScope` directly (`@RequestParam(required = false)`, no `defaultValue`); manual `TicketScope.valueOf(...)` call removed, replaced with a null check defaulting to `TicketScope.ALL` |
| `src/main/java/com/frequency/ticketing/web/exception/GlobalExceptionHandler.java` | modified | added `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` → `400` with `ApiError.Code.VALIDATION_FAILED`, naming the invalid parameter and value |
| `src/test/java/com/frequency/ticketing/contract/TicketSearchFilterContractTest.java` | modified | added two tests: invalid `scope` value and invalid `status` value both now assert `400` + `ApiError`, not `500` |

## Diff Highlights

`TicketController.java`:

```java
// before
@RequestParam(required = false, defaultValue = "all") String scope,
...
TicketScope effectiveScope = TicketScope.valueOf(scope.toUpperCase());

// after
@RequestParam(required = false) TicketScope scope,
...
TicketScope effectiveScope = scope != null ? scope : TicketScope.ALL;
```

`GlobalExceptionHandler.java`:

```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ApiError> handleTypeMismatch(
    MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
  return ResponseEntity.badRequest()
      .body(
          ApiError.of(
              ApiError.Code.VALIDATION_FAILED,
              "Parameter '" + ex.getName() + "' has an invalid value: " + ex.getValue(),
              request.getRequestURI()));
}
```

## Tests Added or Updated

- `TicketSearchFilterContractTest::invalidScopeValueReturns400WithApiErrorNot500` — `GET
  /tickets?scope=created` now asserts `400 Bad Request` with `ApiError.code ==
  VALIDATION_FAILED`, pinning down the exact scenario from the bug report.
- `TicketSearchFilterContractTest::invalidStatusValueReturns400WithApiErrorNot500` — same
  assertion for `status=bogus`, since `status` was already bound directly as `TicketStatus` and
  had the same unhandled-enum-mismatch gap before this fix (per assessment's "Tests to add or
  update" note).

## Local Verification

- Commands run: `./mvnw -q compile test-compile` → **success**, no compilation errors.
- Commands run: `./mvnw -q -Dtest=TicketSearchFilterContractTest test` → **could not execute**:
  all 5 tests in this class (the 3 pre-existing plus the 2 new ones) fail identically at class
  init with `IllegalStateException: Could not find a valid Docker environment` — this project's
  `AbstractIntegrationTest` starts a Testcontainers-managed Postgres, and no Docker daemon is
  available in this sandbox. This is a pre-existing environment limitation unrelated to this
  change (it blocks the whole test class, including the 3 tests that already passed before this
  fix), not a regression it introduced.
- Manual checks: traced the code path by hand — `TicketScope` has no `UNKNOWN`/`null`-tolerant
  member, so `scope` bound as `TicketScope` will make Spring throw
  `MethodArgumentTypeMismatchException` (not `IllegalArgumentException`) for any value outside
  `MINE`/`ASSIGNED`/`ALL`, which is now caught by the new handler and mapped to `400`. Confirmed
  `status` (already a `TicketStatus` parameter) hits the same new handler for the same reason.

## Deviations from Assessment

None. Implemented the assessment's preferred remediation exactly as proposed.

## Follow-ups

- Run `TicketSearchFilterContractTest` (and the rest of the contract suite) in an environment with
  Docker available to confirm the two new tests pass and nothing else regressed — this could not
  be verified in this sandbox.
