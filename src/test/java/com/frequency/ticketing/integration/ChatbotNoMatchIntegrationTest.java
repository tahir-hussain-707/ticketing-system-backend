package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ChatbotQueryRequest;
import com.frequency.ticketing.web.dto.ChatbotTurnResponse;
import com.frequency.ticketing.web.dto.TicketPage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * FR-028 (spec 005 User Story 6): no confident match → honest response directing to manual
 * ticket creation, and — critically — no ticket is created automatically as a side effect.
 */
class ChatbotNoMatchIntegrationTest extends AbstractIntegrationTest {

  @Test
  void noConfidentMatchIsHonestAndCreatesNoTicket() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    long before = countMyTickets(auth);
    String nonsense = "qzxwv" + UUID.randomUUID() + " an issue never seen before";

    ChatbotTurnResponse response =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(new ChatbotQueryRequest(nonsense, null), auth),
                ChatbotTurnResponse.class)
            .getBody();

    assertThat(response.confidentMatch()).isFalse();
    assertThat(response.sourceTicketIds()).isEmpty();
    assertThat(response.responseText()).containsIgnoringCase("raise a ticket");
    assertThat(countMyTickets(auth)).isEqualTo(before);
  }

  private long countMyTickets(HttpHeaders auth) {
    return restTemplate
        .exchange(baseUrl("/tickets?scope=mine&size=1"), HttpMethod.GET, authEntity(auth), TicketPage.class)
        .getBody()
        .totalElements();
  }
}
