# Quickstart: Authenticated Ticketing with RAG Resolution Chatbot

Validates the feature end-to-end against a running instance. See `data-model.md` for entity
shapes and `contracts/` for exact request/response schemas.

## Prerequisites

- PostgreSQL 16+ with the `vector` extension available, migrations `V1`–`V9` applied.
- The service running with the `dev` Spring profile active, so seed users exist: one `ADMIN`, at
  least two `SUPPORT`, one `GENERAL`, each with a known email/password.
- A local Ollama instance running (`ollama serve`) with `mxbai-embed-large` and `llama3.1` pulled
  — see research.md "Embedding generation"/"Response generation"; override via
  `OLLAMA_BASE_URL`/`OLLAMA_EMBEDDING_MODEL`/`OLLAMA_CHAT_MODEL` if using different models or a
  remote Ollama host.
- `curl` and a cookie jar (`-c`/`-b`).

## 1. Only login is open (FR-006)

```bash
curl -i localhost:8080/api/v1/tickets
# Expect: 401 — no session at all

curl -i -X POST localhost:8080/api/v1/chatbot/messages \
  -H 'Content-Type: application/json' -d '{"query":"help"}'
# Expect: 401
```

## 2. Log in (FR-001–004)

```bash
curl -i -c support1.txt -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"support1@example.test","password":"<seed password>"}'
# Expect: 200, LoginResponse, Set-Cookie

curl -i -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"support1@example.test","password":"wrong"}'
# Expect: 401, same generic error as an unregistered email
```

## 3. Create a ticket, confirm auto-assignment (FR-009–012)

```bash
curl -i -c general1.txt -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"general1@example.test","password":"..."}'

curl -i -b general1.txt -X POST localhost:8080/api/v1/tickets \
  -H 'Content-Type: application/json' \
  -d '{"title":"Printer offline","description":"...","priority":"MEDIUM"}'
# Expect: 201, TicketResponse.assignee = the SUPPORT user with fewest OPEN/IN_PROGRESS tickets;
# request body has no assignee field.
```

## 4. View authorization — FR-037 (previously open, now gated both ways)

```bash
TICKET_ID=<id from step 3>

curl -i -b general1.txt localhost:8080/api/v1/tickets/$TICKET_ID
# Expect: 200 — this GENERAL user created it

curl -i -c general2.txt -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"general2@example.test","password":"..."}'
curl -i -b general2.txt localhost:8080/api/v1/tickets/$TICKET_ID
# Expect: 403 — unrelated GENERAL user

curl -i -b support1.txt localhost:8080/api/v1/tickets/$TICKET_ID
# Expect: 200 — SUPPORT sees every ticket
```

## 5. Comment authorization + attribution (FR-014, FR-015)

```bash
curl -i -b general1.txt -X POST localhost:8080/api/v1/tickets/$TICKET_ID/comments \
  -H 'Content-Type: application/json' -d '{"content":"Any update?"}'
# Expect: 201, CommentResponse.authorName = general1's name

curl -i -b general2.txt -X POST localhost:8080/api/v1/tickets/$TICKET_ID/comments \
  -H 'Content-Type: application/json' -d '{"content":"..."}'
# Expect: 403
```

## 6. Scoped listing (FR-016, FR-017)

```bash
curl -s -b general1.txt 'localhost:8080/api/v1/tickets?scope=mine' | jq '.content | length'
curl -s -b support1.txt 'localhost:8080/api/v1/tickets?scope=assigned' | jq '.content | length'
curl -s -b support1.txt 'localhost:8080/api/v1/tickets?scope=all&status=OPEN' | jq '.content | length'
```

## 7. ADMIN-only reassignment (FR-013)

```bash
curl -i -c admin1.txt -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"admin1@example.test","password":"..."}'

curl -i -b admin1.txt -X PATCH localhost:8080/api/v1/tickets/$TICKET_ID/assignee \
  -H 'Content-Type: application/json' -d '{"assigneeId":"<a SUPPORT user id>"}'
# Expect: 200

curl -i -b general1.txt -X PATCH localhost:8080/api/v1/tickets/$TICKET_ID/assignee \
  -H 'Content-Type: application/json' -d '{"assigneeId":"<a SUPPORT user id>"}'
# Expect: 403
```

## 8. Knowledge base indexing + chatbot resolution (FR-018–030)

```bash
# Resolve the ticket (via its normal transition path), attaching a comment with a real
# resolution first, per FR-020/FR-027.
curl -i -b support1.txt -X POST localhost:8080/api/v1/tickets/$TICKET_ID/comments \
  -H 'Content-Type: application/json' -d '{"content":"Replaced the toner cartridge, confirmed fixed."}'
curl -i -b support1.txt -X POST localhost:8080/api/v1/tickets/$TICKET_ID/transitions \
  -H 'Content-Type: application/json' -d '{"status":"IN_PROGRESS"}'
curl -i -b support1.txt -X POST localhost:8080/api/v1/tickets/$TICKET_ID/transitions \
  -H 'Content-Type: application/json' -d '{"status":"RESOLVED"}'

# Knowledge base indexing is async (research.md) — give it a moment, then query the chatbot.
curl -s -b general1.txt -X POST localhost:8080/api/v1/chatbot/messages \
  -H 'Content-Type: application/json' \
  -d '{"query":"My printer is offline, what do I do?"}' | jq .
# Expect: 200, responseText grounded in the resolution above, sourceTicketIds: [<TICKET_ID>],
# confidentMatch: true — content reworded, no raw internal names (FR-031/032).
```

## 9. Honest no-match (FR-028)

```bash
curl -s -b general1.txt -X POST localhost:8080/api/v1/chatbot/messages \
  -H 'Content-Type: application/json' \
  -d '{"query":"Completely unrelated issue never seen before, xyzzy plugh"}' | jq .
# Expect: 200, confidentMatch: false, sourceTicketIds: [], responseText directs to manual ticket
# creation — no ticket is created automatically.
```

## 10. Multi-turn conversation context (FR-033, FR-034)

```bash
RESP=$(curl -s -b general1.txt -X POST localhost:8080/api/v1/chatbot/messages \
  -H 'Content-Type: application/json' -d '{"query":"printer offline"}')
CONV_ID=$(echo "$RESP" | jq -r .conversationId)

curl -s -b general1.txt -X POST localhost:8080/api/v1/chatbot/messages \
  -H 'Content-Type: application/json' \
  -d "{\"query\":\"what about on a Mac?\",\"conversationId\":\"$CONV_ID\"}" | jq .
# Expect: response accounts for the earlier "printer offline" context.

curl -i -b general1.txt -X POST localhost:8080/api/v1/chatbot/conversations/$CONV_ID/end
# Expect: 204 — a subsequent message with no conversationId starts a new conversation.
```

## Constitution/logging spot-check

```bash
# Confirm the transition log line now includes the actor (research.md "Fixing the
# actor-logging gap"), e.g.: ticket_transition ticketId=... from=OPEN to=IN_PROGRESS actor=<userId>
grep ticket_transition <application log output>
```
