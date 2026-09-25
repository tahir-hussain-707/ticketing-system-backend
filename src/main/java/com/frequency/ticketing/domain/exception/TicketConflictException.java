package com.frequency.ticketing.domain.exception;

import java.util.UUID;

/** Thrown when a concurrent modification is detected (optimistic-lock conflict, FR-011 edge case). */
public class TicketConflictException extends RuntimeException {

  public TicketConflictException(UUID ticketId) {
    super("Ticket was modified concurrently: " + ticketId);
  }
}
