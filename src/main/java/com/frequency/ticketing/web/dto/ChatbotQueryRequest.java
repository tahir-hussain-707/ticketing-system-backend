package com.frequency.ticketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * {@code conversationId} is optional: omit to start a new conversation or resume the caller's own
 * currently-open one automatically (FR-036); supply to continue a specific conversation.
 */
public record ChatbotQueryRequest(@NotBlank String query, UUID conversationId) {}
