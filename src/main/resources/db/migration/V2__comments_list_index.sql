-- Additive composite index backing GET /api/v1/tickets/{ticketId}/comments (feature
-- 002-list-comments): a query that filters by ticket_id AND sorts by created_at benefits from
-- a composite index covering both, keeping SC-002 (1,000 comments, <2s) comfortably met.
CREATE INDEX idx_comments_ticket_id_created_at ON comments (ticket_id, created_at);
