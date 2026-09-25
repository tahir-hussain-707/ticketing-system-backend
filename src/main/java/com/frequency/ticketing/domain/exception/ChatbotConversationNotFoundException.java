package com.frequency.ticketing.domain.exception;

import java.util.UUID;

/**
 * Thrown when a supplied {@code conversationId} does not exist, does not belong to the caller, or
 * has already been explicitly ended (spec 005 FR-036, contracts/chatbot-api.yaml).
 */
public class ChatbotConversationNotFoundException extends RuntimeException {

  public ChatbotConversationNotFoundException(UUID conversationId) {
    super("Chatbot conversation not found: " + conversationId);
  }
}
