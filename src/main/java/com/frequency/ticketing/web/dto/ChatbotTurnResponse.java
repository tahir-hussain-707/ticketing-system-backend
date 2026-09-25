package com.frequency.ticketing.web.dto;

import java.util.List;
import java.util.UUID;

public record ChatbotTurnResponse(
    UUID conversationId, String responseText, List<UUID> sourceTicketIds, boolean confidentMatch) {

  public static ChatbotTurnResponse from(
      com.frequency.ticketing.domain.chatbot.ChatbotResult result) {
    return new ChatbotTurnResponse(
        result.conversationId(),
        result.responseText(),
        result.sourceTicketIds(),
        result.confidentMatch());
  }
}
