package com.frequency.ticketing.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.frequency.ticketing.domain.exception.InvalidTransitionException;
import com.frequency.ticketing.domain.ticket.Ticket;
import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.domain.ticket.TicketStatusTransitionPolicy;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exhaustive coverage of every (from, to) pair in the 5-state machine (FR-009, FR-010) — pure
 * unit test, no Spring context needed.
 */
class TicketStatusTransitionPolicyTest {

  private final TicketStatusTransitionPolicy policy = new TicketStatusTransitionPolicy();

  private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = new EnumMap<>(TicketStatus.class);

  static {
    ALLOWED.put(TicketStatus.OPEN, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
    ALLOWED.put(TicketStatus.IN_PROGRESS, EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED));
    ALLOWED.put(TicketStatus.RESOLVED, EnumSet.of(TicketStatus.CLOSED));
    ALLOWED.put(TicketStatus.CLOSED, EnumSet.noneOf(TicketStatus.class));
    ALLOWED.put(TicketStatus.CANCELLED, EnumSet.noneOf(TicketStatus.class));
  }

  static Stream<org.junit.jupiter.params.provider.Arguments> allPairs() {
    Stream.Builder<org.junit.jupiter.params.provider.Arguments> builder = Stream.builder();
    for (TicketStatus from : TicketStatus.values()) {
      for (TicketStatus to : TicketStatus.values()) {
        builder.add(org.junit.jupiter.params.provider.Arguments.of(from, to));
      }
    }
    return builder.build();
  }

  private Ticket ticketAt(TicketStatus status) {
    Ticket ticket = new Ticket("t", "d", TicketPriority.LOW, null);
    if (status != TicketStatus.OPEN) {
      // Drive it there through legal moves only, to keep the fixture honest.
      switch (status) {
        case IN_PROGRESS -> policy.transition(ticket, TicketStatus.IN_PROGRESS);
        case RESOLVED -> {
          policy.transition(ticket, TicketStatus.IN_PROGRESS);
          policy.transition(ticket, TicketStatus.RESOLVED);
        }
        case CLOSED -> {
          policy.transition(ticket, TicketStatus.IN_PROGRESS);
          policy.transition(ticket, TicketStatus.RESOLVED);
          policy.transition(ticket, TicketStatus.CLOSED);
        }
        case CANCELLED -> policy.transition(ticket, TicketStatus.CANCELLED);
        default -> {}
      }
    }
    return ticket;
  }

  @ParameterizedTest
  @MethodSource("allPairs")
  void everyPairMatchesTheAllowedTable(TicketStatus from, TicketStatus to) {
    Ticket ticket = ticketAt(from);
    boolean isAllowed = ALLOWED.get(from).contains(to);

    if (isAllowed) {
      policy.transition(ticket, to);
      assertThat(ticket.getStatus()).isEqualTo(to);
    } else {
      assertThatThrownBy(() -> policy.transition(ticket, to))
          .isInstanceOf(InvalidTransitionException.class);
      assertThat(ticket.getStatus()).isEqualTo(from);
    }
  }

  @Test
  void everyReversalToOpenIsRejected() {
    for (TicketStatus from :
        new TicketStatus[] {TicketStatus.CLOSED, TicketStatus.RESOLVED, TicketStatus.CANCELLED}) {
      Ticket ticket = ticketAt(from);
      assertThatThrownBy(() -> policy.transition(ticket, TicketStatus.OPEN))
          .isInstanceOf(InvalidTransitionException.class);
      assertThat(ticket.getStatus()).isEqualTo(from);
    }
  }
}
