package com.frequency.ticketing.web.dto;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ticket detail view: {@code TicketResponse} fields plus the ordered comment history (FR-003). */
public record TicketDetailResponse(
    UUID id,
    String title,
    String description,
    TicketPriority priority,
    TicketStatus status,
    UserSummary assignee,
    UserSummary createdBy,
    Instant createdAt,
    Instant updatedAt,
    List<CommentResponse> comments) {}
