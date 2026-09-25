package com.frequency.ticketing.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A person who can log in (spec 005 FR-002). {@code passwordHash} MUST NEVER be exposed by any
 * response DTO or log statement (SC-005) — it deliberately has no getter used outside this class.
 */
@Entity
@Table(name = "users")
public class User {

  @Id private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 200)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {
    // JPA
  }

  public User(String name, String email, String passwordHash, UserRole role) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserRole getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
