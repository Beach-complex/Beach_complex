package com.beachcheck.service;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.domain.UserFavorite;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.UserFavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static com.beachcheck.fixture.FavoriteTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * Why: 찜하기 비즈니스 로직 및 트랜잭션 동작 검증
 * Policy: Mock 객체 활용 단위 테스트, ArgumentCaptor로 실제 전달값 검증
 * Contract(Input): Repository/BeachRepository 동작은 stub로 대체
 * Contract(Output): 정확한 예외 발생, 객체 필드값 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserFavoriteService P0+P1 비즈니스 로직 테스트")
class UserFavoriteServiceTest {

    @Mock
    private UserFavoriteRepository favoriteRepository;

    @Mock
    private BeachRepository beachRepository;

    @InjectMocks
    private UserFavoriteService favoriteService;

    private User testUser;
    private Beach testBeach;
    private UUID beachId;

    @BeforeEach
    void setUp() {
        testUser = createUser();
        beachId = UUID.randomUUID();
        testBeach = createBeach(beachId);
    }

    /**
     * TC-S01: addFavorite - 정상 추가
     * Why: ArgumentCaptor를 사용하여 실제 저장되는 UserFavorite 객체의 필드값 검증
     * Policy: save() 메서드에 전달되는 객체의 user, beach 필드 검증 (createdAt은 JPA가 처리)
     * Contract(Input): 유효한 user, beachId
     * Contract(Output): UserFavorite 객체가 올바른 필드값으로 생성되어 저장됨
     */
    @Test
    @DisplayName("TC-S01: addFavorite - 정상 추가 시 올바른 객체 저장")
    void addFavorite_정상추가_객체필드검증() {
        // Given
        given(favoriteRepository.existsByUserIdAndBeachId(testUser.getId(), beachId))
                .willReturn(false);
        given(beachRepository.findById(beachId))
                .willReturn(Optional.of(testBeach));

        ArgumentCaptor<UserFavorite> favoriteCaptor = ArgumentCaptor.forClass(UserFavorite.class);
        given(favoriteRepository.save(favoriteCaptor.capture()))
                .willAnswer(invocation -> invocation.getArgument(0));

        // When
        UserFavorite result = favoriteService.addFavorite(testUser, beachId);

        // Then - 반환값 검증
        assertThat(result).isNotNull();

        // Then - Captor로 실제 저장된 객체 검증
        UserFavorite captured = favoriteCaptor.getValue();
        assertThat(captured).isNotNull();
        assertThat(captured.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(captured.getBeach().getId()).isEqualTo(beachId);

        // Then - 핵심 동작: 저장이 수행되었는지 검증
        then(favoriteRepository).should().save(any(UserFavorite.class));
    }

    /**
     * TC-S02: addFavorite - 중복 예외
     * Why: 이미 찜한 해수욕장 재추가 방지
     * Policy: existsByUserIdAndBeachId가 true 반환 시 즉시 예외
     * Contract(Input): 이미 찜한 beachId
     * Contract(Output): IllegalStateException("이미 찜한 해수욕장입니다.")
     */
    @Test
    @DisplayName("TC-S02: addFavorite - 중복 시 예외 발생")
    void addFavorite_중복시_예외발생() {
        // Given
        given(favoriteRepository.existsByUserIdAndBeachId(testUser.getId(), beachId))
                .willReturn(true);

        // When & Then
        assertThatThrownBy(() -> favoriteService.addFavorite(testUser, beachId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 찜한 해수욕장입니다.");

        // save는 호출되지 않아야 함
        then(favoriteRepository).should(never()).save(any());
        then(beachRepository).should(never()).findById(any());
    }

    /**
     * TC-S03: addFavorite - Beach 없음
     * Why: 존재하지 않는 해수욕장에 대한 찜 방지
     * Policy: beachRepository.findById가 empty 반환 시 예외
     * Contract(Input): 존재하지 않는 beachId
     * Contract(Output): IllegalArgumentException
     */
    @Test
    @DisplayName("TC-S03: addFavorite - Beach 없음 시 예외 발생")
    void addFavorite_존재하지않는Beach_예외발생() {
        // Given
        given(favoriteRepository.existsByUserIdAndBeachId(testUser.getId(), beachId))
                .willReturn(false);
        given(beachRepository.findById(beachId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> favoriteService.addFavorite(testUser, beachId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해수욕장을 찾을 수 없습니다");

        // save는 호출되지 않아야 함
        then(favoriteRepository).should(never()).save(any());
    }

    /**
     * TC-S04: addFavorite - 동시성 충돌
     * Why: 동시 요청으로 인한 중복 저장 시도 처리
     * Policy: save() 시 DataIntegrityViolationException 발생 시 IllegalStateException으로 변환
     * Contract(Input): 동시성으로 인한 중복 저장 시도
     * Contract(Output): IllegalStateException
     */
    @Test
    @DisplayName("TC-S04: addFavorite - 동시성 충돌 시 예외 변환")
    void addFavorite_동시성충돌_예외변환() {
        // Given
        given(favoriteRepository.existsByUserIdAndBeachId(testUser.getId(), beachId))
                .willReturn(false);
        given(beachRepository.findById(beachId))
                .willReturn(Optional.of(testBeach));
        given(favoriteRepository.save(any(UserFavorite.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate entry"));

        // When & Then
        assertThatThrownBy(() -> favoriteService.addFavorite(testUser, beachId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 찜한 해수욕장입니다");

        // Then - 핵심 동작: 저장 시도가 있었는지 검증 (예외 발생)
        then(favoriteRepository).should().save(any(UserFavorite.class));
    }

    /**
     * TC-S05: removeFavorite - 정상 제거
     * Why: 찜 제거 시 올바른 userId, beachId가 전달되는지 검증
     * Policy: ArgumentCaptor로 실제 메서드 파라미터 캡처
     * Contract(Input): 유효한 user, beachId
     * Contract(Output): deleteByUserIdAndBeachId가 올바른 인자로 호출됨
     */
    @Test
    @DisplayName("TC-S05: removeFavorite - 정상 제거 시 올바른 파라미터 전달")
    void removeFavorite_정상제거_파라미터검증() {
        // Given
        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> beachIdCaptor = ArgumentCaptor.forClass(UUID.class);

        // When
        favoriteService.removeFavorite(testUser, beachId);

        // Then - Captor로 올바른 파라미터가 전달되었는지 검증
        then(favoriteRepository).should().deleteByUserIdAndBeachId(
                userIdCaptor.capture(),
                beachIdCaptor.capture()
        );

        assertThat(userIdCaptor.getValue()).isEqualTo(testUser.getId());
        assertThat(beachIdCaptor.getValue()).isEqualTo(beachId);
    }

    // ========== P1 권장 테스트: toggleFavorite 엣지 케이스 ==========

    /**
     * TC-S06: toggleFavorite - 추가 케이스
     * Why: 찜하지 않은 상태에서 toggle 시 추가되는지 검증
     * Policy: existsByUserIdAndBeachId = false → addFavorite 호출 → true 반환
     * Contract(Input): 찜하지 않은 beachId
     * Contract(Output): true (추가됨)
     */
    @Test
    @DisplayName("TC-S06: toggleFavorite - 찜하지 않은 상태에서 추가")
    void toggleFavorite_찜안함_추가성공() {
        // Given - 찜하지 않은 상태
        given(favoriteRepository.existsByUserIdAndBeachId(testUser.getId(), beachId))
                .willReturn(false);
        given(beachRepository.findById(beachId))
                .willReturn(Optional.of(testBeach));
        given(favoriteRepository.save(any(UserFavorite.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // When
        boolean result = favoriteService.toggleFavorite(testUser, beachId);

        // Then - 추가되어 true 반환
        assertThat(result).isTrue();

        // Then - 핵심 동작: 저장이 수행되었는지 검증
        then(favoriteRepository).should().save(any(UserFavorite.class));
        then(favoriteRepository).should(never()).deleteByUserIdAndBeachId(any(), any());
    }

    /**
     * TC-S07: toggleFavorite - 제거 케이스
     * Why: 이미 찜한 상태에서 toggle 시 제거되는지 검증
     * Policy: existsByUserIdAndBeachId = true → removeFavorite 호출 → false 반환
     * Contract(Input): 이미 찜한 beachId
     * Contract(Output): false (제거됨)
     */
    @Test
    @DisplayName("TC-S07: toggleFavorite - 이미 찜한 상태에서 제거")
    void toggleFavorite_찜함_제거성공() {
        // Given - 이미 찜한 상태
        given(favoriteRepository.existsByUserIdAndBeachId(testUser.getId(), beachId))
                .willReturn(true);

        // When
        boolean result = favoriteService.toggleFavorite(testUser, beachId);

        // Then - 제거되어 false 반환
        assertThat(result).isFalse();

        // Then - 핵심 동작: 삭제가 수행되었는지 검증
        then(favoriteRepository).should().deleteByUserIdAndBeachId(testUser.getId(), beachId);
        then(favoriteRepository).should(never()).save(any());
    }

    /**
     * TC-S08: toggleFavorite - 동시성 경합 (Race Condition)
     * Why: 동시 요청으로 인한 중복 저장 시도 시 멱등성 보장
     * Policy: addFavorite에서 DataIntegrityViolationException 발생 → IllegalStateException 변환 → toggleFavorite에서 catch 후 true 반환
     * Contract(Input): 동시성으로 인해 exists=false였지만 save 시점에 이미 존재
     * Contract(Output): 예외 전파 없이 true 반환 (멱등성 보장)
     */
    @Test
    @DisplayName("TC-S08: toggleFavorite - 동시성 경합 시 멱등성 보장")
    void toggleFavorite_동시성경합_멱등성보장() {
        // Given - 체크 시에는 없었지만 save 시점에 중복 발생 (Race Condition)
        given(favoriteRepository.existsByUserIdAndBeachId(testUser.getId(), beachId))
                .willReturn(false);
        given(beachRepository.findById(beachId))
                .willReturn(Optional.of(testBeach));
        given(favoriteRepository.save(any(UserFavorite.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate entry"));

        // When - 동시성 예외가 발생해도 정상 처리되어야 함 (예외 전파 X)
        boolean result = favoriteService.toggleFavorite(testUser, beachId);

        // Then - 멱등성 보장: 예외 없이 true 반환 (이미 추가된 것으로 간주)
        assertThat(result).isTrue();

        // Then - 핵심 동작: 저장 시도는 있었지만, 삭제는 없어야 함
        then(favoriteRepository).should().save(any(UserFavorite.class));
        then(favoriteRepository).should(never()).deleteByUserIdAndBeachId(any(), any());
    }
}

