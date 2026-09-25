package com.frequency.ticketing.domain.chatbot;

import java.util.List;
import java.util.UUID;

/** Result of one chatbot turn, independent of the web-layer DTO shape. */
public record ChatbotResult(
    UUID conversationId, String responseText, List<UUID> sourceTicketIds, boolean confidentMatch) {}
