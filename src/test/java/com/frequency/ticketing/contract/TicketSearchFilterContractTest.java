package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketPage;
import com.frequency.ticketing.web.dto.TicketResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TicketSearchFilterContractTest extends AbstractIntegrationTest {

  @Test
  void keywordQueryReturns200WithPageShape() {
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);
    restTemplate.exchange(
        baseUrl("/tickets"),
        HttpMethod.POST,
        authEntity(new TicketCreateRequest("Contract keyword ticket", "desc", TicketPriority.LOW), auth),
        TicketResponse.class);

    ResponseEntity<TicketPage> response =
        restTemplate.exchange(
            baseUrl("/tickets?q=Contract%20keyword&scope=all"),
            HttpMethod.GET,
            authEntity(auth),
            TicketPage.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().content()).isNotEmpty();
  }

  @Test
  void statusQueryReturns200WithPageShape() {
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);

    ResponseEntity<TicketPage> response =
        restTemplate.exchange(
            baseUrl("/tickets?status=" + TicketStatus.OPEN + "&scope=all"),
            HttpMethod.GET,
            authEntity(auth),
            TicketPage.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void combinedQueryAndStatusReturns200() {
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);

    ResponseEntity<TicketPage> response =
        restTemplate.exchange(
            baseUrl("/tickets?q=contract&status=" + TicketStatus.OPEN + "&scope=all"),
            HttpMethod.GET,
            authEntity(auth),
            TicketPage.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void invalidScopeValueReturns400WithApiErrorNot500() {
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets?scope=created"),
            HttpMethod.GET,
            authEntity(auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.VALIDATION_FAILED.name());
  }

  @Test
  void invalidStatusValueReturns400WithApiErrorNot500() {
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets?status=bogus&scope=all"),
            HttpMethod.GET,
            authEntity(auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.VALIDATION_FAILED.name());
  }
}
