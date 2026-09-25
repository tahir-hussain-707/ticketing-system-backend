package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.repository.KnowledgeBaseEntryRepository;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ChatbotQueryRequest;
import com.frequency.ticketing.web.dto.ChatbotTurnResponse;
import com.frequency.ticketing.web.dto.CommentCreateRequest;
import com.frequency.ticketing.web.dto.CommentResponse;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import com.frequency.ticketing.web.dto.TicketTransitionRequest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * FR-023/024/025 (spec 005 User Story 3, Scenarios 1-3): a query semantically close to a resolved
 * ticket returns a response grounded in that ticket's actual resolution and cites it.
 */
class ChatbotResolutionIntegrationTest extends AbstractIntegrationTest {

  @Autowired private KnowledgeBaseEntryRepository knowledgeBaseEntryRepository;

  private UUID resolveTicketWithComment(HttpHeaders auth, String uniqueToken) {
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(
                    new TicketCreateRequest(
                        uniqueToken + " printer offline", "the office printer will not turn on", TicketPriority.LOW),
                    auth),
                TicketResponse.class)
            .getBody();

    restTemplate.exchange(
        baseUrl("/tickets/" + created.id() + "/comments"),
        HttpMethod.POST,
        authEntity(
            new CommentCreateRequest(uniqueToken + " resolution: replaced the power cable, confirmed fixed"), auth),
        CommentResponse.class);

    restTemplate.exchange(
        baseUrl("/tickets/" + created.id() + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(TicketStatus.IN_PROGRESS), auth),
        TicketResponse.class);
    restTemplate.exchange(
        baseUrl("/tickets/" + created.id() + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(TicketStatus.RESOLVED), auth),
        TicketResponse.class);

    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> knowledgeBaseEntryRepository.findByTicketId(created.id()).isPresent());

    return created.id();
  }

  @Test
  void similarQueryReturnsResponseGroundedInPastResolution() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    String uniqueToken = "zqtok" + UUID.randomUUID().toString().substring(0, 8);
    UUID ticketId = resolveTicketWithComment(auth, uniqueToken);

    ChatbotTurnResponse response =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(new ChatbotQueryRequest(uniqueToken + " printer offline, what do I do?", null), auth),
                ChatbotTurnResponse.class)
            .getBody();

    assertThat(response.confidentMatch()).isTrue();
    assertThat(response.sourceTicketIds()).contains(ticketId);
    assertThat(response.responseText()).containsIgnoringCase("power cable");
  }
}
