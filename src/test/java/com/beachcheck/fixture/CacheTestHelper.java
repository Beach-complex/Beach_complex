package com.beachcheck.fixture;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.Collection;
import java.util.Map;

/**
 * Why: 캐시 상태 확인 로직을 재사용 가능하도록 헬퍼 클래스로 분리
 * Policy: instanceof를 사용한 타입 안전한 캐스팅으로 구현체별 처리
 * Contract(Input): CacheManager와 캐시 이름
 * Contract(Output): 캐시의 상태 정보 또는 포맷팅된 출력
 *
 * Note: Caffeine 의존성은 test scope로만 추가되어 프로덕션 코드에는 영향 없음
 *       Redis 전환 시 printRedisCacheDetails() 메서드만 추가하면 됨
 */
public class CacheTestHelper {

    /**
     * 캐시 상태를 콘솔에 출력
     * Why: 테스트 디버깅 시 캐시 내용을 시각적으로 확인
     * Policy: Spring Cache 추상화만 사용하여 캐시 구현체 독립적
     *
     * @param cacheManager Spring CacheManager
     * @param cacheName 캐시 이름
     * @param message 출력할 메시지 (예: "Before addFavorite")
     */
    public static void printCacheState(CacheManager cacheManager, String cacheName, String message) {
        System.out.println("\n=== " + message + " ===");

        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            System.out.println("캐시 '" + cacheName + "'가 존재하지 않습니다.");
            return;
        }

        // Spring Cache 추상화는 전체 캐시 조회를 지원하지 않으므로
        // 테스트에서 추적한 키 목록을 사용하거나, 캐시 통계 정보만 출력
        System.out.println("캐시 이름: " + cacheName);
        System.out.println("캐시 구현체: " + cache.getClass().getSimpleName());

        // Caffeine/Redis 구현체별로 안전하게 처리
        printCacheDetails(cache);
    }

    /**
     * 특정 키의 캐시 값 존재 여부 확인
     * Why: @CacheEvict 동작 검증 시 사용
     * Policy: Spring Cache 추상화 사용
     *
     * Note: 이 메서드는 "키가 존재하는지"만 확인합니다.
     *       캐시에 null 값이 저장된 경우에도 true를 반환합니다.
     *
     * @param cacheManager Spring CacheManager
     * @param cacheName 캐시 이름
     * @param key 캐시 키
     * @return 캐시에 키가 존재하면 true (값이 null이어도 true), 키가 없으면 false
     */
    public static boolean hasKey(CacheManager cacheManager, String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return false;
        }
        // ValueWrapper가 null이면 키가 존재하지 않음
        // ValueWrapper가 not null이면 키가 존재 (값은 null일 수 있음)
        return cache.get(key) != null;
    }

    /**
     * 특정 키의 캐시 값 조회
     * Why: 캐시된 값 검증 시 사용
     * Policy: Spring Cache 추상화 사용
     *
     * Warning: 이 메서드는 "키가 없는 경우"와 "값이 null인 경우"를 구분하지 않습니다.
     *          두 경우 모두 null을 반환합니다.
     *          키 존재 여부를 확인하려면 hasKey()를 먼저 사용하세요.
     *
     * @param cacheManager Spring CacheManager
     * @param cacheName 캐시 이름
     * @param key 캐시 키
     * @return 캐시된 값 (키가 없거나 값이 null이면 null)
     */
    public static Object getCacheValue(CacheManager cacheManager, String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }

        Cache.ValueWrapper wrapper = cache.get(key);
        return wrapper != null ? wrapper.get() : null;
    }

    /**
     * 모든 캐시 이름 조회
     * Why: 캐시 상태 전체 점검 시 사용
     *
     * @param cacheManager Spring CacheManager
     * @return 캐시 이름 목록
     */
    public static Collection<String> getCacheNames(CacheManager cacheManager) {
        return cacheManager.getCacheNames();
    }

    /**
     * 캐시 구현체별 상세 정보 출력
     * Why: 구현체별로 다른 방식으로 캐시 내용 조회
     * Policy: instanceof를 사용한 타입 안전한 캐스팅 (Reflection 없음)
     */
    private static void printCacheDetails(Cache cache) {
        // instanceof로 타입 안전하게 체크 및 캐스팅
        if (cache instanceof CaffeineCache caffeineCache) {
            printCaffeineCacheDetails(caffeineCache);
        }
//         Redis 캐시인 경우 (향후 대응)
//         else if (cache instanceof RedisCache redisCache) {
//             printRedisCacheDetails(redisCache);
//         }
        else {
            // 알 수 없는 캐시 구현체
            System.out.println("  → 알 수 없는 캐시 구현체: " + cache.getClass().getSimpleName());
            System.out.println("  → 상세 정보 조회 미지원 (개별 키로 검증하세요)");
        }
    }

    /**
     * Caffeine 캐시 상세 정보 출력
     * Why: 로컬 캐시는 전체 내용 조회 가능
     * Policy: Reflection 없이 직접 메서드 호출 (타입 안전)
     *
     * @param caffeineCache CaffeineCache 인스턴스
     */
    private static void printCaffeineCacheDetails(CaffeineCache caffeineCache) {
        // Reflection 없이 직접 호출 - 컴파일 타임에 타입 체크됨
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
            caffeineCache.getNativeCache();

        Map<Object, Object> cacheMap = nativeCache.asMap();

        if (cacheMap.isEmpty()) {
            System.out.println("  캐시가 비어있습니다.");
        } else {
            System.out.println("  캐시 크기: " + cacheMap.size());
            cacheMap.forEach((key, value) ->
                System.out.println("    키: " + key + " → 값: " + value)
            );
        }
    }

}


