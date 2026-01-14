package com.beachcheck.service;

import com.beachcheck.domain.EmailVerificationToken;
import com.beachcheck.domain.User;
import com.beachcheck.repository.EmailVerificationTokenRepository;
import com.beachcheck.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmailVerificationService {

  private final EmailVerificationTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final EmailSenderService emailSenderService;

  private final String baseUrl;
  private final long tokenExpirationMinutes;
  private final long resendCooldownMinutes;
  private final String fromAddress;

  public EmailVerificationService(
      EmailVerificationTokenRepository tokenRepository,
      UserRepository userRepository,
      EmailSenderService emailSenderService,
      @Value("${app.email-verification.base-url}") String baseUrl,
      @Value("${app.email-verification.token-expiration-minutes:30}") long tokenExpirationMinutes,
      @Value("${app.email-verification.resend-cooldown-minutes:3}") long resendCooldownMinutes,
      @Value("${app.email-verification.from-address:}") String fromAddress) {
    this.tokenRepository = tokenRepository;
    this.userRepository = userRepository;
    this.emailSenderService = emailSenderService;
    this.baseUrl = baseUrl;
    this.tokenExpirationMinutes = tokenExpirationMinutes;
    this.resendCooldownMinutes = resendCooldownMinutes;
    this.fromAddress = fromAddress;
  }

  public void sendVerification(User user) {
    String rawToken = createToken(user);
    sendEmail(user.getEmail(), rawToken);
  }

  /**
   * Why: 이메일 인증 토큰의 1회성/만료 정책을 강제해 계정 신뢰성을 확보한다.
   *
   * <p>Policy: 토큰은 SHA-256 해시로 저장되며, used/expired 토큰은 재사용 불가하다.
   *
   * <p>Contract(Input): tokenValue는 메일 링크의 raw 토큰 문자열이다.
   *
   * <p>Contract(Output): 유효 토큰이면 user.enabled=true로 전환된다.
   */
  public void verifyToken(String tokenValue) {
    String hashed = hashToken(tokenValue);
    EmailVerificationToken token =
        tokenRepository
            .findByToken(hashed)
            .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

    if (token.isUsed()) {
      throw new IllegalStateException("Verification token already used");
    }
    if (token.isExpired()) {
      throw new IllegalStateException("Verification token expired");
    }

    User user = token.getUser();
    user.setEnabled(true);
    userRepository.save(user);

    token.markUsed();
    tokenRepository.save(token);
  }

  public void resendVerification(String email) {
    userRepository
        .findByEmail(email)
        .ifPresent(
            user -> {
              if (Boolean.TRUE.equals(user.getEnabled())) {
                return;
              }

              enforceCooldown(user.getId());
              tokenRepository.markAllUnusedAsUsed(user.getId(), Instant.now());

              String rawToken = createToken(user);
              sendEmail(user.getEmail(), rawToken);
            });
  }

  private String createToken(User user) {
    EmailVerificationToken token = new EmailVerificationToken();
    String rawToken = UUID.randomUUID().toString();
    token.setUser(user);
    token.setToken(hashToken(rawToken));
    token.setExpiresAt(Instant.now().plus(tokenExpirationMinutes, ChronoUnit.MINUTES));
    tokenRepository.save(token);
    return rawToken;
  }

  private String hashToken(String token) {
    // DB 유출 시 원문 토큰 노출을 막기 위해 해시로 저장한다.
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hashed.length * 2);
      for (byte b : hashed) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }

  private void enforceCooldown(UUID userId) {
    tokenRepository
        .findTopByUserIdOrderByCreatedAtDesc(userId)
        .ifPresent(
            last -> {
              Instant limit = last.getCreatedAt().plus(resendCooldownMinutes, ChronoUnit.MINUTES);
              if (Instant.now().isBefore(limit)) {
                throw new IllegalStateException("Verification email recently sent");
              }
            });
  }

  private void sendEmail(String to, String token) {
    String link = baseUrl + "?token=" + token;
    String subject = "Email verification";
    String body =
        "Please verify your email by clicking the link below:\n\n"
            + link
            + "\n\nThis link expires in "
            + tokenExpirationMinutes
            + " minutes.";
    emailSenderService.send(fromAddress, to, subject, body);
  }
}
