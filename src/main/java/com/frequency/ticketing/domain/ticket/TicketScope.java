package com.frequency.ticketing.domain.ticket;

/**
 * Ticket-list ownership scope (spec 005 FR-016). {@code ALL} means "every ticket the caller is
 * permitted to see" — narrowed to {@code MINE} server-side for a {@code GENERAL} caller
 * (research.md "Listing scope semantics").
 */
public enum TicketScope {
  MINE,
  ASSIGNED,
  ALL
}
