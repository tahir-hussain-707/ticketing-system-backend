# Bug Assessment: `GET /api/v1/tickets` returns 500 for an invalid `scope` value

- **Slug**: ticket-scope-invalid-value-500
- **Created**: 2026-09-25
- **Source**: pasted text (application error log, JSON/logstash format)
- **Verdict**: valid
- **Severity**: high

## Report (verbatim or summarized)

User reports a 500 Internal Server Error hitting `/api/v1/tickets?scoped=created&page=0`, with
this log line:

```
message: "Servlet.service() for servlet [dispatcherServlet] in context with path [] threw
exception [Request processing failed: java.lang.IllegalArgumentException: No enum constant
com.frequency.ticketing.domain.ticket.TicketScope.CREATED] with root cause"
stack_trace (top frames):
java.lang.IllegalArgumentException: No enum constant com.frequency.ticketing.domain.ticket.TicketScope.CREATED
	at java.base/java.lang.Enum.valueOf(Enum.java:293)
	at com.frequency.ticketing.domain.ticket.TicketScope.valueOf(TicketScope.java:8)
	at com.frequency.ticketing.web.controller.TicketController.list(TicketController.java:115)
	...
```

No URL was supplied in this report — nothing to apply the URL Trust Policy to.

## Symptom

Calling the ticket list endpoint with a `scope` value that isn't one of `TicketScope`'s three
members (`MINE`, `ASSIGNED`, `ALL`) causes an uncaught `IllegalArgumentException` from
`TicketScope.valueOf(...)`, which `GlobalExceptionHandler` has no handler for — so Spring's
default error handling returns a generic `500 Internal Server Error` instead of the `400 Bad
Request` with a structured `ApiError` body that every other rejection path in this service
produces.

## Reproduction

1. Authenticate as any user (login is required for this endpoint — spec 005 FR-006).
2. Call `GET /api/v1/tickets?scope=created&page=0` (any value not in `{mine, assigned, all}`,
   case-insensitive, works — `created` is simply the one in the log).
3. Observe `500 Internal Server Error` instead of a `400` with a field-level validation error.

[NEEDS CLARIFICATION: the user's literal request URL in the report uses the query param name
`scoped` (`?scoped=created&page=0`), but `TicketController.list` binds to a param literally named
`scope` (`TicketController.java:109`) with `defaultValue = "all"`. Spring silently ignores unknown
query params, so a request with `scoped=` (not `scope=`) would fall through to the default value
`"all"` and would **not** throw — `TicketScope.valueOf("ALL")` succeeds. For the stack trace's
`No enum constant ... CREATED` to occur, the actual request sent to the server must have used
`scope=created`, not `scoped=created`. This is most likely a transcription typo in the bug report
itself (or a client-side param name mismatch not reflected in the pasted URL) rather than evidence
of a second bug. The root cause and fix below stand either way — an unrecognized `scope` value
still isn't handled — but the exact client request is worth confirming.]

## Suspected Code Paths

- `src/main/java/com/frequency/ticketing/web/controller/TicketController.java:109-116` — binds
  `scope` as a raw `String` (not the `TicketScope` enum directly) and calls
  `TicketScope.valueOf(scope.toUpperCase())` unguarded inside the handler body, so an invalid
  value throws from application code rather than from Spring's request-binding layer.
- `src/main/java/com/frequency/ticketing/domain/ticket/TicketScope.java:8-12` — the enum only
  has `MINE`, `ASSIGNED`, `ALL`; any other string (including the log's `CREATED`) is guaranteed to
  throw via `Enum.valueOf`.
- `src/main/java/com/frequency/ticketing/web/exception/GlobalExceptionHandler.java` — has
  `@ExceptionHandler` entries for `MethodArgumentNotValidException`,
  `HttpMessageNotReadableException`, and several domain-specific exceptions, but none for
  `IllegalArgumentException` (or a catch-all), so this exception type falls through to the
  container's default error page.

## Root Cause Hypothesis

`TicketController.list` parses the `scope` query parameter with a manual
`TicketScope.valueOf(scope.toUpperCase())` call instead of letting Spring bind it directly as a
`TicketScope` parameter (as `status` already does two lines above it, at
`TicketController.java:107`) or validating it explicitly. Any caller-supplied value outside
`{MINE, ASSIGNED, ALL}` throws an unhandled `IllegalArgumentException`, which
`GlobalExceptionHandler` doesn't map, so it surfaces as a bare `500` instead of the `400
Bad Request` with structured `ApiError` that constitution Principle I requires for all rejections
("Errors MUST use a single consistent JSON error body across the whole service ... Rejections
MUST return `400 Bad Request` with per-field details" — Principle II). Confidence: high — the
stack trace pinpoints the exact line and enum, and the code matches exactly what the trace shows.

## Proposed Remediation

**Preferred**: Bind `scope` directly as `TicketScope scope` (like `status` is already bound as
`TicketStatus status` on the line above) with `@RequestParam(required = false)` and default it to
`TicketScope.ALL` after binding, removing the manual `valueOf` call entirely. Spring's own
enum-conversion failure for an invalid enum query param raises
`MethodArgumentTypeMismatchException`, which needs a new `@ExceptionHandler` in
`GlobalExceptionHandler` (there is currently no handler for it either) mapping it to `400 Bad
Request` with `ApiError.Code.VALIDATION_FAILED` and a message naming the invalid parameter/value,
consistent with the existing `handleValidation` shape.

**Alternatives**:
- Keep `scope` as a raw `String` and wrap the existing `TicketScope.valueOf(...)` call in a
  try/catch inside `list()`, throwing a dedicated exception (or building the `ApiError` inline)
  on failure. Trade-off: keeps the fix localized to one file but leaves the "manual enum parsing
  in a controller" pattern in place for the next person to copy.
- Add a single catch-all `@ExceptionHandler(IllegalArgumentException.class)` in
  `GlobalExceptionHandler` mapping to `400`. Trade-off: simplest change, but `IllegalArgumentException`
  is a very broad type — a future unrelated bug that happens to throw it would silently become a
  `400` instead of surfacing as the `500` that would correctly flag it as a server bug.

**Files likely to change**:
- `src/main/java/com/frequency/ticketing/web/controller/TicketController.java` (bind `scope` as
  `TicketScope` directly)
- `src/main/java/com/frequency/ticketing/web/exception/GlobalExceptionHandler.java` (add handler
  for `MethodArgumentTypeMismatchException`, or whatever exception type the chosen fix produces)
- Corresponding contract test file, e.g.
  `src/test/java/com/frequency/ticketing/contract/TicketSearchFilterContractTest.java` (already
  covers `scope`/`status` filtering per its name)

**Tests to add or update**:
- Contract test: `GET /api/v1/tickets?scope=bogus-value` returns `400` with an `ApiError` body
  (code `VALIDATION_FAILED`), not `500`.
- Same case for an invalid `status` value, if not already covered — `status` is bound directly as
  `TicketStatus` today (`TicketController.java:106-107`) via `@RequestParam`, so it's worth
  confirming Spring's default enum-binding failure for that parameter is *also* mapped to `400` by
  the same new handler, not just `scope` once it's changed to match.

## Risks & Considerations

- Adding a handler for `MethodArgumentTypeMismatchException` is service-wide — verify no other
  endpoint currently relies on that exception type surfacing as `500` (a search shows no such
  handler exists yet, so this is a net-new mapping, not a change to existing behavior for other
  endpoints).
- No security or data-integrity impact: the endpoint requires authentication already (FR-006),
  and no state is mutated on this read path.
- No stack trace or internal identifier is currently leaked to the client (no
  `server.error.include-message`/`include-stacktrace` config found in `application.yml`), so this
  is a correctness/contract-consistency defect, not an information-disclosure one — severity is
  driven by the constitution Principle I violation and the noisy 500 in production logs for a
  routine bad-input case, not by client-facing data exposure.

## Open Questions

- [NEEDS CLARIFICATION: confirm whether the client actually sent `scope=created` (matching the
  stack trace) or `scoped=created` (matching the reported URL) — see Reproduction section above.]
