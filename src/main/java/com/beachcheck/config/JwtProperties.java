package com.beachcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
  // TODO(OAuth): OAuth 토큰 정책 도입 시 만료/리프레시 정책 및 키 관리 전략 재정의.
  private String secret;
  private long accessTokenExpiration;
  private long refreshTokenExpiration;

  // Getters and Setters
  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getAccessTokenExpiration() {
    return accessTokenExpiration;
  }

  public void setAccessTokenExpiration(long accessTokenExpiration) {
    this.accessTokenExpiration = accessTokenExpiration;
  }

  public long getRefreshTokenExpiration() {
    return refreshTokenExpiration;
  }

  public void setRefreshTokenExpiration(long refreshTokenExpiration) {
    this.refreshTokenExpiration = refreshTokenExpiration;
  }
}
