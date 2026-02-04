package com.beachcheck.service;

import com.beachcheck.domain.RefreshToken;
import com.beachcheck.domain.User;
import com.beachcheck.dto.auth.request.LogInRequestDto;
import com.beachcheck.dto.auth.request.SignUpRequestDto;
import com.beachcheck.dto.auth.response.AuthResponseDto;
import com.beachcheck.dto.auth.response.TokenResponseDto;
import com.beachcheck.dto.auth.response.UserResponseDto;
import com.beachcheck.exception.ApiException;
import com.beachcheck.exception.ErrorCode;
import com.beachcheck.repository.RefreshTokenRepository;
import com.beachcheck.repository.UserRepository;
import com.beachcheck.util.JwtUtils;
import jakarta.persistence.EntityNotFoundException;
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

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtUtils jwtUtils,
      EmailVerificationService emailVerificationService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtils = jwtUtils;
    this.emailVerificationService = emailVerificationService;
  }

  @Transactional
  public UserResponseDto signUp(SignUpRequestDto request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new IllegalArgumentException("Email already exists");
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
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new BadCredentialsException("Invalid email or password");
    }

    if (!user.getEnabled()) {
      throw new IllegalStateException("Account is disabled");
    }

    // 기존 refresh token 무효화
    refreshTokenRepository.revokeAllByUser(user);

    // 새 토큰 생성
    String accessToken = jwtUtils.generateAccessToken(user);
    String refreshTokenStr = jwtUtils.generateRefreshToken(user);

    // Refresh token 세팅 및 저장
    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUser(user);
    refreshToken.setToken(refreshTokenStr);
    refreshToken.setExpiresAt(Instant.now().plusMillis(30L * 24 * 60 * 60 * 1000)); // 30일
    refreshTokenRepository.save(refreshToken);

    // 마지막 로그인 시각 업데이트
    user.setLastLoginAt(Instant.now());
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
            .orElseThrow(() -> new EntityNotFoundException("Refresh token not found"));

    refreshToken.setRevoked(true);
    refreshTokenRepository.save(refreshToken);
  }

  @Transactional
  public TokenResponseDto refresh(String refreshTokenStr) {
    RefreshToken refreshToken =
        refreshTokenRepository
            .findByToken(refreshTokenStr)
            .orElseThrow(() -> invalidRefreshToken());

    if (Boolean.TRUE.equals(refreshToken.getRevoked()) || refreshToken.isExpired()) {
      throw invalidRefreshToken();
    }

    User user = refreshToken.getUser();
    String newAccessToken = jwtUtils.generateAccessToken(user);

    return TokenResponseDto.of(newAccessToken, jwtUtils.getAccessTokenExpiration());
  }

  private ApiException invalidRefreshToken() {
    return new ApiException(ErrorCode.INVALID_REQUEST, "Invalid refresh token");
  }

  public UserResponseDto getCurrentUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
    return UserResponseDto.from(user);
  }
}
