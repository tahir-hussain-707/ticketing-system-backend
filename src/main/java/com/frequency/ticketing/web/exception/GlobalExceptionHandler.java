package com.frequency.ticketing.web.exception;

import com.frequency.ticketing.domain.exception.AiServiceUnavailableException;
import com.frequency.ticketing.domain.exception.ChatbotConversationNotFoundException;
import com.frequency.ticketing.domain.exception.ForbiddenActionException;
import com.frequency.ticketing.domain.exception.InvalidAssignmentException;
import com.frequency.ticketing.domain.exception.InvalidCredentialsException;
import com.frequency.ticketing.domain.exception.InvalidTransitionException;
import com.frequency.ticketing.domain.exception.TicketConflictException;
import com.frequency.ticketing.domain.exception.TicketNotFoundException;
import com.frequency.ticketing.domain.exception.UserNotFoundException;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.ApiFieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every rejection path to the single consistent {@link ApiError} shape (constitution
 * Principle I). No stack traces or internal identifiers are ever returned to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ApiFieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiFieldError(fe.getField(), message(fe)))
            .toList();
    return ResponseEntity.badRequest()
        .body(ApiError.ofValidation("Request validation failed", request.getRequestURI(), fieldErrors));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadableBody(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            ApiError.of(
                ApiError.Code.VALIDATION_FAILED,
                "Request body is malformed or contains an invalid value",
                request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            ApiError.of(
                ApiError.Code.VALIDATION_FAILED,
                "Parameter '" + ex.getName() + "' has an invalid value: " + ex.getValue(),
                request.getRequestURI()));
  }

  @ExceptionHandler(TicketNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(
      TicketNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiError.of(ApiError.Code.TICKET_NOT_FOUND, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(InvalidTransitionException.class)
  public ResponseEntity<ApiError> handleInvalidTransition(
      InvalidTransitionException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiError.of(ApiError.Code.INVALID_TRANSITION, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler({TicketConflictException.class, OptimisticLockingFailureException.class})
  public ResponseEntity<ApiError> handleConflict(Exception ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            ApiError.of(
                ApiError.Code.TICKET_CONFLICT,
                "Ticket was modified concurrently; reload and retry.",
                request.getRequestURI()));
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ApiError> handleUserNotFound(
      UserNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiError.of(ApiError.Code.USER_NOT_FOUND, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(InvalidAssignmentException.class)
  public ResponseEntity<ApiError> handleInvalidAssignment(
      InvalidAssignmentException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(ApiError.of(ApiError.Code.VALIDATION_FAILED, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ApiError> handleInvalidCredentials(
      InvalidCredentialsException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiError.of(ApiError.Code.INVALID_CREDENTIALS, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(ForbiddenActionException.class)
  public ResponseEntity<ApiError> handleForbidden(
      ForbiddenActionException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiError.of(ApiError.Code.FORBIDDEN, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(ChatbotConversationNotFoundException.class)
  public ResponseEntity<ApiError> handleConversationNotFound(
      ChatbotConversationNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            ApiError.of(
                ApiError.Code.CHATBOT_CONVERSATION_NOT_FOUND, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(AiServiceUnavailableException.class)
  public ResponseEntity<ApiError> handleAiServiceUnavailable(
      AiServiceUnavailableException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiError.of(
                ApiError.Code.AI_SERVICE_UNAVAILABLE,
                "The resolution assistant is temporarily unavailable; please try again shortly.",
                request.getRequestURI()));
  }

  private static String message(FieldError fe) {
    return fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid value";
  }
}
