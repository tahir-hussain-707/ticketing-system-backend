package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.ChatbotQueryRequest;
import com.frequency.ticketing.web.dto.ChatbotTurnResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Matches contracts/chatbot-api.yaml exactly (spec 005 FR-036). */
class ChatbotEndConversationContractTest extends AbstractIntegrationTest {

  @Test
  void endingOwnConversationReturns204() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    ChatbotTurnResponse turn =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(new ChatbotQueryRequest("start a conversation", null), auth),
                ChatbotTurnResponse.class)
            .getBody();

    ResponseEntity<Void> response =
        restTemplate.exchange(
            baseUrl("/chatbot/conversations/" + turn.conversationId() + "/end"),
            HttpMethod.POST,
            authEntity(auth),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void endingUnknownConversationReturns404() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/chatbot/conversations/" + UUID.randomUUID() + "/end"),
            HttpMethod.POST,
            authEntity(auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void endingSomeoneElsesConversationReturns404() {
    HttpHeaders owner = loginAs(TestUser.GENERAL_1);
    ChatbotTurnResponse turn =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(new ChatbotQueryRequest("owner's conversation", null), owner),
                ChatbotTurnResponse.class)
            .getBody();

    HttpHeaders other = loginAs(TestUser.GENERAL_2);
    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/chatbot/conversations/" + turn.conversationId() + "/end"),
            HttpMethod.POST,
            authEntity(other),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
