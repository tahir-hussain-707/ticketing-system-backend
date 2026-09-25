package com.frequency.ticketing.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.web.dto.CommentCreateRequest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Bean Validation constraints on the request DTOs directly (data-model.md
 * Validation Rules Summary) — no Spring context needed.
 */
class BeanValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Test
  void blankTitleViolatesNotBlank() {
    var request = new TicketCreateRequest("", "description", TicketPriority.LOW);

    Set<ConstraintViolation<TicketCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("title"));
  }

  @Test
  void oversizedTitleViolatesSize() {
    var request = new TicketCreateRequest("x".repeat(201), "description", TicketPriority.LOW);

    Set<ConstraintViolation<TicketCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("title"));
  }

  @Test
  void titleAtMaxLengthIsValid() {
    var request = new TicketCreateRequest("x".repeat(200), "description", TicketPriority.LOW);

    Set<ConstraintViolation<TicketCreateRequest>> violations = validator.validate(request);
    assertThat(violations).isEmpty();
  }

  @Test
  void blankDescriptionViolatesNotBlank() {
    var request = new TicketCreateRequest("title", "", TicketPriority.LOW);

    Set<ConstraintViolation<TicketCreateRequest>> violations = validator.validate(request);

    assertThat(violations)
        .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("description"));
  }

  @Test
  void missingPriorityIsValidCreateRequestDefaultsLater() {
    // priority has no @NotNull on TicketCreateRequest — TicketService defaults it to MEDIUM.
    var request = new TicketCreateRequest("title", "description", null);

    Set<ConstraintViolation<TicketCreateRequest>> violations = validator.validate(request);
    assertThat(violations).isEmpty();
  }

  @Test
  void blankCommentContentViolatesNotBlank() {
    var request = new CommentCreateRequest("");

    Set<ConstraintViolation<CommentCreateRequest>> violations = validator.validate(request);

    assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("content"));
  }
}
