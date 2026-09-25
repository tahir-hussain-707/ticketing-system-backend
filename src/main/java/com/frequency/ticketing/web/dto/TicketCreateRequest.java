package com.frequency.ticketing.web.dto;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * priority is optional — {@code TicketService} defaults it to MEDIUM when omitted (FR-001).
 * Carries no assignee field — the assignee is always the system's own automatic choice (spec 005
 * FR-009).
 */
public record TicketCreateRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank String description,
    TicketPriority priority) {}
