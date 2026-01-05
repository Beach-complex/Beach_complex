# ADR-004: 캐시 저장소 마이그레이션 (ConcurrentHashMap → Caffeine)

## 상태

Accepted

---

## 컨텍스트

BeachCheck 서비스는 해변 정보 조회 성능 최적화를 위해 Spring Cache 추상화를 활용하고 있으며, 초기에는 `ConcurrentMapCacheManager`를 캐시 저장소로  사용했다.

### 현재 상황
- `beachSummaries` 캐시: 사용자별 해변 목록 + 찜 여부 정보 캐싱
- `facilitySummaries` 캐시: 해변별 편의시설 정보 캐싱
- `conditionSnapshots` 캐시: 해변별 날씨/상태 정보 캐싱 (최근 24시간)

### 발견된 문제
1. **TTL(Time-To-Live) 부재**: 한번 캐시된 데이터가 영구 보존되어 `conditionSnapshots`의 경우 오래된 날씨 데이터 제공
2. **메모리 무제한 증가**: 캐시 크기 제한 기능이 없어 사용자 증가 시 OOM 위험
3. **설정 불일치**: `application.yml`에 Caffeine 설정이 있으나 실제로는 적용되지 않아 혼란 유발
4. **모니터링 부재**: 캐시 히트율, 메모리 사용량 등 메트릭 수집 불가

### PR 리뷰 지적사항 (PB-49)
- `findAll()`의 `@Cacheable("beachSummaries")`는 사용자 구분 없이 캐시 키 생성
- 사용자 A의 찜 정보가 사용자 B에게 잘못 표시될 수 있음
- 찜 변경 시 캐시 무효화 전략 필요

---

## 결정

`ConcurrentMapCacheManager`를 **Caffeine Cache**로 교체하고, 사용자별 캐시 키 전략을 도입한다.

### 주요 변경사항
1. **의존성 추가**: `com.github.ben-manes.caffeine:caffeine:3.1.8`
2. **CacheConfig 수정**: CaffeineCacheManager로 전환, TTL 10분 및 최대 1000개 엔트리 제한
3. **캐시 키 전략**: `@Cacheable(value = "beachSummaries", key = "#user?.id ?: 'anonymous'")`
4. **캐시 무효화**: 찜 추가/삭제 시 `@CacheEvict(value = "beachSummaries", key = "#user.id")`

---

## 근거

### Caffeine 선택 이유
- **TTL 지원**: 10분 자동 만료로 오래된 데이터 방지
- **메모리 제어**: maximumSize로 OOM 위험 제거
- **고성능**: JVM 내부 처리로 네트워크 오버헤드 없음 (Redis 대비)
- **통계 기능**: recordStats()로 캐시 효율성 측정 가능
- **Spring 통합**: Spring Boot의 Cache Abstraction과 완벽 호환

### Redis를 선택하지 않은 이유
- 현재 단일 서버 환경에서는 분산 캐시 불필요 (over-engineering)
- Redis 서버 운영 비용 및 관리 부담
- 네트워크 레이턴시로 인한 성능 저하 (로컬 캐시 대비)
- 추후 트래픽 증가 시 마이그레이션 가능 (Spring Cache Abstraction 덕분에 코드 변경 최소화)

### 사용자별 캐시 키 전략
- `beachSummaries::1` (user.id=1)
- `beachSummaries::2` (user.id=2)
- `beachSummaries::anonymous` (비로그인)
- 각 사용자의 찜 정보가 독립적으로 캐시되어 데이터 불일치 방지

---

## 결과

### 긍정적 영향
1. **데이터 신선도 보장**: 10분 TTL로 최악의 경우에도 10분 후엔 최신 데이터 제공
2. **메모리 안정성**: 최대 1000개 엔트리 제한으로 OOM 위험 제거
3. **사용자별 정확한 데이터**: 찜 정보 불일치 문제 해결 (PB-49)
4. **운영 가시성**: Actuator `/metrics` 엔드포인트를 통한 캐시 모니터링 가능

### 부정적 영향
1. **캐시 워밍 주기 단축**: TTL 10분으로 인한 주기적 DB 재조회 (허용 가능 수준)
2. **분산 환경 미지원**: 멀티 인스턴스 배포 시 서버 간 캐시 불일치 가능 (추후 Redis로 해결 필요)

### 향후 작업
- **Short-term**: 캐시별 차등 TTL 적용 검토 (facilitySummaries 1시간, conditionSnapshots 5분)
- **Mid-term**: 트래픽 증가 시 Redis 전환 고려 (멀티 인스턴스 배포 시점)
- **Long-term**: 2-tier 캐싱 전략 (L1: Caffeine, L2: Redis)

---

## 대안

### 1. Ehcache
- Caffeine 대비 성능 낮음 (벤치마크 결과)
- Spring Boot 3.x에서 Caffeine 권장
- 커뮤니티 활성도 낮음

### 2. Guava Cache
- Caffeine이 Guava Cache의 후속 프로젝트
- Guava는 maintenance 모드
- Caffeine이 더 나은 성능 및 기능 제공

### 3. Redis 즉시 도입
- 현재 트래픽 규모에서 과도한 복잡도
- Redis 서버 운영 비용 및 인프라 의존성
- 분산 환경 필요성 아직 없음

---

## 참고

- [Caffeine GitHub](https://github.com/ben-manes/caffeine)
- [Spring Cache Abstraction Docs](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- Issue: PB-49 (찜 동기화 문제)
- PR Review: beachSummaries 캐시 키/무효화 전략 검토

---

## 작성일자

2026-01-05

---
