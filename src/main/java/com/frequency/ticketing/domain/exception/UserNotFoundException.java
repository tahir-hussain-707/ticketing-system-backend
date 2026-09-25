package com.frequency.ticketing.domain.exception;

import java.util.UUID;

/** Thrown when a request references a user id that does not exist (e.g. a reassignment target). */
public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(UUID userId) {
    super("User not found: " + userId);
  }
}
