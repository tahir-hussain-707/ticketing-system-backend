package com.frequency.ticketing.domain.user;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security's view of an authenticated {@link User} — carries the id and role
 * {@link CurrentUser} needs, alongside the {@code ROLE_<role>} authority the filter chain's
 * {@code hasRole(...)} checks rely on (spec 005 FR-013).
 */
public class AppUserPrincipal implements UserDetails {

  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final UserRole role;

  public AppUserPrincipal(User user) {
    this.id = user.getId();
    this.email = user.getEmail();
    this.passwordHash = user.getPasswordHash();
    this.role = user.getRole();
  }

  public UUID getId() {
    return id;
  }

  public UserRole getRole() {
    return role;
  }

  @Override
  public List<GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }
}
