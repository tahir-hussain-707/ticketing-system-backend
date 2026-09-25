package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.TicketingApplication;
import com.frequency.ticketing.domain.ticket.Ticket;
import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.repository.TicketRepository;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * Proves restart durability (FR-008, SC-003), not just JPA session-scoping: writes a ticket
 * through the primary test context, then boots an entirely separate, freshly-created Spring
 * {@code ApplicationContext} against the same PostgreSQL Testcontainer — simulating an
 * application restart — and re-reads the ticket from that new context.
 */
class TicketRestartDurabilityIntegrationTest extends AbstractIntegrationTest {

  @Test
  void ticketSurvivesSimulatedApplicationRestart() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Survives restart", "desc", TicketPriority.HIGH), auth),
                TicketResponse.class)
            .getBody();

    ConfigurableApplicationContext restarted =
        new SpringApplicationBuilder(TicketingApplication.class)
            .properties(
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.flyway.enabled=true",
                "server.port=0")
            .run();
    try {
      TicketRepository repository = restarted.getBean(TicketRepository.class);
      Ticket reloaded = repository.findById(created.id()).orElseThrow();

      assertThat(reloaded.getTitle()).isEqualTo("Survives restart");
      assertThat(reloaded.getDescription()).isEqualTo("desc");
      assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.HIGH);
      assertThat(reloaded.getCreatedById()).isEqualTo(idOf(TestUser.GENERAL_1));
      assertThat(reloaded.getStatus()).isEqualTo(created.status());
    } finally {
      restarted.close();
    }
  }
}
