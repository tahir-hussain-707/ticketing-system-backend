# Quickstart: Authentication, Auto-Assignment & Scoped Ticket Access

Validates the feature end-to-end against a running instance. See `data-model.md` for entity
shapes and `contracts/` for exact request/response schemas.

## Prerequisites

- PostgreSQL 16+ reachable per `application.yml`, migrations `V1`–`V5` applied.
- The service running with the `dev` Spring profile active, so the seed users exist (see
  research.md "Seed users"): one `ADMIN`, at least two `SUPPORT`, and one `GENERAL` user, each
  with a known email/password for local testing.
- `curl` and a cookie jar (`-c`/`-b`), or an equivalent HTTP client that persists cookies.

## 1. Unauthenticated access is rejected (FR-006, FR-007)

```bash
curl -i -X POST localhost:8080/api/v1/tickets \
  -H 'Content-Type: application/json' \
  -d '{"title":"Cannot log in","description":"..."}'
# Expect: 401
```

## 2. Log in (FR-001, FR-002, FR-003)

```bash
curl -i -c cookies.txt -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"general@example.test","password":"<seed password>"}'
# Expect: 200, LoginResponse body, Set-Cookie in response headers

curl -i -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"general@example.test","password":"wrong"}'
# Expect: 401, same generic error as an unregistered email
```

## 3. Create a ticket and confirm auto-assignment (FR-008, FR-009, FR-010)

```bash
curl -i -b cookies.txt -X POST localhost:8080/api/v1/tickets \
  -H 'Content-Type: application/json' \
  -d '{"title":"Printer offline","description":"...","priority":"MEDIUM"}'
# Expect: 201, TicketResponse.assignee = the SUPPORT user with the fewest OPEN/IN_PROGRESS
# tickets at that moment (not caller-supplied — the request body has no assignee field).
```

Repeat with a second `GENERAL`/`SUPPORT` login and a new ticket; confirm the assignee rotates to
whichever `SUPPORT` user now has the smaller count.

## 4. Comment authorization (FR-012, FR-013)

```bash
# As the ticket's creator — succeeds, named
curl -i -b cookies.txt -X POST localhost:8080/api/v1/tickets/{ticketId}/comments \
  -H 'Content-Type: application/json' -d '{"content":"Any update?"}'
# Expect: 201, CommentResponse.authorName = the creator's name

# As an unrelated logged-in user — rejected
curl -i -b other-user-cookies.txt -X POST localhost:8080/api/v1/tickets/{ticketId}/comments \
  -H 'Content-Type: application/json' -d '{"content":"..."}'
# Expect: 403
```

## 5. Scoped listing (FR-014, FR-015)

```bash
curl -s -b cookies.txt 'localhost:8080/api/v1/tickets?scope=mine' | jq '.content | length'
curl -s -b cookies.txt 'localhost:8080/api/v1/tickets?scope=assigned' | jq '.content | length'
curl -s -b cookies.txt 'localhost:8080/api/v1/tickets?scope=all&status=OPEN' | jq '.content | length'
```

## 6. ADMIN-only reassignment (FR-016)

```bash
# As ADMIN — succeeds
curl -i -b admin-cookies.txt -X PATCH localhost:8080/api/v1/tickets/{ticketId}/assignee \
  -H 'Content-Type: application/json' -d '{"assigneeId":"<a SUPPORT user id>"}'
# Expect: 200

# As non-ADMIN — rejected
curl -i -b cookies.txt -X PATCH localhost:8080/api/v1/tickets/{ticketId}/assignee \
  -H 'Content-Type: application/json' -d '{"assigneeId":"<a SUPPORT user id>"}'
# Expect: 403
```

## 7. Logout (FR-017)

```bash
curl -i -b cookies.txt -X POST localhost:8080/api/v1/auth/logout
# Expect: 204

curl -i -b cookies.txt -X POST localhost:8080/api/v1/tickets \
  -H 'Content-Type: application/json' -d '{"title":"x","description":"y"}'
# Expect: 401 — the cookie from step 2 no longer grants access
```

## Not covered by this feature (verify unchanged)

```bash
# Still unauthenticated per this feature's explicit scope boundary (see research.md)
curl -s localhost:8080/api/v1/tickets/{ticketId} | jq .
curl -s localhost:8080/api/v1/tickets/{ticketId}/comments | jq .
```
