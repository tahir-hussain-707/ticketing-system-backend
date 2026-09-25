package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frequency.ticketing.domain.knowledgebase.KnowledgeBaseEntry;
import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.repository.KnowledgeBaseEntryRepository;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.CommentCreateRequest;
import com.frequency.ticketing.web.dto.CommentResponse;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import com.frequency.ticketing.web.dto.TicketTransitionRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * FR-018, FR-026, FR-027 (spec 005 User Story 4): resolving a ticket indexes it with no manual
 * step; an unresolved ticket is never indexed; a resolved ticket with no comments is indexed but
 * flagged as carrying no resolution content.
 */
class KnowledgeBaseIndexingIntegrationTest extends AbstractIntegrationTest {

  @Autowired private KnowledgeBaseEntryRepository knowledgeBaseEntryRepository;

  private TicketResponse createTicket(HttpHeaders auth, String title) {
    return restTemplate
        .exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest(title, "desc " + title, TicketPriority.LOW), auth),
            TicketResponse.class)
        .getBody();
  }

  @Test
  void resolvingATicketIndexesItWithoutManualStep() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    TicketResponse ticket = createTicket(auth, "kb index " + UUID.randomUUID());

    restTemplate.exchange(
        baseUrl("/tickets/" + ticket.id() + "/comments"),
        HttpMethod.POST,
        authEntity(new CommentCreateRequest("fixed it"), auth),
        CommentResponse.class);
    restTemplate.exchange(
        baseUrl("/tickets/" + ticket.id() + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(TicketStatus.IN_PROGRESS), auth),
        TicketResponse.class);
    restTemplate.exchange(
        baseUrl("/tickets/" + ticket.id() + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(TicketStatus.RESOLVED), auth),
        TicketResponse.class);

    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> knowledgeBaseEntryRepository.findByTicketId(ticket.id()).isPresent());

    KnowledgeBaseEntry entry = knowledgeBaseEntryRepository.findByTicketId(ticket.id()).orElseThrow();
    assertThat(entry.isHasResolutionContent()).isTrue();
  }

  @Test
  void unresolvedTicketIsNeverIndexed() throws InterruptedException {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    TicketResponse ticket = createTicket(auth, "still open " + UUID.randomUUID());

    restTemplate.exchange(
        baseUrl("/tickets/" + ticket.id() + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(TicketStatus.IN_PROGRESS), auth),
        TicketResponse.class);

    // No RESOLVED transition — give the (non-existent) async indexing a moment, then confirm
    // nothing was created.
    Thread.sleep(500);
    Optional<KnowledgeBaseEntry> entry = knowledgeBaseEntryRepository.findByTicketId(ticket.id());
    assertThat(entry).isEmpty();
  }

  @Test
  void resolvedTicketWithNoCommentsIsIndexedButFlaggedNoResolutionContent() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    TicketResponse ticket = createTicket(auth, "no comments " + UUID.randomUUID());

    restTemplate.exchange(
        baseUrl("/tickets/" + ticket.id() + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(TicketStatus.IN_PROGRESS), auth),
        TicketResponse.class);
    restTemplate.exchange(
        baseUrl("/tickets/" + ticket.id() + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(TicketStatus.RESOLVED), auth),
        TicketResponse.class);

    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> knowledgeBaseEntryRepository.findByTicketId(ticket.id()).isPresent());

    KnowledgeBaseEntry entry = knowledgeBaseEntryRepository.findByTicketId(ticket.id()).orElseThrow();
    assertThat(entry.isHasResolutionContent()).isFalse();
  }
}
