package com.frequency.ticketing.domain.ticket;

import java.util.UUID;

/**
 * Published when a ticket reaches {@code RESOLVED} (spec 005 FR-018). {@code KnowledgeBaseService}
 * listens after commit to build the ticket's knowledge base entry without holding up the
 * transition itself (research.md "Knowledge base indexing trigger").
 */
public record TicketResolvedEvent(UUID ticketId) {}
