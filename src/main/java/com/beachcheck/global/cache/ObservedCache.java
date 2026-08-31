package com.beachcheck.global.cache;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.cache.caffeine.CaffeineCache;

public final class ObservedCache extends CaffeineCache {

  private final ObservationRegistry registry;

  public ObservedCache(
      String name,
      com.github.benmanes.caffeine.cache.Cache<Object, Object> cache,
      boolean allowNullValues,
      ObservationRegistry registry) {
    super(name, cache, allowNullValues);
    this.registry = registry;
  }

  @Override
  public ValueWrapper get(Object key) {
    return observe("get", () -> super.get(key), value -> value == null ? "miss" : "hit");
  }

  @Override
  public void put(Object key, Object value) {
    observe("put", () -> super.put(key, value));
  }

  @Override
  public void evict(Object key) {
    observe("evict", () -> super.evict(key));
  }

  @Override
  public void clear() {
    observe("clear", super::clear);
  }

  private void observe(String operation, Runnable action) {
    observe(
        operation,
        () -> {
          action.run();
          return null;
        },
        ignored -> "success");
  }

  private <T> T observe(String operation, Supplier<T> action, Function<T, String> result) {
    Observation observation;
    try {
      observation =
          Observation.createNotStarted("cache.operation", registry)
              .contextualName("caffeine " + operation + " " + getName())
              .lowCardinalityKeyValue("cache.system", "caffeine")
              .lowCardinalityKeyValue("cache.name", getName())
              .lowCardinalityKeyValue("cache.operation", operation)
              .start();
    } catch (RuntimeException | Error ignored) {
      return action.get();
    }
    try {
      T value = action.get();
      safely(() -> observation.lowCardinalityKeyValue("cache.result", result.apply(value)));
      return value;
    } catch (RuntimeException | Error failure) {
      safely(() -> observation.lowCardinalityKeyValue("cache.result", "error"));
      safely(() -> observation.error(new IllegalStateException("Cache operation failed")));
      throw failure;
    } finally {
      safely(observation::stop);
    }
  }

  private void safely(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException | Error ignored) {
      // 관측 실패는 캐시 동작에 영향을 주지 않는다.
    }
  }
}
