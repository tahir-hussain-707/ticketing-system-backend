package com.frequency.ticketing.domain.ticket;

/** The ticket lifecycle states enforced by {@link TicketStatusTransitionPolicy}. */
public enum TicketStatus {
  OPEN,
  IN_PROGRESS,
  RESOLVED,
  CLOSED,
  CANCELLED
}
