package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketDetailResponse;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TicketDetailContractTest extends AbstractIntegrationTest {

  @Test
  void getExistingTicketReturns200WithEmptyComments() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Detail me", "desc", TicketPriority.LOW), auth),
                TicketResponse.class)
            .getBody();

    ResponseEntity<TicketDetailResponse> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id()),
            HttpMethod.GET,
            authEntity(auth),
            TicketDetailResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().id()).isEqualTo(created.id());
    assertThat(response.getBody().comments()).isEmpty();
  }

  @Test
  void getUnknownTicketReturns404NotFound() {
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + UUID.randomUUID()), HttpMethod.GET, authEntity(auth), ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.TICKET_NOT_FOUND.name());
  }

  @Test
  void getTicketWithoutAuthenticationReturns401() {
    ResponseEntity<ApiError> response =
        restTemplate.getForEntity(baseUrl("/tickets/" + UUID.randomUUID()), ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void getTicketAsUnrelatedGeneralUserReturns403() {
    HttpHeaders creatorAuth = loginAs(TestUser.GENERAL_1);
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Private ticket", "desc", TicketPriority.LOW), creatorAuth),
                TicketResponse.class)
            .getBody();

    HttpHeaders otherAuth = loginAs(TestUser.GENERAL_2);
    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id()), HttpMethod.GET, authEntity(otherAuth), ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
