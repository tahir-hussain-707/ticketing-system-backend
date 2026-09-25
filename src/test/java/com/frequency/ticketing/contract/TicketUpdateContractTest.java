package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import com.frequency.ticketing.web.dto.TicketUpdateRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TicketUpdateContractTest extends AbstractIntegrationTest {

  @Test
  void patchUpdatesSuppliedFieldsAndReturns200() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Original", "desc", TicketPriority.LOW), auth),
                TicketResponse.class)
            .getBody();

    ResponseEntity<TicketResponse> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id()),
            HttpMethod.PATCH,
            authEntity(new TicketUpdateRequest(null, null, TicketPriority.CRITICAL), auth),
            TicketResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().priority()).isEqualTo(TicketPriority.CRITICAL);
    assertThat(response.getBody().title()).isEqualTo("Original");
  }

  @Test
  void patchIgnoresUnknownStatusFieldStatusStaysOpen() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Status guard", "desc", TicketPriority.LOW), auth),
                TicketResponse.class)
            .getBody();

    // TicketUpdateRequest has no status field — sending one via raw map proves PATCH
    // cannot be used to reach status (FR-013): it's silently ignored, not applied.
    ResponseEntity<TicketResponse> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id()),
            HttpMethod.PATCH,
            authEntity(Map.of("title", "Still open", "status", "CLOSED"), auth),
            TicketResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(TicketStatus.OPEN);
  }
}
