package com.beachcheck.integration;

import static com.beachcheck.fixture.CacheTestHelper.getCacheValue;
import static com.beachcheck.fixture.CacheTestHelper.hasKey;
import static com.beachcheck.fixture.CacheTestHelper.printCacheState;
import static com.beachcheck.fixture.FavoriteTestFixtures.createBeachWithLocation;
import static com.beachcheck.fixture.FavoriteTestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beachcheck.base.IntegrationTest;
import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.domain.UserFavorite;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.UserFavoriteRepository;
import com.beachcheck.repository.UserRepository;
import com.beachcheck.service.UserFavoriteService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class UserFavoriteServiceIntegrationTest extends IntegrationTest {
  @Autowired private UserFavoriteService favoriteService;

  @Autowired private UserFavoriteRepository favoriteRepository;

  @Autowired private BeachRepository beachRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private CacheManager cacheManager;

  private Beach beach1, beach2;
  private User user1, user2;

  @BeforeEach
  void setUp() {
    // Fixture를 사용하여 해수욕장 생성 (PostGIS location 포함)
    // Why: Flyway 데이터(HAEUNDAE, GWANGALLI 등)와 충돌 방지를 위해 TEST_ prefix 사용
    beach1 =
        beachRepository.save(
            createBeachWithLocation("TEST_BEACH_1", "테스트해수욕장1", 129.1603, 35.1587));
    beach2 =
        beachRepository.save(
            createBeachWithLocation("TEST_BEACH_2", "테스트해수욕장2", 129.1189, 35.1532));

    // Fixture를 사용하여 사용자 생성
    user1 = userRepository.save(createUser("user1@test.com", "User 1"));
    user2 = userRepository.save(createUser("user2@test.com", "User 2"));

    // 캐시 초기화
    cacheManager.getCache("beachSummaries").clear();
  }

  /**
   * Why: 찜 추가 시 실제 DB에 저장되는지 검증 Policy: Repository로 재조회하여 DB 저장 확인, 사용자 간 격리 검증 Contract(Input):
   * 유효한 user, beachId Contract(Output): UserFavorite 저장, ID/createdAt 자동 생성, 다른 사용자 영향 없음
   */
  @Test
  @DisplayName("P0-01: 찜 추가 시 실제 DB에 저장됨")
  void addFavorite_withRealDB_savesSuccessfully() {
    // When: 찜 추가
    UserFavorite result = favoriteService.addFavorite(user1, beach1.getId());

    // Then: 반환값 검증
    assertThat(result).isNotNull();
    assertThat(result.getId()).isNotNull();
    assertThat(result.getUser().getId()).isEqualTo(user1.getId());
    assertThat(result.getBeach().getId()).isEqualTo(beach1.getId());
    assertThat(result.getCreatedAt()).isNotNull();

    // Then: DB에 실제로 저장되었는지 확인
    List<UserFavorite> favorites = favoriteRepository.findByUserId(user1.getId());
    assertThat(favorites).hasSize(1);
    assertThat(favorites.get(0).getBeach().getId()).isEqualTo(beach1.getId());

    // Then: 다른 사용자는 영향 없음
    List<UserFavorite> user2Favorites = favoriteRepository.findByUserId(user2.getId());
    assertThat(user2Favorites).isEmpty();
  }

  /**
   * Why: DB UNIQUE 제약과 서비스 레이어 중복 체크가 제대로 동작하는지 검증 Policy: existsByUserIdAndBeachId 체크 후 예외 발생,
   * DB에는 1개만 유지 Contract(Input): 이미 찜한 beachId Contract(Output): IllegalStateException 발생, DB에 중복
   * 저장 안됨
   */
  @Test
  @DisplayName("P0-02: 중복 찜 시도 시 예외 발생 (DB UNIQUE 제약)")
  void addFavorite_duplicate_throwsException() {
    // Given: 이미 찜한 상태
    favoriteService.addFavorite(user1, beach1.getId());

    // When & Then: 중복 찜 시도 시 예외 발생
    assertThatThrownBy(() -> favoriteService.addFavorite(user1, beach1.getId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("이미 찜한 해수욕장입니다");

    // Then: DB에는 여전히 1개만 존재
    List<UserFavorite> favorites = favoriteRepository.findByUserId(user1.getId());
    assertThat(favorites).hasSize(1);
  }

  /**
   * Why: 존재하지 않는 해수욕장 찜 시도 시 예외 처리가 제대로 되는지 검증 Policy: beachRepository.findById() 실패 시 즉시 예외, 트랜잭션
   * 롤백 Contract(Input): 존재하지 않는 UUID Contract(Output): IllegalArgumentException 발생, DB에 저장 안됨
   */
  @Test
  @DisplayName("P0-03: 존재하지 않는 해수욕장 찜 시 예외 발생")
  void addFavorite_nonExistentBeach_throwsException() {
    // Given: 존재하지 않는 UUID
    UUID nonExistentBeachId = UUID.randomUUID();

    // When & Then: 예외 발생
    assertThatThrownBy(() -> favoriteService.addFavorite(user1, nonExistentBeachId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("해수욕장을 찾을 수 없습니다");

    // Then: DB에 저장되지 않음
    List<UserFavorite> favorites = favoriteRepository.findByUserId(user1.getId());
    assertThat(favorites).isEmpty();
  }

  /**
   * Why: 찜 제거 시 실제 DB에서 삭제되는지 검증 Policy: deleteByUserIdAndBeachId 실행 후 Repository 재조회로 삭제 확인
   * Contract(Input): 찜한 상태의 user, beachId Contract(Output): DB에서 해당 레코드 삭제됨
   */
  @Test
  @DisplayName("P0-04: 찜 제거 시 실제 DB에서 삭제됨")
  void removeFavorite_withRealDB_deletesSuccessfully() {
    // Given: 찜 추가
    favoriteService.addFavorite(user1, beach1.getId());
    assertThat(favoriteRepository.findByUserId(user1.getId())).hasSize(1);

    // When: 찜 제거
    favoriteService.removeFavorite(user1, beach1.getId());

    // Then: DB에서 삭제됨
    List<UserFavorite> favorites = favoriteRepository.findByUserId(user1.getId());
    assertThat(favorites).isEmpty();
  }

  /**
   * Why: 찜 제거의 멱등성(Idempotent) 보장 검증 Policy: deleteByUserIdAndBeachId는 존재하지 않아도 예외 없이 처리
   * Contract(Input): 찜하지 않은 상태의 user, beachId Contract(Output): 예외 없이 정상 처리, DB 상태 변화 없음
   */
  @Test
  @DisplayName("P0-05: 존재하지 않는 찜 제거 시 에러 없이 처리")
  void removeFavorite_nonExistent_doesNotThrow() {
    // When: 찜하지 않은 상태에서 제거 시도
    // Then: 예외 발생 안함 (정상 동작)
    favoriteService.removeFavorite(user1, beach1.getId());

    // Then: DB 상태 변화 없음
    List<UserFavorite> favorites = favoriteRepository.findByUserId(user1.getId());
    assertThat(favorites).isEmpty();
  }

  /**
   * Why: 찜 목록 조회 시 Beach 엔티티와 JOIN이 제대로 동작하는지 검증 Policy: UserFavorite → Beach 연관관계 조회, 사용자별 격리 확인
   * Contract(Input): 여러 사용자가 각각 여러 해수욕장을 찜한 상태 Contract(Output): 사용자별로 찜한 Beach 목록만 반환, Beach 필드 정상
   * 로딩
   */
  @Test
  @DisplayName("P0-06: 찜 목록 조회 시 Beach 엔티티와 JOIN")
  void getFavoriteBeaches_withRealDB_returnsCorrectList() {
    // Given: user1이 beach1, beach2 찜
    favoriteService.addFavorite(user1, beach1.getId());
    favoriteService.addFavorite(user1, beach2.getId());

    // Given: user2는 beach1만 찜
    favoriteService.addFavorite(user2, beach1.getId());

    // When: user1의 찜 목록 조회
    List<Beach> user1Favorites = favoriteService.getFavoriteBeaches(user1);

    // Then: user1의 찜 2개 반환
    assertThat(user1Favorites).hasSize(2);
    assertThat(user1Favorites)
        .extracting(Beach::getId)
        .containsExactlyInAnyOrder(beach1.getId(), beach2.getId()); // 순서 상관없이 검증 but 1과 2만 존재해야함

    // Then: Beach 엔티티가 제대로 로딩되었는지 검증
    assertThat(user1Favorites.get(0).getName()).isNotNull();
    assertThat(user1Favorites.get(0).getCode()).isNotNull();
    assertThat(user1Favorites.get(1).getName()).isNotNull();
    assertThat(user1Favorites.get(1).getCode()).isNotNull();

    // When: user2의 찜 목록 조회
    List<Beach> user2Favorites = favoriteService.getFavoriteBeaches(user2);

    // Then: user2의 찜 1개만 반환
    assertThat(user2Favorites).hasSize(1);
    assertThat(user2Favorites.get(0).getId()).isEqualTo(beach1.getId());
  }

  /**
   * Why: 토글 기능으로 찜 추가가 제대로 동작하는지 검증 Policy: 찜하지 않은 상태에서 토글 시 addFavorite 호출, DB 저장 확인
   * Contract(Input): 찜하지 않은 상태의 user, beachId Contract(Output): true 반환, DB에 UserFavorite 저장됨
   */
  @Test
  @DisplayName("P1-01: 토글로 찜 추가 성공")
  void toggleFavorite_add_savesSuccessfully() {
    // When: 찜하지 않은 상태에서 토글
    boolean result = favoriteService.toggleFavorite(user1, beach1.getId());

    // Then: 추가됨 (true)
    assertThat(result).isTrue();

    // Then: DB에 저장됨
    List<UserFavorite> favorites = favoriteRepository.findByUserId(user1.getId());
    assertThat(favorites).hasSize(1);
  }

  /**
   * Why: 토글 기능으로 찜 제거가 제대로 동작하는지 검증 Policy: 이미 찜한 상태에서 토글 시 removeFavorite 호출, DB 삭제 확인
   * Contract(Input): 이미 찜한 상태의 user, beachId Contract(Output): false 반환, DB에서 UserFavorite 삭제됨
   */
  @Test
  @DisplayName("P1-02: 토글로 찜 제거 성공")
  void toggleFavorite_remove_deletesSuccessfully() {
    // Given: 이미 찜한 상태
    favoriteService.addFavorite(user1, beach1.getId());

    // When: 토글
    boolean result = favoriteService.toggleFavorite(user1, beach1.getId());

    // Then: 제거됨 (false)
    assertThat(result).isFalse();

    // Then: DB에서 삭제됨
    List<UserFavorite> favorites = favoriteRepository.findByUserId(user1.getId());
    assertThat(favorites).isEmpty();
  }

  /**
   * Why: 찜 추가 시 @CacheEvict가 제대로 동작하여 캐시가 무효화되는지 검증 Policy: 해당 사용자(user.id)의 beachSummaries 캐시만 제거,
   * 다른 사용자 영향 없음 Contract(Input): 캐시에 데이터가 있는 상태에서 찜 추가 Contract(Output): 해당 사용자의 캐시만 null로 변경됨
   */
  @Test
  @DisplayName("P1-03: 찜 추가 시 캐시 무효화")
  void addFavorite_evictsCache_correctly() {
    // Given: user1과 user2의 캐시에 각각 데이터 추가
    UUID user1CacheKey = user1.getId();
    UUID user2CacheKey = user2.getId();
    cacheManager.getCache("beachSummaries").put("user:" + user1CacheKey, "user1_cached_data");
    cacheManager.getCache("beachSummaries").put("user:" + user2CacheKey, "user2_cached_data");

    printCacheState(cacheManager, "beachSummaries", "Before addFavorite");
    assertThat(hasKey(cacheManager, "beachSummaries", "user:" + user1CacheKey)).isTrue();
    assertThat(hasKey(cacheManager, "beachSummaries", "user:" + user2CacheKey)).isTrue();

    // When: user1이 찜 추가 (@CacheEvict 동작, key = user.id)
    favoriteService.addFavorite(user1, beach1.getId());
    printCacheState(cacheManager, "beachSummaries", "After addFavorite");

    // Then: user1의 캐시만 무효화되고, user2의 캐시는 유지됨
    assertThat(hasKey(cacheManager, "beachSummaries", "user:" + user1CacheKey)).isFalse();
    assertThat(hasKey(cacheManager, "beachSummaries", "user:" + user2CacheKey)).isTrue();
    assertThat(getCacheValue(cacheManager, "beachSummaries", "user:" + user2CacheKey))
        .isEqualTo("user2_cached_data");
  }

  /**
   * Why: 찜 제거 시 @CacheEvict가 제대로 동작하여 캐시가 무효화되는지 검증 Policy: 해당 사용자(user.id)의 beachSummaries 캐시만 제거,
   * 다른 사용자 영향 없음 Contract(Input): 캐시에 데이터가 있는 상태에서 찜 제거 Contract(Output): 해당 사용자의 캐시만 null로 변경됨
   */
  @Test
  @DisplayName("P1-04: 찜 제거 시 캐시 무효화")
  void removeFavorite_evictsCache_correctly() {
    // Given: 찜 추가 및 user1, user2의 캐시 데이터 추가
    favoriteService.addFavorite(user1, beach1.getId());
    UUID user1CacheKey = user1.getId();
    UUID user2CacheKey = user2.getId();
    cacheManager.getCache("beachSummaries").put("user:" + user1CacheKey, "user1_cached_data");
    cacheManager.getCache("beachSummaries").put("user:" + user2CacheKey, "user2_cached_data");

    printCacheState(cacheManager, "beachSummaries", "Before removeFavorite");

    // When: user1이 찜 제거 (@CacheEvict 동작)
    favoriteService.removeFavorite(user1, beach1.getId());
    printCacheState(cacheManager, "beachSummaries", "After removeFavorite");

    // Then: user1의 캐시만 무효화되고, user2의 캐시는 유지됨
    assertThat(hasKey(cacheManager, "beachSummaries", "user:" + user1CacheKey)).isFalse();
    assertThat(hasKey(cacheManager, "beachSummaries", "user:" + user2CacheKey)).isTrue();
    assertThat(getCacheValue(cacheManager, "beachSummaries", "user:" + user2CacheKey))
        .isEqualTo("user2_cached_data");
  }

  /**
   * Why: 동시 찜 추가 요청 시 UNIQUE 제약과 예외 처리가 안전하게 동작하는지 검증 Policy: CountDownLatch로 동시 요청 시뮬레이션, UNIQUE
   * 제약으로 1개만 저장 Contract(Input): 10개 스레드가 동시에 같은 user/beach 찜 추가 시도 Contract(Output): 1개만 성공, 9개는
   * IllegalStateException 또는 DataIntegrityViolationException, DB에 1개만 저장
   *
   * <p>예외 처리 전략: - IllegalStateException: Pre-check(exists)에서 차단된 경우 -
   * DataIntegrityViolationException: 트랜잭션 커밋 시점에 DB UNIQUE 제약 위반
   */
  @Test
  @DisplayName("P2-01: 동시 찜 추가 요청 처리 (동시성)")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentAddFavorite_handlesCorrectly() throws InterruptedException {

    // Given: 10개의 스레드가 동시에 같은 찜 추가 시도
    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount); // 병렬 처리 스레드풀
    CountDownLatch latch = new CountDownLatch(threadCount); // 모든 스레드 완료 대기

    AtomicInteger successCount = new AtomicInteger(0); // 스레드 안전한 성공 카운터
    AtomicInteger failCount = new AtomicInteger(0); // 스레드 안전한 실패 카운터

    // When: 동시 요청
    for (int i = 0; i < threadCount; i++) {
      executorService.submit(
          () -> {
            try {
              favoriteService.addFavorite(user1, beach1.getId());
              successCount.incrementAndGet();
            } catch (IllegalStateException | DataIntegrityViolationException e) {
              // 예상되는 실패: Pre-check 또는 DB UNIQUE 제약 위반
              failCount.incrementAndGet();
            } catch (Exception e) {
              // 예상치 못한 예외는 즉시 전파하여 테스트 실패
              throw new RuntimeException("Unexpected exception in concurrent test", e);
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await(); // 모든 스레드 완료 대기
    executorService.shutdown();

    // Then: 1개만 성공, 나머지는 실패
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failCount.get()).isEqualTo(9);

    // Then: DB에는 1개만 존재
    List<UserFavorite> favorites = favoriteRepository.findByUserId(user1.getId());
    assertThat(favorites).hasSize(1);

    favoriteRepository.deleteAll(); // 테스트 데이터 정리
  }
}
