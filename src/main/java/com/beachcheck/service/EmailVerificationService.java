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

/**
 * Why: 이메일 인증 토큰의 생성, 전송, 검증, 재전송을 관리해 계정 신뢰성을 확보하기 위해.
 *
 * <p>Policy: 토큰은 SHA-256 해시로 저장되며, 만료/재전송 쿨다운 정책을 적용한다.
 *
 * <p>Contract(Input/Output): 메서드별로 토큰 생성, 검증, 재전송 기능을 제공한다.
 */
@Service
@Transactional
public class EmailVerificationService {

  private final EmailVerificationTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final EmailSender emailSender;

  private final String baseUrl;
  private final long tokenExpirationMinutes;
  private final long resendCooldownMinutes;
  private final String fromAddress;

  public EmailVerificationService(
      EmailVerificationTokenRepository tokenRepository,
      UserRepository userRepository,
      EmailSender emailSender,
      @Value("${app.email-verification.base-url}") String baseUrl,
      @Value("${app.email-verification.token-expiration-minutes:30}") long tokenExpirationMinutes,
      @Value("${app.email-verification.resend-cooldown-minutes:3}") long resendCooldownMinutes,
      @Value("${app.email-verification.from-address:}") String fromAddress) {
    this.tokenRepository = tokenRepository;
    this.userRepository = userRepository;
    this.emailSender = emailSender;
    this.baseUrl = baseUrl;
    this.tokenExpirationMinutes = tokenExpirationMinutes;
    this.resendCooldownMinutes = resendCooldownMinutes;
    this.fromAddress = fromAddress;
  }

  /**
   * Why: 이메일 인증 토큰을 생성해 사용자에게 전송한다.
   *
   * <p>Policy: 토큰은 SHA-256 해시로 저장되며, 만료 정책이 적용된다.
   *
   * <p>Contract(Input): user는 인증 이메일을 받을 대상 사용자이다.
   *
   * <p>Contract(Output): 인증 이메일이 전송된다.
   */
  public void sendVerification(User user) {
    // TODO(OAuth): OAuth 가입자는 email verification 정책을 스킵하거나 대체 흐름 적용.
    String rawToken = createToken(user);
    sendVerificationEmail(user.getEmail(), rawToken);
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
            .orElseThrow(() -> new IllegalArgumentException("유효 하지 않은 인증 토큰입니다."));

    if (token.isUsed()) {
      throw new IllegalStateException("이미 사용된 인증 토큰입니다.");
    }
    if (token.isExpired()) {
      throw new IllegalStateException("만료된 인증 토큰입니다.");
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
              sendVerificationEmail(user.getEmail(), rawToken);
            });
  }

  private String createToken(User user) {
    String rawToken = UUID.randomUUID().toString();
    EmailVerificationToken token =
        new EmailVerificationToken(
            user,
            hashToken(rawToken),
            Instant.now().plus(tokenExpirationMinutes, ChronoUnit.MINUTES));
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
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", ex);
    }
  }

  /**
   * Why: 인증 이메일 재전송 시 쿨다운 정책을 적용해 남용을 방지한다.
   *
   * <p>Contract(Input): userId는 재전송 요청 사용자의 ID이다.
   *
   * <p>Contract(Output): 쿨다운 위반 시 예외를 던진다
   */
  private void enforceCooldown(UUID userId) {
    tokenRepository
        .findTopByUserIdOrderByCreatedAtDesc(userId)
        .ifPresent(
            last -> {
              Instant limit = last.getCreatedAt().plus(resendCooldownMinutes, ChronoUnit.MINUTES);
              if (Instant.now().isBefore(limit)) {
                throw new IllegalStateException("인증 이메일이 최근에 발송되었습니다. 잠시 후 다시 시도해주세요.");
              }
            });
  }

  private void sendVerificationEmail(String to, String token) {
    String link = baseUrl + "?token=" + token;
    String subject = "이메일 인증";
    String body =
        """
        아래 링크를 클릭하여 이메일을 인증해주세요:

        %s

        이 링크는 %d분 후에 만료됩니다.
        """
            .formatted(link, tokenExpirationMinutes);

    emailSender.send(fromAddress, to, subject, body);
  }
}
