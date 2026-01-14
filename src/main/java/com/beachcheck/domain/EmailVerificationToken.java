package com.beachcheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

  @Id @UuidGenerator @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, unique = true, length = 255)
  private String token;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @PrePersist
  public void onCreate() {
    createdAt = Instant.now();
  }

  protected EmailVerificationToken() {}

  public EmailVerificationToken(User user, String token, Instant expiresAt) {
    this.user = Objects.requireNonNull(user, "user");
    this.token = Objects.requireNonNull(token, "token");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
  }

  public boolean isExpired() {
    return expiresAt.isBefore(Instant.now());
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public void markUsed() {
    usedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EmailVerificationToken)) {
      return false;
    }
    EmailVerificationToken other = (EmailVerificationToken) o;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
