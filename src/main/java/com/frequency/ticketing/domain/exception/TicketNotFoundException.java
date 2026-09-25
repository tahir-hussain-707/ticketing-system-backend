package com.frequency.ticketing.domain.exception;

import java.util.UUID;

/** Thrown when a request references a ticket id that does not exist. */
public class TicketNotFoundException extends RuntimeException {

  public TicketNotFoundException(UUID ticketId) {
    super("Ticket not found: " + ticketId);
  }
}
