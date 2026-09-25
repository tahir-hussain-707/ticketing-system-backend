package com.frequency.ticketing.config;

import com.frequency.ticketing.domain.user.User;
import com.frequency.ticketing.domain.user.UserRole;
import com.frequency.ticketing.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seed users for local development only — never a Flyway migration, since a migration runs
 * identically in every environment including a future production one (constitution Principle V;
 * research.md "Seed users"). Real account provisioning is out of scope (spec 005 FR-005).
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public DevDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(String... args) {
    seed("admin1@example.test", "Ada Admin", UserRole.ADMIN);
    seed("support1@example.test", "Sam Support", UserRole.SUPPORT);
    seed("support2@example.test", "Sasha Support", UserRole.SUPPORT);
    seed("general1@example.test", "Gina General", UserRole.GENERAL);
    seed("general2@example.test", "Greg General", UserRole.GENERAL);
  }

  private void seed(String email, String name, UserRole role) {
    if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
      return;
    }
    userRepository.save(new User(name, email, passwordEncoder.encode("dev-password"), role));
  }
}
