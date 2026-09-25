package com.frequency.ticketing.domain.exception;

/** Thrown when a reassignment's target user does not hold the {@code SUPPORT} role. */
public class InvalidAssignmentException extends RuntimeException {

  public InvalidAssignmentException(String message) {
    super(message);
  }
}
