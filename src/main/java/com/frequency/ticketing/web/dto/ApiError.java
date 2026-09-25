package com.frequency.ticketing.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * The single, consistent error shape returned for every rejected request (constitution
 * Principle I). No stack traces or internal identifiers are ever included.
 */
public record ApiError(
    String code, String message, Instant timestamp, String path, List<ApiFieldError> fieldErrors) {

  public enum Code {
    VALIDATION_FAILED,
    TICKET_NOT_FOUND,
    USER_NOT_FOUND,
    INVALID_TRANSITION,
    TICKET_CONFLICT,
    INVALID_CREDENTIALS,
    UNAUTHENTICATED,
    FORBIDDEN,
    AI_SERVICE_UNAVAILABLE,
    CHATBOT_CONVERSATION_NOT_FOUND
  }

  public static ApiError of(Code code, String message, String path) {
    return new ApiError(code.name(), message, Instant.now(), path, List.of());
  }

  public static ApiError ofValidation(String message, String path, List<ApiFieldError> fieldErrors) {
    return new ApiError(Code.VALIDATION_FAILED.name(), message, Instant.now(), path, fieldErrors);
  }
}
