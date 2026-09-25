-- Embedding dimension (1024) matches Voyage AI's voyage-3 model output
-- (research.md "Embedding generation"). Changing embedding models requires a new migration
-- to alter this column's dimension and re-embed existing entries.
CREATE TABLE knowledge_base_entries (
    id                     UUID PRIMARY KEY,
    ticket_id              UUID          NOT NULL UNIQUE REFERENCES tickets (id),
    embedding              vector(1024)  NOT NULL,
    resolution_content     TEXT,
    has_resolution_content BOOLEAN       NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_knowledge_base_entries_embedding
    ON knowledge_base_entries USING hnsw (embedding vector_cosine_ops);
