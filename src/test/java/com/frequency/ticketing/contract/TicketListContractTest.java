package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketPage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TicketListContractTest extends AbstractIntegrationTest {

  @Test
  void listTicketsReturns200WithPageShape() {
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);
    restTemplate.exchange(
        baseUrl("/tickets"),
        HttpMethod.POST,
        authEntity(new TicketCreateRequest("List me", "desc", TicketPriority.MEDIUM), auth),
        Void.class);

    ResponseEntity<TicketPage> response =
        restTemplate.exchange(
            baseUrl("/tickets?scope=all"), HttpMethod.GET, authEntity(auth), TicketPage.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().content()).isNotEmpty();
    assertThat(response.getBody().content().stream().anyMatch(t -> t.title().equals("List me"))).isTrue();
  }

  @Test
  void listTicketsWithoutAuthenticationReturns401() {
    ResponseEntity<ApiError> response = restTemplate.getForEntity(baseUrl("/tickets"), ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
