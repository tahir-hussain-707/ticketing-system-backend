package com.frequency.ticketing.domain.auth;

import com.frequency.ticketing.domain.exception.InvalidCredentialsException;
import com.frequency.ticketing.domain.user.User;
import com.frequency.ticketing.repository.UserRepository;
import com.frequency.ticketing.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Login establishes a session (FR-001), logout ends it (FR-008). Never distinguishes a wrong
 * password from an unregistered email in what it throws (FR-003).
 */
@Service
public class AuthenticationService {

  private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final UserRepository userRepository;

  public AuthenticationService(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      UserRepository userRepository) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.userRepository = userRepository;
  }

  public LoginResponse login(
      String email, String password, HttpServletRequest request, HttpServletResponse response) {
    Authentication authenticated;
    try {
      authenticated =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(email, password));
    } catch (BadCredentialsException | UsernameNotFoundException ex) {
      // Never log the password. The email alone doesn't reveal to a log reader whether the
      // rejection was "wrong password" vs. "unregistered" — same as the client-facing error.
      log.info("login_attempt email={} outcome=rejected", email);
      throw new InvalidCredentialsException();
    }

    // Regenerate the session id on every successful login (before the new context is bound to
    // it) so a pre-authentication session id an attacker may have fixed on the victim's browser
    // is never the one that ends up authenticated — prevents session fixation.
    HttpSession existingSession = request.getSession(false);
    if (existingSession != null) {
      request.changeSessionId();
    }

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authenticated);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    User user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(InvalidCredentialsException::new);
    log.info("login_attempt userId={} outcome=success", user.getId());
    return new LoginResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    new SecurityContextLogoutHandler().logout(request, response, authentication);
  }
}
