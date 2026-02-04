package com.beachcheck.controller;

import com.beachcheck.domain.User;
import com.beachcheck.dto.beach.BeachDto;
import com.beachcheck.dto.beach.request.BeachSearchRequestDto;
import com.beachcheck.service.BeachService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beaches")
@Validated
public class BeachController {

  // TODO(OAuth): OAuth 도입 시 인증 Principal 타입(User vs OAuth2User) 통일 및 비로그인(null) 처리 정책 재점검.
  private final BeachService beachService;

  public BeachController(BeachService beachService) {
    this.beachService = beachService;
  }

  /**
   * 해변 검색
   *
   * @param request 검색 조건
   * @param user 인증된 사용자 (찜 여부 포함을 위해 사용, 비로그인 시 null)
   * @return 해변 목록 (찜 여부 포함)
   */
  @GetMapping
  public ResponseEntity<List<BeachDto>> searchBeaches(
      @Valid BeachSearchRequestDto request, @AuthenticationPrincipal User user) {
    // DTO 레벨 검증
    request.validateRadiusParams();

    // 반경 검색 요청인 경우
    if (request.hasCompleteRadiusParams()) {
      return ResponseEntity.ok(
          beachService.findNearby(request.lon(), request.lat(), request.radiusKm(), user));
    }

    // 기존 검색 또는 필터링
    if (request.q() != null || request.tag() != null) {
      return ResponseEntity.ok(beachService.search(request.q(), request.tag(), user));
    }

    // 기본: 전체 목록 (캐시됨)
    return ResponseEntity.ok(beachService.findAll(user));
  }

  // TODO: Add POST endpoint once upstream event ingestion is designed.
}
