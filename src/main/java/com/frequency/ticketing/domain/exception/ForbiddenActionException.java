package com.frequency.ticketing.domain.exception;

/**
 * Thrown when an authenticated caller is not permitted to perform a specific, data-dependent
 * action (spec 005: view authorization FR-037, comment authorization FR-014).
 */
public class ForbiddenActionException extends RuntimeException {

  public ForbiddenActionException(String message) {
    super(message);
  }
}
