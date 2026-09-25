# Quickstart: List Comments

Validates this feature end-to-end against
[`contracts/comments-list-api.yaml`](./contracts/comments-list-api.yaml). Builds on
001-ticket-management-backend's quickstart — same running application, same database, no new
setup steps. Not a substitute for the automated test suite (constitution Principle III).

## Prerequisites

Application running per
[`001-ticket-management-backend/quickstart.md`](../001-ticket-management-backend/quickstart.md)
steps 1–2 (PostgreSQL up, `./mvnw spring-boot:run`, migrations applied — including this
feature's `V2__comments_list_index.sql`).

## 1. Create a ticket and add a few comments

```bash
TICKET_ID=$(curl -s -X POST localhost:8080/api/v1/tickets \
  -H 'Content-Type: application/json' \
  -d '{"title":"Comment listing demo","description":"desc","priority":"LOW"}' \
  | jq -r '.id')

curl -s -X POST localhost:8080/api/v1/tickets/$TICKET_ID/comments \
  -H 'Content-Type: application/json' -d '{"content":"first"}'
curl -s -X POST localhost:8080/api/v1/tickets/$TICKET_ID/comments \
  -H 'Content-Type: application/json' -d '{"content":"second"}'
curl -s -X POST localhost:8080/api/v1/tickets/$TICKET_ID/comments \
  -H 'Content-Type: application/json' -d '{"content":"third"}'
```

## 2. List the ticket's comments on their own (FR-001)

```bash
curl -s localhost:8080/api/v1/tickets/$TICKET_ID/comments | jq
```

Expect: `200`, `content` has 3 entries ordered `first, second, third` (FR-002), each with
`content` and `createdAt` populated (FR-002a), plus `page`/`size`/`totalElements`/`totalPages`.

## 3. Empty list is not an error (FR-003)

```bash
EMPTY_TICKET_ID=$(curl -s -X POST localhost:8080/api/v1/tickets \
  -H 'Content-Type: application/json' \
  -d '{"title":"No comments yet","description":"desc","priority":"LOW"}' \
  | jq -r '.id')

curl -s localhost:8080/api/v1/tickets/$EMPTY_TICKET_ID/comments | jq
```

Expect: `200`, `content: []`, `totalElements: 0`.

## 4. Unknown ticket is rejected (FR-004)

```bash
curl -s -o /dev/stderr -w '%{http_code}\n' \
  localhost:8080/api/v1/tickets/00000000-0000-0000-0000-000000000000/comments
```

Expect: `404`, body `code: TICKET_NOT_FOUND`.

## 5. Pagination (FR-005)

```bash
curl -s "localhost:8080/api/v1/tickets/$TICKET_ID/comments?page=0&size=2" | jq '.content | length'
curl -s "localhost:8080/api/v1/tickets/$TICKET_ID/comments?page=1&size=2" | jq '.content | length'
```

Expect: page 0 returns 2 comments, page 1 returns the remaining 1.

## 6. Run the automated suite

```bash
./mvnw test
```

Expect `CommentListContractTest` and `CommentListIntegrationTest` green alongside the full
001 suite (constitution Principle III — this is the authoritative proof, not this manual guide).
