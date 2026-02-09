package com.beachcheck.util;

import com.beachcheck.config.JwtProperties;
import com.beachcheck.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {

  private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);
  private final SecretKey key;
  private final long accessTokenExpiration;
  private final long refreshTokenExpiration;

  public JwtUtils(JwtProperties jwtProperties) {
    this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpiration = jwtProperties.getAccessTokenExpiration();
    this.refreshTokenExpiration = jwtProperties.getRefreshTokenExpiration();
  }

  public String generateAccessToken(User user) {
    // TODO(OAuth): OAuth 사용자 식별/권한 매핑을 위한 클레임(issuer, provider, providerUserId 등) 정책 확정.
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("type", "access")
        .claim("email", user.getEmail()) // 페이로드 사용자 정의 속성 추가
        .claim("role", user.getRole().name())
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(key)
        .compact();
  }

  public String generateRefreshToken(User user) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("type", "refresh")
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(key)
        .compact();
  }

  public UUID getUserIdFromToken(String token) {
    Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

    return UUID.fromString(claims.getSubject());
  }

  public boolean validateToken(String token) {
    try {
      Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
      return true;
    } catch (SecurityException | MalformedJwtException e) {
      // Invalid JWT signature
      log.info("Invalid JWT signature: {}", e.getMessage());
    } catch (ExpiredJwtException e) {
      // Expired JWT token
      log.info("Expired token: {}", e.getMessage());
    } catch (UnsupportedJwtException e) {
      // Unsupported JWT token
      log.info("Unsupported token: {}", e.getMessage());
    } catch (IllegalArgumentException e) {
      // JWT claims string is empty
      log.info("JWT claims string is empty: {}", e.getMessage());
    }
    return false;
  }

  public boolean isAccessToken(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      String tokenType = claims.get("type", String.class);
      // Backward compatibility: tokens without "type" are treated as access tokens.
      return tokenType == null || "access".equals(tokenType);
    } catch (SecurityException
        | MalformedJwtException
        | ExpiredJwtException
        | UnsupportedJwtException
        | IllegalArgumentException e) {
      return false;
    }
  }

  public long getAccessTokenExpiration() {
    return accessTokenExpiration / 1000; // Convert to seconds
  }

  public long getRefreshTokenExpirationMillis() {
    return refreshTokenExpiration;
  }
}
