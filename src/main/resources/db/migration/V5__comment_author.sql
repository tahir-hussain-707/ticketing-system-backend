-- IF NOT EXISTS: makes this script safe to re-run if flyway_schema_history's row for this
-- version is ever removed/repaired while the column itself already exists on the target DB.
ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_id UUID REFERENCES users (id);

-- Backfill pre-existing comment rows with the system user before enforcing NOT NULL,
-- so this migration is safe on environments that already have comment data
-- (same pattern as V4__ticket_ownership_and_assignment.sql).
INSERT INTO users (id, name, email, password_hash, role, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'System', 'system@frequency.local', 'N/A', 'ADMIN', now(), now())
ON CONFLICT (id) DO NOTHING;

UPDATE comments
SET author_id = '00000000-0000-0000-0000-000000000001'
WHERE author_id IS NULL;

ALTER TABLE comments ALTER COLUMN author_id SET NOT NULL;
