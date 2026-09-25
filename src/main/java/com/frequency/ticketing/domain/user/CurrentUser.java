package com.frequency.ticketing.domain.user;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated caller's identity from the {@link SecurityContextHolder} (spec 005).
 * {@link SecurityConfig}'s filter chain guarantees an {@link Authentication} is present on every
 * request this component is used from, except the login endpoint itself.
 */
@Component
public class CurrentUser {

  public UUID id() {
    return principal().getId();
  }

  public UserRole role() {
    return principal().getRole();
  }

  private AppUserPrincipal principal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal)) {
      throw new IllegalStateException("No authenticated user in the current security context");
    }
    return (AppUserPrincipal) authentication.getPrincipal();
  }
}
