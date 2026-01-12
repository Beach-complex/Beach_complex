package com.beachcheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "user_favorites",
    // 중복 방지: 한 사용자가 같은 해수욕장을 여러 번 찜할 수 없음
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "beach_id"}))
public class UserFavorite {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "beach_id", nullable = false)
  private Beach beach;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  public void onCreate() {
    createdAt = Instant.now();
  }

  // Constructors
  public UserFavorite() {}

  public UserFavorite(User user, Beach beach) {
    this.user = user;
    this.beach = beach;
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Beach getBeach() {
    return beach;
  }

  public void setBeach(Beach beach) {
    this.beach = beach;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
