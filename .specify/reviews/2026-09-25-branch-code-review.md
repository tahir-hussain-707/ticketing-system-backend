# Code Review: `feature/rag-implementation` (high effort)

- **Date**: 2026-09-25
- **Scope**: full branch diff
- **Reviewer**: `/code-review` (high)

Track each item as `[ ]` open / `[x]` fixed. Ordered severity-first (security → correctness →
efficiency).

**2026-09-25 fix pass**: 7 of 8 fixed and verified via `./mvnw compile test-compile` (clean) plus
the full unit-test suite (`TicketMapperTest`, `CommentMapperTest`, `ChatbotServiceThresholdTest`,
`TicketStatusTransitionPolicyTest`, `CommentPageTest`, `BeanValidationTest` — 44/44 passing,
including two new regression tests). The Docker-backed integration/contract suite could not be
run in this sandbox (no Docker daemon available) — recommend running the full `./mvnw test` suite
in an environment with Docker before merging. CSRF left open pending a decision — see that item.

## Findings

- [x] **Session fixation on login** — `src/main/java/com/frequency/ticketing/domain/auth/AuthenticationService.java:61`
  Login saves the security context into the existing HTTP session without regenerating the
  session ID (no `changeSessionId`/`migrateSession`).
  **Failure scenario**: attacker sets victim's `JSESSIONID` before login (e.g. via subdomain
  cookie). Victim logs in; the pre-known session id becomes authenticated as the victim —
  hijack without ever stealing a post-login cookie.
  **Fix**: calls `request.changeSessionId()` (when a session already exists) before binding the
  new security context, so any pre-authentication session id is discarded on login.

- [ ] **CSRF disabled under session-cookie auth** — `src/main/java/com/frequency/ticketing/config/SecurityConfig.java:57`
  CSRF is disabled while auth is session-cookie based with credentialed CORS.
  **Failure scenario**: any request that lands as a CORS-simple request, or a future endpoint
  outside `application/json`, rides the ambient session cookie with no CSRF token check —
  state-changing action executes cross-site. Today's protection is incidental to the CORS
  config, not an explicit CSRF defense, and breaks silently if CORS is loosened later.
  **Status**: left disabled — not fixed. Re-enabling CSRF (cookie token repository + eager
  token-resolution filter, per Spring's SPA-CSRF guide) is a breaking change for every existing
  client: it starts rejecting any POST/PATCH/DELETE that doesn't send back an `X-XSRF-TOKEN`
  header. The frontend that calls this API lives in a separate repository not available here, so
  it can't be confirmed or updated to send that header as part of this change, and this sandbox
  has no Docker to run the integration suite and verify the new behavior. Left disabled with a
  documented gap in `SecurityConfig.java`'s class comment pending an explicit decision — needs
  frontend coordination before enabling.

- [x] **Race on knowledge-base upsert violates unique constraint** — `src/main/java/com/frequency/ticketing/domain/knowledgebase/KnowledgeBaseService.java:39`
  Concurrent `onTicketResolved` events for the same ticket race `findByTicketId` → `insert`.
  **Failure scenario**: a ticket reopened and re-resolved twice in quick succession fires two
  async tasks; both read "no existing entry", both `save()` — the second insert throws
  `DataIntegrityViolationException` on `UNIQUE(ticket_id)` (from `V7`), silently swallowed since
  nothing awaits the future.
  **Fix**: replaced `findByTicketId` + `ifPresentOrElse(refresh, save)` with a single atomic
  `INSERT ... ON CONFLICT (ticket_id) DO UPDATE` (`KnowledgeBaseEntryRepository.upsert`) — the
  database now serializes the conflict instead of two racing transactions both inserting.

- [x] **Wrong `ApiError` code for user-not-found** — `src/main/java/com/frequency/ticketing/web/exception/GlobalExceptionHandler.java:94`
  `handleUserNotFound` returns `ApiError.Code.TICKET_NOT_FOUND` instead of a user-specific code.
  **Failure scenario**: `ADMIN` PATCHes `/tickets/{id}/assignee` with a nonexistent
  `assigneeId`; `TicketService.reassign()` throws `UserNotFoundException`, but the client sees
  `code=TICKET_NOT_FOUND` — client-side error handling fires on the wrong condition even though
  the ticket still exists.
  **Fix**: added `ApiError.Code.USER_NOT_FOUND` and switched the handler to return it; added a
  regression test (`TicketReassignContractTest.reassignToNonexistentUserReturns404WithUserNotFoundCode`).

- [x] **N+1 queries mapping ticket list to response** — `src/main/java/com/frequency/ticketing/web/dto/TicketMapper.java:29`
  `summaryOf()` issues a synchronous `findById` per ticket per user field.
  **Failure scenario**: `GET /api/v1/tickets?size=100` triggers up to 200 extra single-row
  `SELECT`s (assignee + createdBy) on top of the page query, instead of one batched `IN(...)`
  lookup.
  **Fix**: added `TicketMapper.toResponseList` — one `findAllById` batched over every
  assignee/createdBy id on the page — and switched `TicketService.search` to use it instead of
  mapping `toResponse` per row.

- [x] **N+1 queries mapping comment list to response** — `src/main/java/com/frequency/ticketing/web/dto/CommentMapper.java:24`
  `toResponse()` issues a `findById` per comment for `authorName`.
  **Failure scenario**: listing a ticket's 50 comments triggers 50 separate author lookups;
  compounds with the `TicketMapper` N+1 since `getDetailById` renders comments through this path
  too.
  **Fix**: `CommentMapper.toResponseList` now batches author lookups via one `findAllById`;
  `CommentService.listByTicket` switched to it instead of mapping `toResponse` per row.

- [x] **Unbounded query to fetch only the latest conversation** — `src/main/java/com/frequency/ticketing/domain/chatbot/ChatbotService.java:138`
  `resolveConversation` loads the caller's entire conversation history just to take the most
  recent one.
  **Failure scenario**: `findByUserIdOrderByStartedAtDesc(callerId)` has no `LIMIT`; a heavy
  chatbot user accumulates conversations across the 90-day retention window — every message
  re-fetches and materializes the whole list before `.findFirst()` discards all but one row.
  Should be a `Top1`/`Pageable(1)` query.
  **Fix**: added `ChatbotConversationRepository.findTopByUserIdOrderByStartedAtDesc` (Spring
  Data's `Top1` derived-query form) and switched `ChatbotService.resolveConversation` to it,
  removing the old unbounded `findByUserIdOrderByStartedAtDesc`.

- [x] **Unbounded async executor, no `TaskExecutor` bean** — `src/main/java/com/frequency/ticketing/TicketingApplication.java:11`
  `@EnableAsync` with no `TaskExecutor` bean defined falls back to `SimpleAsyncTaskExecutor`
  (unbounded thread-per-task).
  **Failure scenario**: a burst of tickets transitioning to `RESOLVED` fires one async task each
  with no pooling/queueing/backpressure — thread count grows unbounded under load instead of a
  bounded executor with a queue.
  **Fix**: added `config/AsyncConfig.java` with a bounded `ThreadPoolTaskExecutor` bean
  (core 4 / max 16 / queue 500), which `@EnableAsync` picks up as the sole `TaskExecutor` bean.

## Suggested fix ownership (by area)

| Area | Findings |
|---|---|
| Security / auth | session fixation, CSRF |
| Async / concurrency | knowledge-base race, unbounded executor |
| Error contract | wrong `ApiError` code |
| Query performance | ticket mapper N+1, comment mapper N+1, unbounded conversation query |
