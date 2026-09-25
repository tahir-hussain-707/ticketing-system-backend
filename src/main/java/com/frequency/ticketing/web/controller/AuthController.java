package com.frequency.ticketing.web.controller;

import com.frequency.ticketing.domain.auth.AuthenticationService;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.LoginRequest;
import com.frequency.ticketing.web.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Matches contracts/auth-api.yaml exactly. Login is the only endpoint not requiring auth. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login and logout (spec 005)")
public class AuthController {

  private final AuthenticationService authenticationService;

  public AuthController(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @PostMapping("/login")
  @Operation(summary = "Log in with email and password", description = "FR-001, FR-003, FR-004.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = LoginResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Missing email or password",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Incorrect password or unregistered email",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public LoginResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    return authenticationService.login(
        request.email(), request.password(), httpRequest, httpResponse);
  }

  @PostMapping("/logout")
  @Operation(summary = "Log out, ending the current session", description = "FR-008.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Session ended"),
    @ApiResponse(
        responseCode = "401",
        description = "No authenticated session to end",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    authenticationService.logout(request, response);
    return ResponseEntity.noContent().build();
  }
}
