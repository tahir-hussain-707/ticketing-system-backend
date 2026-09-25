package com.frequency.ticketing.domain.exception;

/**
 * Thrown when the embedding or response-generation call fails (timeout, non-2xx, malformed
 * response) — mapped to {@code 503} rather than an empty or silently wrong response (spec 005
 * FR-030).
 */
public class AiServiceUnavailableException extends RuntimeException {

  public AiServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
