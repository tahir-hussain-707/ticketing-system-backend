CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE tickets (
    id           UUID PRIMARY KEY,
    title        VARCHAR(200)  NOT NULL,
    description  TEXT          NOT NULL,
    priority     VARCHAR(20)   NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    assignee     VARCHAR(200),
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL
);

CREATE TABLE comments (
    id          UUID PRIMARY KEY,
    ticket_id   UUID          NOT NULL REFERENCES tickets (id),
    content     TEXT          NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_tickets_title_trgm ON tickets USING GIN (title gin_trgm_ops);
CREATE INDEX idx_tickets_description_trgm ON tickets USING GIN (description gin_trgm_ops);
CREATE INDEX idx_tickets_status ON tickets (status);
CREATE INDEX idx_comments_ticket_id ON comments (ticket_id);
