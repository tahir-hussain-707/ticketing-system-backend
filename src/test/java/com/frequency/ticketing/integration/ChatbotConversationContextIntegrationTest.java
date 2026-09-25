package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ChatbotQueryRequest;
import com.frequency.ticketing.web.dto.ChatbotTurnResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** FR-033/FR-034 (spec 005 User Story 3, Scenario 5): a follow-up uses conversation context. */
class ChatbotConversationContextIntegrationTest extends AbstractIntegrationTest {

  @Test
  void followUpQueryIsAttachedToTheSameConversationAndUsesContext() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    String token = "ctxtok" + java.util.UUID.randomUUID().toString().substring(0, 6);

    ChatbotTurnResponse first =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(new ChatbotQueryRequest(token + " unrelated first question", null), auth),
                ChatbotTurnResponse.class)
            .getBody();
    assertThat(first.conversationId()).isNotNull();

    ChatbotTurnResponse followUp =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(
                    new ChatbotQueryRequest("what about on a Mac?", first.conversationId()), auth),
                ChatbotTurnResponse.class)
            .getBody();

    // Same conversation is continued (the stub LLM's response text encodes priorTurns count).
    assertThat(followUp.conversationId()).isEqualTo(first.conversationId());
    assertThat(followUp.responseText()).contains("priorTurns=1");
  }

  @Test
  void omittingConversationIdResumesTheCallersOpenConversation() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_2);

    ChatbotTurnResponse first =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(new ChatbotQueryRequest("first message, no id", null), auth),
                ChatbotTurnResponse.class)
            .getBody();

    ChatbotTurnResponse second =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(new ChatbotQueryRequest("second message, still no id", null), auth),
                ChatbotTurnResponse.class)
            .getBody();

    assertThat(second.conversationId()).isEqualTo(first.conversationId());
  }
}
