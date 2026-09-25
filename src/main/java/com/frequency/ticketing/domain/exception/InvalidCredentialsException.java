package com.frequency.ticketing.domain.exception;

/**
 * Thrown for a login attempt with an incorrect password or an unregistered email. Callers MUST
 * use the same generic message either way (spec 005 FR-003) — never reveal which was wrong.
 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid email or password");
  }
}
