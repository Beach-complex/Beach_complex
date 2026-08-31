package com.beachcheck.global.config;

import com.beachcheck.global.cache.ObservedCacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager(ObservationRegistry observationRegistry) {
    ObservedCacheManager cacheManager = new ObservedCacheManager(observationRegistry);
    cacheManager.setCacheNames(
        List.of("beachSummaries", "facilitySummaries", "conditionSnapshots"));

    // 10분 후 자동 만료 -> 최악의 경우에도 10분 후엔 최신 데이터 제공, 최대 1000개 캐시 유지
    cacheManager.setCaffeine(
        Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(10, TimeUnit.MINUTES)); // TTL 설정
    return cacheManager;
  }

  // TODO: Replace simple map cache with Redis-backed cache metrics once Redis cluster is
  // provisioned.
}
