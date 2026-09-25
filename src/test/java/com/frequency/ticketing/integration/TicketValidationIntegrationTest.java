package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketPage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Blank title, blank description, and an invalid priority value are each rejected with 400
 * VALIDATION_FAILED and per-field detail, with no row written (FR-011, SC-005).
 */
class TicketValidationIntegrationTest extends AbstractIntegrationTest {

  @Test
  void blankTitleRejectedWithFieldError() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest("", "desc", TicketPriority.LOW), auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.VALIDATION_FAILED.name());
    assertThat(response.getBody().fieldErrors()).anySatisfy(fe -> assertThat(fe.field()).isEqualTo("title"));
  }

  @Test
  void blankDescriptionRejectedWithFieldError() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest("Title", "", TicketPriority.LOW), auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().fieldErrors())
        .anySatisfy(fe -> assertThat(fe.field()).isEqualTo("description"));
  }

  @Test
  void invalidPriorityRejectedAndNoRowWritten() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    long countBefore = countTickets(auth);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(Map.of("title", "Bad priority", "description", "d", "priority", "URGENT"), auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(countTickets(auth)).isEqualTo(countBefore);
  }

  private long countTickets(HttpHeaders auth) {
    return restTemplate
        .exchange(baseUrl("/tickets?size=1&scope=mine"), HttpMethod.GET, authEntity(auth), TicketPage.class)
        .getBody()
        .totalElements();
  }
}
