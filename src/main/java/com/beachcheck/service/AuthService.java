package com.beachcheck.service;

import com.beachcheck.domain.RefreshToken;
import com.beachcheck.domain.User;
import com.beachcheck.dto.auth.request.LogInRequestDto;
import com.beachcheck.dto.auth.request.SignUpRequestDto;
import com.beachcheck.dto.auth.response.AuthResponseDto;
import com.beachcheck.dto.auth.response.TokenResponseDto;
import com.beachcheck.dto.auth.response.UserResponseDto;
import com.beachcheck.repository.RefreshTokenRepository;
import com.beachcheck.repository.UserRepository;
import com.beachcheck.util.JwtUtils;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtils jwtUtils;
  private final EmailVerificationService emailVerificationService;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtUtils jwtUtils,
      EmailVerificationService emailVerificationService,
      Clock clock) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtils = jwtUtils;
    this.emailVerificationService = emailVerificationService;
    this.clock = clock;
  }

  @Transactional
  public UserResponseDto signUp(SignUpRequestDto request) {
    // TODO(OAuth): OAuth 가입/연동 시 이메일/비밀번호 기반 플로우와 분리하고,
    // TODO(OAuth): 중복 계정 병합 정책 및 email verification 정책 재정의.
    if (userRepository.existsByEmail(request.email())) {
      throw new IllegalArgumentException("이미 가입된 이메일입니다.");
    }

    User user = new User();
    user.setEmail(request.email());
    user.setPassword(passwordEncoder.encode(request.password()));
    user.setName(request.name());
    user.setRole(User.Role.USER);
    // 이메일 인증 완료 전에는 로그인 차단을 위해 비활성화 상태로 생성한다.
    user.setEnabled(false);
    user.setAuthProvider(User.AuthProvider.EMAIL);

    User savedUser = userRepository.save(user);
    emailVerificationService.sendVerification(savedUser);
    return UserResponseDto.from(savedUser);
  }

  @Transactional
  public AuthResponseDto logIn(LogInRequestDto request) {
    // TODO(OAuth): OAuth 로그인/첫 가입 플로우 추가 시 기존 이메일 로그인과 서비스/테스트 구조 분리.
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    if (!user.getEnabled()) {
      throw new IllegalStateException("비활성화된 계정입니다.");
    }

    // 기존 refresh token 무효화
    refreshTokenRepository.revokeAllByUser(user);

    // 새 토큰 생성
    String accessToken = jwtUtils.generateAccessToken(user);
    String refreshTokenStr = jwtUtils.generateRefreshToken(user);
    Instant now = Instant.now(clock);

    // Refresh token 세팅 및 저장
    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUser(user);
    refreshToken.setToken(refreshTokenStr);
    refreshToken.setExpiresAt(now.plusMillis(jwtUtils.getRefreshTokenExpirationMillis()));
    refreshTokenRepository.save(refreshToken);

    // 마지막 로그인 시각 업데이트
    user.setLastLoginAt(now);
    userRepository.save(user);

    UserResponseDto userResponse = UserResponseDto.from(user);
    return AuthResponseDto.of(
        accessToken, refreshTokenStr, jwtUtils.getAccessTokenExpiration(), userResponse);
  }

  @Transactional
  public void logOut(String refreshTokenStr) {
    RefreshToken refreshToken =
        refreshTokenRepository
            .findByToken(refreshTokenStr)
            .orElseThrow(() -> new EntityNotFoundException("리프레시 토큰을 찾을 수 없습니다."));

    refreshToken.setRevoked(true);
    refreshTokenRepository.save(refreshToken);
  }

  @Transactional
  public TokenResponseDto refresh(String refreshTokenStr) {
    RefreshToken refreshToken =
        refreshTokenRepository
            .findByToken(refreshTokenStr)
            .orElseThrow(() -> new EntityNotFoundException("리프레시 토큰을 찾을 수 없습니다."));

    if (refreshToken.getRevoked()) {
      throw new IllegalStateException("폐기된 리프레시 토큰입니다.");
    }

    if (refreshToken.isExpired()) {
      throw new IllegalStateException("만료된 리프레시 토큰입니다.");
    }

    User user = refreshToken.getUser();
    String newAccessToken = jwtUtils.generateAccessToken(user);

    return TokenResponseDto.of(newAccessToken, jwtUtils.getAccessTokenExpiration());
  }

  public UserResponseDto getCurrentUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
    return UserResponseDto.from(user);
  }
}
