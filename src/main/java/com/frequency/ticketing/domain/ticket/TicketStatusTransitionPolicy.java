package com.frequency.ticketing.domain.ticket;

import com.frequency.ticketing.domain.exception.InvalidTransitionException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for the ticket status state machine (FR-009, FR-010; constitution
 * Principle II). {@link com.frequency.ticketing.domain.ticket.Ticket#applyTransition} is
 * package-private, so this is the ONLY code path that may change a ticket's status.
 */
@Component
public class TicketStatusTransitionPolicy {

  private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = new EnumMap<>(TicketStatus.class);

  static {
    ALLOWED.put(TicketStatus.OPEN, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
    ALLOWED.put(TicketStatus.IN_PROGRESS, EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED));
    ALLOWED.put(TicketStatus.RESOLVED, EnumSet.of(TicketStatus.CLOSED));
    ALLOWED.put(TicketStatus.CLOSED, EnumSet.noneOf(TicketStatus.class));
    ALLOWED.put(TicketStatus.CANCELLED, EnumSet.noneOf(TicketStatus.class));
  }

  /**
   * Applies {@code newStatus} to {@code ticket} if the transition is allowed from its current
   * status, otherwise throws {@link InvalidTransitionException} and leaves the ticket unchanged.
   */
  public void transition(Ticket ticket, TicketStatus newStatus) {
    TicketStatus current = ticket.getStatus();
    if (!ALLOWED.getOrDefault(current, Set.of()).contains(newStatus)) {
      throw new InvalidTransitionException(current, newStatus);
    }
    ticket.applyTransition(newStatus);
  }
}
