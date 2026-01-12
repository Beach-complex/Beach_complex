package com.beachcheck.controller;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.service.UserFavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/favorites")
public class UserFavoriteController {

    private final UserFavoriteService favoriteService;

    public UserFavoriteController(UserFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * 내 찜 목록 조회
     * GET /api/favorites
     */
    @GetMapping
    public ResponseEntity<List<Beach>> getMyFavorites(
            @AuthenticationPrincipal User user
    ) {
        List<Beach> favorites = favoriteService.getFavoriteBeaches(user);
        return ResponseEntity.ok(favorites);
    }

    /**
     * 찜 추가
     * POST /api/favorites/{beachId}
     */
    @PostMapping("/{beachId}")
    public ResponseEntity<Map<String, Object>> addFavorite(
            @AuthenticationPrincipal User user,
            @PathVariable UUID beachId
    ) {
        favoriteService.addFavorite(user, beachId);
        return ResponseEntity.ok(Map.of(
                "message", "찜 목록에 추가되었습니다.",
                "isFavorite", true
        ));
    }

    /**
     * 찜 제거
     * DELETE /api/favorites/{beachId}
     */
    @DeleteMapping("/{beachId}")
    public ResponseEntity<Map<String, Object>> removeFavorite(
            @AuthenticationPrincipal User user,
            @PathVariable UUID beachId
    ) {
        favoriteService.removeFavorite(user, beachId);
        return ResponseEntity.ok(Map.of(
                "message", "찜 목록에서 제거되었습니다.",
                "isFavorite", false
        ));
    }

    /**
     * 찜 토글 (추가/제거)
     * PUT /api/favorites/{beachId}/toggle
     */
    @PutMapping("/{beachId}/toggle")
    public ResponseEntity<Map<String, Object>> toggleFavorite(
            @AuthenticationPrincipal User user,
            @PathVariable UUID beachId
    ) {
        boolean isFavorite = favoriteService.toggleFavorite(user, beachId);
        return ResponseEntity.ok(Map.of(
                "message", isFavorite ? "찜 목록에 추가되었습니다." : "찜 목록에서 제거되었습니다.",
                "isFavorite", isFavorite
        ));
    }

    /**
     * 특정 해수욕장 찜 여부 확인
     * GET /api/favorites/{beachId}/check
     */
    @GetMapping("/{beachId}/check")
    public ResponseEntity<Map<String, Boolean>> checkFavorite(
            @AuthenticationPrincipal User user,
            @PathVariable UUID beachId
    ) {
        boolean isFavorite = favoriteService.isFavorite(user, beachId);
        return ResponseEntity.ok(Map.of("isFavorite", isFavorite));
    }
}
