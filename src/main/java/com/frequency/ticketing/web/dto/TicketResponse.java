package com.frequency.ticketing.web.dto;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code assignee} is nullable (no {@code SUPPORT} user was available at creation, FR-012);
 * {@code createdBy} is always present (FR-006 requires an authenticated creator).
 */
public record TicketResponse(
    UUID id,
    String title,
    String description,
    TicketPriority priority,
    TicketStatus status,
    UserSummary assignee,
    UserSummary createdBy,
    Instant createdAt,
    Instant updatedAt) {}
