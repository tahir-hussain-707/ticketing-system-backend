package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.ChatbotQueryRequest;
import com.frequency.ticketing.web.dto.ChatbotTurnResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Matches contracts/chatbot-api.yaml exactly (spec 005 FR-021, FR-022, FR-028). */
class ChatbotMessageContractTest extends AbstractIntegrationTest {

  @Test
  void blankQueryReturns400ValidationFailed() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/chatbot/messages"),
            HttpMethod.POST,
            authEntity(new ChatbotQueryRequest("", null), auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.VALIDATION_FAILED.name());
  }

  @Test
  void noMatchQueryReturns200WithConfidentMatchFalse() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    String nonsense = "xkzqvw" + java.util.UUID.randomUUID() + " flibbertigibbet wombat unrelated";

    ResponseEntity<ChatbotTurnResponse> response =
        restTemplate.exchange(
            baseUrl("/chatbot/messages"),
            HttpMethod.POST,
            authEntity(new ChatbotQueryRequest(nonsense, null), auth),
            ChatbotTurnResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().confidentMatch()).isFalse();
    assertThat(response.getBody().sourceTicketIds()).isEmpty();
  }
}
