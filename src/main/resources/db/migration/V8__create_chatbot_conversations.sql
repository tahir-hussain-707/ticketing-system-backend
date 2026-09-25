CREATE TABLE chatbot_conversations (
    id                  UUID PRIMARY KEY,
    user_id             UUID          NOT NULL REFERENCES users (id),
    started_at          TIMESTAMPTZ   NOT NULL,
    explicitly_ended_at TIMESTAMPTZ
);

CREATE INDEX idx_chatbot_conversations_user_started ON chatbot_conversations (user_id, started_at);
