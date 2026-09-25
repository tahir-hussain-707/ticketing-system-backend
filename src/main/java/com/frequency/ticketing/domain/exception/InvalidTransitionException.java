package com.frequency.ticketing.domain.exception;

import com.frequency.ticketing.domain.ticket.TicketStatus;

/** Thrown when a requested status transition is not in the allowed set (FR-009, FR-010). */
public class InvalidTransitionException extends RuntimeException {

  public InvalidTransitionException(TicketStatus from, TicketStatus to) {
    super("Cannot transition ticket from " + from + " to " + to);
  }
}
