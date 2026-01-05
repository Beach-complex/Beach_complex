package com.beachcheck.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of("beachSummaries", "facilitySummaries", "conditionSnapshots"));

        // 10분 후 자동 만료 -> 최악의 경우에도 10분 후엔 최신 데이터 제공, 최대 1000개 캐시 유지
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES));  // TTL 설정
        return cacheManager;
    }

    // TODO: Replace simple map cache with Redis-backed cache metrics once Redis cluster is provisioned.
}
