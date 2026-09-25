package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.support.FailingAiTestClients;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.ChatbotQueryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Import;

/**
 * FR-030 (spec 005): when the embedding provider fails, the chatbot returns 503 with a distinct
 * {@code ApiError}, not an empty or silently wrong response.
 */
@Import(FailingAiTestClients.class)
class ChatbotServiceUnavailableIntegrationTest extends AbstractIntegrationTest {

  @Test
  void embeddingProviderFailureReturns503() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/chatbot/messages"),
            HttpMethod.POST,
            authEntity(new ChatbotQueryRequest("any query at all", null), auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.AI_SERVICE_UNAVAILABLE.name());
  }
}
