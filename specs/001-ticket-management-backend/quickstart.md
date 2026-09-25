# Quickstart: Ticket Management Backend

Validates the feature end-to-end against the contract in [`contracts/tickets-api.yaml`](./contracts/tickets-api.yaml)
and the entities in [`data-model.md`](./data-model.md). Not a substitute for the automated test
suite (constitution Principle III) — this is a manual/smoke run-through.

## Prerequisites

- Java 25 (LTS) installed
- Docker (for local PostgreSQL and for Testcontainers-backed tests)
- Repository checked out on branch `001-ticket-management-backend`

## 1. Start PostgreSQL locally

```bash
docker run --name ticketing-postgres -e POSTGRES_DB=ticketing \
  -e POSTGRES_USER=ticketing -e POSTGRES_PASSWORD=ticketing \
  -p 5432:5432 -d postgres:16
```

## 2. Run the application

```bash
./mvnw spring-boot:run
```

Flyway applies `V1__init_schema.sql` automatically on startup. Confirm readiness:

```bash
curl -s localhost:8080/actuator/health | grep -i '"status":"UP"'
```

## 3. Exercise User Story 1 — ticket lifecycle (P1)

```bash
# Create (FR-001) — expect 201, status OPEN
TICKET_ID=$(curl -s -X POST localhost:8080/api/v1/tickets \
  -H 'Content-Type: application/json' \
  -d '{"title":"Printer offline","description":"3rd floor printer unreachable","priority":"HIGH"}' \
  | jq -r '.id')

# View details (FR-003) — expect 200, comments: []
curl -s localhost:8080/api/v1/tickets/$TICKET_ID

# List (FR-002) — expect the ticket present in content[]
curl -s localhost:8080/api/v1/tickets

# Update fields, not status (FR-004) — expect 200, title/priority changed
curl -s -X PATCH localhost:8080/api/v1/tickets/$TICKET_ID \
  -H 'Content-Type: application/json' \
  -d '{"assignee":"jane.doe","priority":"CRITICAL"}'

# Legal transition OPEN -> IN_PROGRESS (FR-009) — expect 200
curl -s -X POST localhost:8080/api/v1/tickets/$TICKET_ID/transitions \
  -H 'Content-Type: application/json' -d '{"status":"IN_PROGRESS"}'

# Illegal transition IN_PROGRESS -> OPEN (FR-010) — expect 409, code INVALID_TRANSITION
curl -s -o /dev/stderr -w '%{http_code}\n' -X POST localhost:8080/api/v1/tickets/$TICKET_ID/transitions \
  -H 'Content-Type: application/json' -d '{"status":"OPEN"}'

# Continue the legal path to CLOSED (FR-009)
curl -s -X POST localhost:8080/api/v1/tickets/$TICKET_ID/transitions \
  -H 'Content-Type: application/json' -d '{"status":"RESOLVED"}'
curl -s -X POST localhost:8080/api/v1/tickets/$TICKET_ID/transitions \
  -H 'Content-Type: application/json' -d '{"status":"CLOSED"}'

# CLOSED is terminal — expect 409 (FR-010)
curl -s -o /dev/stderr -w '%{http_code}\n' -X POST localhost:8080/api/v1/tickets/$TICKET_ID/transitions \
  -H 'Content-Type: application/json' -d '{"status":"OPEN"}'
```

## 4. Exercise User Story 2 — comments (P2)

```bash
curl -s -X POST localhost:8080/api/v1/tickets/$TICKET_ID/comments \
  -H 'Content-Type: application/json' -d '{"content":"Escalated to facilities."}'

# Confirm it appears, ordered, on ticket detail (FR-005)
curl -s localhost:8080/api/v1/tickets/$TICKET_ID | jq '.comments'

# Empty content rejected (FR-011) — expect 400, code VALIDATION_FAILED
curl -s -o /dev/stderr -w '%{http_code}\n' -X POST localhost:8080/api/v1/tickets/$TICKET_ID/comments \
  -H 'Content-Type: application/json' -d '{"content":""}'
```

## 5. Exercise User Story 3 — search and filter (P3)

```bash
# Keyword search (FR-006) — expect the printer ticket in results
curl -s 'localhost:8080/api/v1/tickets?q=printer'

# Status filter (FR-007) — expect only CLOSED tickets
curl -s 'localhost:8080/api/v1/tickets?status=CLOSED'

# Combined (FR-007) — expect intersection of both conditions
curl -s 'localhost:8080/api/v1/tickets?q=printer&status=CLOSED'
```

## 6. Restart-durability check (FR-008, SC-003)

```bash
# Stop the app (Ctrl+C on the mvnw process), leave the postgres container running, then restart:
./mvnw spring-boot:run
curl -s localhost:8080/api/v1/tickets/$TICKET_ID   # ticket and its comment still present, unchanged
```

## 7. Run the automated suite

```bash
./mvnw test
```

Expect: full state-machine transition matrix (all 5 legal + all illegal pairs, per FR-009/FR-010),
persistence/restart test, search/filter tests, and validation tests all green (constitution
Principle III — this is the authoritative proof, not this manual quickstart).

## Cleanup

```bash
docker rm -f ticketing-postgres
```
