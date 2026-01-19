package com.beachcheck.controller;

import com.beachcheck.domain.User;
import com.beachcheck.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

  private final UserRepository userRepository;

  public NotificationController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * FCM 토큰 저장/업데이트
   *
   * <p>Why: 프론트엔드에서 받은 FCM 토큰을 사용자 정보에 저장하여 푸시 알림 발송에 사용
   *
   * <p>Policy: 로그인한 사용자만 호출 가능, FCM 토큰은 로그인 시마다 갱신됨 (브라우저/기기 변경 대응)
   *
   * <p>Contract(Input):
   *
   * <ul>
   *   <li>fcmToken: NULL 불가, 빈 문자열 불가
   * </ul>
   *
   * <p>Contract(Output):
   *
   * <ul>
   *   <li>200 OK: 토큰 저장 성공
   *   <li>404 NOT FOUND: 사용자를 찾을 수 없음
   * </ul>
   */
  @PostMapping("/fcm-token")
  public ResponseEntity<FcmTokenResponse> saveFcmToken(
      @AuthenticationPrincipal User user, @Valid @RequestBody FcmTokenRequest request) {

    User dbUser =
        userRepository
            .findById(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    dbUser.setFcmToken(request.fcmToken());
    userRepository.save(dbUser);

    return ResponseEntity.ok(new FcmTokenResponse("FCM 토큰이 저장되었습니다."));
  }

  /**
   * 알림 수신 설정 변경
   *
   * <p>Why: 사용자가 알림 수신 여부를 직접 제어할 수 있도록 함
   *
   * <p>Policy: 로그인한 사용자만 호출 가능, opt-out 방식 (기본값 true)
   *
   * <p>Contract(Input):
   *
   * <ul>
   *   <li>enabled: NULL 불가
   * </ul>
   *
   * <p>Contract(Output):
   *
   * <ul>
   *   <li>200 OK: 설정 변경 성공
   *   <li>404 NOT FOUND: 사용자를 찾을 수 없음
   * </ul>
   */
  @PutMapping("/settings")
  public ResponseEntity<NotificationSettingsResponse> updateNotificationSettings(
      @AuthenticationPrincipal User user, @Valid @RequestBody NotificationSettingsRequest request) {

    User dbUser =
        userRepository
            .findById(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    dbUser.setNotificationEnabled(request.enabled());
    userRepository.save(dbUser);

    return ResponseEntity.ok(
        new NotificationSettingsResponse("알림 설정이 변경되었습니다.", dbUser.getNotificationEnabled()));
  }

  // DTOs
  public record FcmTokenRequest(@NotBlank(message = "FCM 토큰은 필수입니다.") String fcmToken) {}

  public record FcmTokenResponse(String message) {}

  public record NotificationSettingsRequest(Boolean enabled) {}

  public record NotificationSettingsResponse(String message, Boolean enabled) {}
}
