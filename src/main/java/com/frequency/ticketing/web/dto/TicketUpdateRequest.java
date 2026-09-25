package com.frequency.ticketing.web.dto;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import jakarta.validation.constraints.Size;

/**
 * All fields optional; only supplied (non-null) fields are changed. Never accepts status
 * (FR-013). Never accepts assignee either — reassignment is exclusively the dedicated
 * {@code ADMIN}-only endpoint (spec 005 FR-013).
 */
public record TicketUpdateRequest(
    @Size(max = 200) String title, String description, TicketPriority priority) {}
