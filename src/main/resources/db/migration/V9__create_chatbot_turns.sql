CREATE TABLE chatbot_turns (
    id                UUID PRIMARY KEY,
    conversation_id   UUID          NOT NULL REFERENCES chatbot_conversations (id) ON DELETE CASCADE,
    query_text        TEXT          NOT NULL,
    response_text     TEXT          NOT NULL,
    source_ticket_ids UUID[]        NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_chatbot_turns_conversation_id ON chatbot_turns (conversation_id);
