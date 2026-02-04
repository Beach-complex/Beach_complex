package com.beachcheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "users")
public class User implements UserDetails {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  // TODO(OAuth): OAuth provider는 email이 없거나(또는 변경될 수) 있으므로, 식별자(provider+sub) 도입 및 email UNIQUE 정책
  // 재검토.
  @Column(nullable = false, unique = true, columnDefinition = "VARCHAR")
  private String email;

  // TODO(OAuth): OAuth 계정은 password를 저장하지 않을 수 있으므로, nullable/더미값 정책 또는 인증정보 테이블 분리 필요.
  @Column(nullable = false, columnDefinition = "VARCHAR")
  private String password;

  @Column(nullable = false, columnDefinition = "VARCHAR(100)")
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "VARCHAR(20)")
  private Role role = Role.USER;

  // TODO(OAuth): OAuth 가입자의 enabled 기본값 및 이메일 인증 정책(스킵/대체)을 provider별로 명확히 분리.
  @Column(nullable = false)
  private Boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "timezone", columnDefinition = "VARCHAR")
  private String timezone;

  @Enumerated(EnumType.STRING)
  // TODO(OAuth): OAuth 도입 시 provider별 필수 필드/정책(비밀번호, enabled, email-verified 등) 분리.
  private AuthProvider authProvider; // Email, Google, Kakao

  @Column(name = "fcm_token", length = 500)
  private String fcmToken;

  // 알림 수신 동의 여부
  @Column(name = "notification_enabled", nullable = false)
  private Boolean notificationEnabled = true; // opt-out 방식

  @PrePersist
  public void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  public void onUpdate() {
    updatedAt = Instant.now();
  }

  // UserDetails 인터페이스 구현
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public AuthProvider getAuthProvider() {
    return authProvider;
  }

  public void setAuthProvider(AuthProvider authProvider) {
    this.authProvider = authProvider;
  }

  public String getFcmToken() {
    return fcmToken;
  }

  public void setFcmToken(String fcmToken) {
    this.fcmToken = fcmToken;
  }

  public Boolean getNotificationEnabled() {
    return notificationEnabled;
  }

  public void setNotificationEnabled(Boolean notificationEnabled) {
    this.notificationEnabled = notificationEnabled;
  }

  public enum Role {
    USER,
    ADMIN
  }

  public enum AuthProvider {
    EMAIL,
    GOOGLE,
    KAKAO
  }
}
