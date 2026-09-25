package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketDetailResponse;
import com.frequency.ticketing.web.dto.TicketPage;
import com.frequency.ticketing.web.dto.TicketResponse;
import com.frequency.ticketing.web.dto.TicketUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** Create -> list -> view -> update happy path (SC-001): fields intact on every read. */
class TicketLifecycleIntegrationTest extends AbstractIntegrationTest {

  @Test
  void createListViewUpdateRoundTrip() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    TicketCreateRequest createRequest =
        new TicketCreateRequest("Lifecycle ticket", "Full round trip", TicketPriority.HIGH);
    TicketResponse created =
        restTemplate
            .exchange(baseUrl("/tickets"), HttpMethod.POST, authEntity(createRequest, auth), TicketResponse.class)
            .getBody();

    assertThat(created.title()).isEqualTo("Lifecycle ticket");
    assertThat(created.description()).isEqualTo("Full round trip");
    assertThat(created.priority()).isEqualTo(TicketPriority.HIGH);
    assertThat(created.createdBy().id()).isEqualTo(idOf(TestUser.GENERAL_1));
    assertThat(created.status()).isEqualTo(TicketStatus.OPEN);

    TicketPage page =
        restTemplate
            .exchange(baseUrl("/tickets?scope=mine"), HttpMethod.GET, authEntity(auth), TicketPage.class)
            .getBody();
    assertThat(page.content()).anySatisfy(t -> assertThat(t.id()).isEqualTo(created.id()));

    TicketDetailResponse detail =
        restTemplate
            .exchange(
                baseUrl("/tickets/" + created.id()), HttpMethod.GET, authEntity(auth), TicketDetailResponse.class)
            .getBody();
    assertThat(detail.title()).isEqualTo("Lifecycle ticket");
    assertThat(detail.comments()).isEmpty();

    TicketResponse updated =
        restTemplate
            .exchange(
                baseUrl("/tickets/" + created.id()),
                HttpMethod.PATCH,
                authEntity(new TicketUpdateRequest("Updated title", null, TicketPriority.CRITICAL), auth),
                TicketResponse.class)
            .getBody();
    assertThat(updated.title()).isEqualTo("Updated title");
    assertThat(updated.description()).isEqualTo("Full round trip"); // untouched field intact
    assertThat(updated.priority()).isEqualTo(TicketPriority.CRITICAL);

    TicketDetailResponse afterUpdate =
        restTemplate
            .exchange(
                baseUrl("/tickets/" + created.id()), HttpMethod.GET, authEntity(auth), TicketDetailResponse.class)
            .getBody();
    assertThat(afterUpdate.title()).isEqualTo("Updated title");
  }
}
