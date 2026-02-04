package com.beachcheck.controller;

import com.beachcheck.domain.User;
import com.beachcheck.dto.auth.request.LogInRequestDto;
import com.beachcheck.dto.auth.request.RefreshTokenRequestDto;
import com.beachcheck.dto.auth.request.ResendVerificationRequestDto;
import com.beachcheck.dto.auth.request.SignUpRequestDto;
import com.beachcheck.dto.auth.response.AuthResponseDto;
import com.beachcheck.dto.auth.response.TokenResponseDto;
import com.beachcheck.dto.auth.response.UserResponseDto;
import com.beachcheck.service.AuthService;
import com.beachcheck.service.EmailVerificationService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

  private final AuthService authService;
  private final EmailVerificationService emailVerificationService;

  public AuthController(
      AuthService authService, EmailVerificationService emailVerificationService) {
    this.authService = authService;
    this.emailVerificationService = emailVerificationService;
  }

  @PostMapping("/signup")
  public ResponseEntity<UserResponseDto> signUp(@Valid @RequestBody SignUpRequestDto request) {
    // TODO(OAuth): OAuth 가입/연동 엔드포인트 추가 시 URL/응답 계약 재정의.
    UserResponseDto user = authService.signUp(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(user);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponseDto> logIn(@Valid @RequestBody LogInRequestDto request) {
    // TODO(OAuth): OAuth 인가 코드/토큰 교환 엔드포인트 추가 및 클라이언트 계약 정리.
    AuthResponseDto response = authService.logIn(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/logout")
  public ResponseEntity<Map<String, String>> logOut(
      @Valid @RequestBody RefreshTokenRequestDto request) {
    authService.logOut(request.refreshToken());
    return ResponseEntity.ok(Map.of("message", "Successfully signed out"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponseDto> refresh(
      @Valid @RequestBody RefreshTokenRequestDto request) {
    TokenResponseDto response = authService.refresh(request.refreshToken());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/me")
  public ResponseEntity<UserResponseDto> getCurrentUser(@AuthenticationPrincipal User user) {
    // TODO(OAuth): OAuth2Login(세션)/JWT를 병행할 경우 principal 매핑(User vs OAuth2User) 및 인증 실패 응답 계약 정리.
    UserResponseDto response = authService.getCurrentUser(user.getId());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/verify-email")
  public ResponseEntity<String> verifyEmailPage(@RequestParam String token) {
    // GET은 확인 페이지 제공, 실제 인증은 POST로 처리해 프리페치 오인증을 막는다.
    String safeToken = HtmlUtils.htmlEscape(token);
    String body =
        """
        <!doctype html>
        <html><body>
        <form id="f" method="post" action="/api/auth/verify-email/confirm">
          <input type="hidden" name="token" value="%s"/>
        </form>
        <script>document.getElementById('f').submit();</script>
        </body></html>
        """
            .formatted(safeToken);

    return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body(body);
  }

  @PostMapping("/verify-email/confirm")
  public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
    emailVerificationService.verifyToken(token);
    return ResponseEntity.ok(Map.of("message", "Email verified"));
  }

  @PostMapping("/resend-verification")
  public ResponseEntity<Map<String, String>> resendVerification(
      @Valid @RequestBody ResendVerificationRequestDto request) {
    emailVerificationService.resendVerification(request.email());
    return ResponseEntity.ok(Map.of("message", "Verification email resent"));
  }
}
