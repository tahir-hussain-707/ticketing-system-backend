package com.frequency.ticketing.web.dto;

import com.frequency.ticketing.domain.ticket.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketTransitionRequest(@NotNull TicketStatus status) {}
