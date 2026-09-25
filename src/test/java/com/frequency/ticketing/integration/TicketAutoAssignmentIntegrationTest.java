package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.Ticket;
import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.user.User;
import com.frequency.ticketing.domain.user.UserRole;
import com.frequency.ticketing.repository.TicketRepository;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * FR-009/FR-010/FR-011/FR-012 (spec 005 User Story 2). Uses freshly created SUPPORT users with a
 * random email per test, and — since {@code UserRepository.findSupportUsersByWorkloadAscending()}
 * naturally returns every {@code SUPPORT} user in the shared Testcontainer, including ones other
 * test classes created — filters its result down to just this test's own candidates before
 * asserting relative order, so pre-existing workload from other tests can never affect the
 * outcome.
 */
class TicketAutoAssignmentIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TicketRepository ticketRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private User freshSupportUser() {
    String email = "support-" + UUID.randomUUID() + "@example.test";
    return userRepository.save(new User("Fresh Support", email, passwordEncoder.encode("x"), UserRole.SUPPORT));
  }

  private void seedOpenTicket(UUID assigneeId, UUID createdById) {
    Ticket ticket = new Ticket("seed", "seed", TicketPriority.LOW, createdById);
    ticket.assignTo(assigneeId);
    ticketRepository.save(ticket);
  }

  private List<UUID> workloadOrderAmong(List<UUID> candidateIds) {
    return userRepository.findSupportUsersByWorkloadAscending().stream()
        .map(User::getId)
        .filter(candidateIds::contains)
        .toList();
  }

  @Test
  void leastLoadedCandidateOrdersFirst() {
    UUID creatorId = idOf(TestUser.GENERAL_1);
    User busy = freshSupportUser();
    User idle = freshSupportUser();
    seedOpenTicket(busy.getId(), creatorId);
    seedOpenTicket(busy.getId(), creatorId);

    List<UUID> order = workloadOrderAmong(List.of(busy.getId(), idle.getId()));

    assertThat(order).containsExactly(idle.getId(), busy.getId());
  }

  @Test
  void tiedCandidatesBreakByAscendingId() {
    User first = freshSupportUser();
    User second = freshSupportUser();
    UUID lower = first.getId().compareTo(second.getId()) < 0 ? first.getId() : second.getId();
    UUID higher = first.getId().compareTo(second.getId()) < 0 ? second.getId() : first.getId();

    List<UUID> order = workloadOrderAmong(List.of(first.getId(), second.getId()));

    assertThat(order).containsExactly(lower, higher);
  }

  @Test
  void ticketCreationAssignsToASupportUserWhenOneExists() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Assign me", "desc", TicketPriority.LOW), auth),
                TicketResponse.class)
            .getBody();

    // At least one SUPPORT user always exists by this point (seeded by other tests/TestUser),
    // so the ticket must come back assigned — proves FR-009/FR-010 wire correctly end-to-end.
    assertThat(created.assignee()).isNotNull();
    assertThat(created.assignee().id()).isNotNull();
  }

  @Test
  void resolvedTicketNoLongerCountsTowardWorkload() {
    UUID creatorId = idOf(TestUser.GENERAL_1);
    User candidate = freshSupportUser();
    User other = freshSupportUser();

    Ticket resolved = new Ticket("will resolve", "d", TicketPriority.LOW, creatorId);
    resolved.assignTo(candidate.getId());
    ticketRepository.save(resolved);
    seedOpenTicket(other.getId(), creatorId);

    // Force the ticket straight to RESOLVED at the row level — this test only cares about the
    // raw status value the workload query filters on (FR-010), not the legality of the path
    // there (already covered by TicketStateMachineIntegrationTest).
    jdbcTemplate.update("UPDATE tickets SET status = 'RESOLVED' WHERE id = ?", resolved.getId());

    List<UUID> order = workloadOrderAmong(List.of(candidate.getId(), other.getId()));

    // candidate now has zero counted (RESOLVED doesn't count) tickets, other has one (OPEN).
    assertThat(order).containsExactly(candidate.getId(), other.getId());
  }
}
