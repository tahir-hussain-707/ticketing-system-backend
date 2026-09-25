package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ChatbotQueryRequest;
import com.frequency.ticketing.web.dto.ChatbotTurnResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * FR-036 Edge Cases (spec 005): after a conversation is explicitly ended, a subsequent message
 * with no {@code conversationId} starts a brand-new conversation rather than resuming it.
 */
class ChatbotConversationLifecycleIntegrationTest extends AbstractIntegrationTest {

  @Test
  void newQueryAfterExplicitEndStartsAFreshConversation() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    ChatbotTurnResponse first =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(new ChatbotQueryRequest("about to end this one", null), auth),
                ChatbotTurnResponse.class)
            .getBody();

    restTemplate.exchange(
        baseUrl("/chatbot/conversations/" + first.conversationId() + "/end"),
        HttpMethod.POST,
        authEntity(auth),
        Void.class);

    ChatbotTurnResponse afterEnd =
        restTemplate
            .exchange(
                baseUrl("/chatbot/messages"),
                HttpMethod.POST,
                authEntity(new ChatbotQueryRequest("a new question after ending", null), auth),
                ChatbotTurnResponse.class)
            .getBody();

    assertThat(afterEnd.conversationId()).isNotEqualTo(first.conversationId());
  }
}
