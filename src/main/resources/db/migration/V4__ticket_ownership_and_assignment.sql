ALTER TABLE tickets ADD COLUMN created_by_id UUID REFERENCES users (id);
ALTER TABLE tickets ADD COLUMN assignee_id UUID REFERENCES users (id);

-- Backfill pre-existing ticket rows with a system user before enforcing NOT NULL,
-- so this migration is safe on environments that already have ticket data.
INSERT INTO users (id, name, email, password_hash, role, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'System', 'system@frequency.local', 'N/A', 'ADMIN', now(), now())
ON CONFLICT (id) DO NOTHING;

UPDATE tickets
SET created_by_id = '00000000-0000-0000-0000-000000000001'
WHERE created_by_id IS NULL;

ALTER TABLE tickets ALTER COLUMN created_by_id SET NOT NULL;

ALTER TABLE tickets DROP COLUMN assignee;

CREATE INDEX idx_tickets_assignee_id_status ON tickets (assignee_id, status);
CREATE INDEX idx_tickets_created_by_id ON tickets (created_by_id);
