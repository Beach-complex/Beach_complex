package com.beachcheck.service;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.domain.UserFavorite;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.UserFavoriteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserFavoriteService {

    private final UserFavoriteRepository favoriteRepository;
    private final BeachRepository beachRepository;

    public UserFavoriteService(
            UserFavoriteRepository favoriteRepository,
            BeachRepository beachRepository
    ) {
        this.favoriteRepository = favoriteRepository;
        this.beachRepository = beachRepository;
    }

    /**
     * 찜 추가
     */
    @Transactional
    public UserFavorite addFavorite(User user, UUID beachId) {
        // 이미 찜했는지 확인
        if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
            throw new IllegalStateException("이미 찜한 해수욕장입니다.");
        }

        Beach beach = beachRepository.findById(beachId)
                .orElseThrow(() -> new IllegalArgumentException("해수욕장을 찾을 수 없습니다."));

        UserFavorite favorite = new UserFavorite(user, beach);

        try {
            return favoriteRepository.save(favorite);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 인한 UNIQUE 제약위반 상태이므로 예외 던지기
            throw new IllegalStateException("이미 찜한 해수욕장입니다.");
        }
    }

    /**
     * 찜 제거
     */
    @Transactional
    public void removeFavorite(User user, UUID beachId) {
        favoriteRepository.deleteByUserIdAndBeachId(user.getId(), beachId);
    }

    /**
     * 찜 토글 (추가/제거)
     */
    @Transactional
    public boolean toggleFavorite(User user, UUID beachId) {
        if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
            removeFavorite(user, beachId);
            return false; // 제거됨
        } else {
            addFavorite(user, beachId);
            return true; // 추가됨
        }
    }

    /**
     * 사용자의 찜 목록 조회
     */
    public List<Beach> getFavoriteBeaches(User user) {
        return favoriteRepository.findByUserId(user.getId())
                .stream()
                .map(UserFavorite::getBeach)
                .toList();
    }

    /**
     * 사용자의 찜한 해수욕장 ID 목록 조회 (성능 최적화)
     */
    public Set<UUID> getFavoriteBeachIds(User user) {
        return favoriteRepository.findBeachIdsByUserId(user.getId());
    }

    /**
     * 특정 해수욕장이 찜되어 있는지 확인
     */
    public boolean isFavorite(User user, UUID beachId) {
        return favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId);
    }
}