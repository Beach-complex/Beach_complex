package com.beachcheck.global.cache;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

public final class ObservedCacheManager extends CaffeineCacheManager {

  private final ObservationRegistry registry;

  public ObservedCacheManager(ObservationRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected Cache adaptCaffeineCache(
      String name, com.github.benmanes.caffeine.cache.Cache<Object, Object> cache) {
    return new ObservedCache(name, cache, isAllowNullValues(), registry);
  }
}
