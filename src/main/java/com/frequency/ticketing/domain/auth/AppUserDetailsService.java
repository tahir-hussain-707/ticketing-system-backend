package com.frequency.ticketing.domain.auth;

import com.frequency.ticketing.domain.user.AppUserPrincipal;
import com.frequency.ticketing.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads a {@link UserDetails} by email for Spring Security's {@code AuthenticationManager}. */
@Service
public class AppUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public AppUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return userRepository
        .findByEmailIgnoreCase(email)
        .map(AppUserPrincipal::new)
        .orElseThrow(() -> new UsernameNotFoundException("No user with that email"));
  }
}
