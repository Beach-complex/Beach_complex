# 동시성 이슈: UserFavorite 중복 저장 문제

## 문제 정의

### 현상
`UserFavoriteService.addFavorite()` 메서드에서 동시 요청 시 DB UNIQUE 제약 위반으로 500 에러 발생 가능

### 발생 위치
**파일**: `src/main/java/com/beachcheck/service/UserFavoriteService.java:33-44`

**현재 코드**:
```java
@Transactional
public UserFavorite addFavorite(User user, UUID beachId) {
    // (1) 존재 확인
    if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
        throw new IllegalStateException("이미 찜한 해수욕장입니다.");
    }

    Beach beach = beachRepository.findById(beachId)
            .orElseThrow(() -> new IllegalArgumentException("해수욕장을 찾을 수 없습니다."));

    // (2) 저장
    UserFavorite favorite = new UserFavorite(user, beach);
    return favoriteRepository.save(favorite);
}
```

### 문제 원인: Race Condition (경합 상태)

**시나리오**:
```
시간축 →

Request A: existsByUserIdAndBeachId(user1, beach1) → false 반환
Request B: existsByUserIdAndBeachId(user1, beach1) → false 반환
Request A: save(user1, beach1) → 성공
Request B: save(user1, beach1) → ❌ DataIntegrityViolationException
                                   (UNIQUE 제약 위반)
```

**근본 원인**:
- `@Transactional`은 **하나의 트랜잭션 내** 원자성(atomicity)을 보장하지만
- **서로 다른 두 트랜잭션 간의 격리성(isolation)은 별개 문제**
- 두 개의 독립적인 HTTP 요청 = 두 개의 별도 트랜잭션

**트랜잭션 격리 수준 (Isolation Level)**:
- Spring 기본값: `READ_COMMITTED` (PostgreSQL 기본값)
- `READ_COMMITTED`에서는:
  ```
  TX-A: SELECT ... (결과: 없음)
  TX-B: SELECT ... (결과: 없음, TX-A가 아직 커밋 안 함)
  TX-A: INSERT + COMMIT (성공)
  TX-B: INSERT (UNIQUE 제약 위반!) ❌
  ```

**왜 @Transactional만으로 해결 안 되나?**:
- 원자성: ✅ 각 트랜잭션 내부는 all-or-nothing 보장
- 격리성: ⚠️ 동시 실행되는 트랜잭션끼리는 서로 영향 줌 (격리 수준에 따라)
- **이 문제는 격리성 문제**

**영향도**:
- 사용자가 빠르게 찜 버튼을 여러 번 클릭 시
- 여러 기기에서 동시에 같은 해변 찜 시
- 발생 확률: 낮음 (millisecond 단위 경합), 하지만 **실패 시 사용자 경험 저하**

---

## 해결 방안

### 방안 1: DB 예외 처리 (권장) ⭐

**개념**: UNIQUE 제약 위반 예외를 catch하여 적절히 처리

**장점**:
- ✅ 간단한 구현
- ✅ 성능 영향 최소
- ✅ DB 제약조건을 최종 방어선으로 활용
- ✅ 중복 저장 시도를 성공으로 간주 (멱등성)

**단점**:
- ⚠️ 예외 발생 시 약간의 오버헤드

**구현**:
```java
@Transactional
public UserFavorite addFavorite(User user, UUID beachId) {
    // 이미 찜했는지 확인 (대부분의 경우 중복 방지)
    if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
        throw new IllegalStateException("이미 찜한 해수욕장입니다.");
    }

    Beach beach = beachRepository.findById(beachId)
            .orElseThrow(() -> new IllegalArgumentException("해수욕장을 찾을 수 없습니다."));

    UserFavorite favorite = new UserFavorite(user, beach);

    try {
        return favoriteRepository.save(favorite);
    } catch (DataIntegrityViolationException e) {
        // 동시 요청으로 인한 UNIQUE 제약 위반 (경합 상태)
        // 이미 저장되었으므로 성공으로 간주하거나 예외 던지기
        throw new IllegalStateException("이미 찜한 해수욕장입니다.");
    }
}
```

**Import 추가**:
```java
import org.springframework.dao.DataIntegrityViolationException;
```

**적용 위치**:
- `UserFavoriteService.addFavorite()` (line 33)
- `UserFavoriteService.toggleFavorite()` (line 58) - addFavorite 호출하므로 자동 처리

---

### 방안 2: 격리 수준 변경 (SERIALIZABLE) - 비권장

**개념**: 트랜잭션 격리 수준을 최고 수준으로 설정

**구현**:
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public UserFavorite addFavorite(User user, UUID beachId) {
    // ... 기존 코드
}
```

**장점**:
- ✅ 완벽한 격리 보장 (트랜잭션 직렬화)
- ✅ 동시성 문제 완전 해결

**단점**:
- ❌ **심각한 성능 저하** (트랜잭션 직렬 실행)
- ❌ 데드락 발생 위험
- ❌ 전체 시스템 처리량(throughput) 감소
- ❌ 이 간단한 문제에 과도한 해결책

**결론**: 성능 희생이 너무 크므로 비권장. 방안 1이 훨씬 효율적.

---

### 방안 3: Pessimistic Lock - 비권장

**개념**: 데이터베이스 행 레벨 잠금

**구현**:
```java
// UserFavoriteRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT uf FROM UserFavorite uf WHERE uf.user.id = :userId AND uf.beach.id = :beachId")
Optional<UserFavorite> findByUserIdAndBeachIdWithLock(
    @Param("userId") UUID userId,
    @Param("beachId") UUID beachId
);
```

**장점**:
- ✅ 동시성 제어

**단점**:
- ❌ 잠금 대기로 인한 성능 저하
- ❌ 데드락 가능성
- ❌ 레코드가 존재하지 않을 때 잠금 불가 (INSERT 전이므로)
- ❌ 이 문제 해결에 부적합

**결론**: INSERT 시나리오에 부적합. 방안 1 권장.

---

### 방안 4: PostgreSQL UPSERT (ON CONFLICT) - 대안

**개념**: INSERT 시도 → 충돌 시 무시 또는 업데이트

**장점**:
- ✅ DB 레벨에서 원자적 처리
- ✅ 예외 발생 안 함

**단점**:
- ❌ JPA/Hibernate에서 직접 지원 안 함 (Native Query 필요)
- ❌ 기존 코드 구조 변경 필요
- ❌ DB 종속적 (PostgreSQL 전용)

**구현 (참고용)**:
```java
// UserFavoriteRepository.java
@Modifying
@Query(value = """
    INSERT INTO user_favorites (id, user_id, beach_id, created_at)
    VALUES (:#{#favorite.id}, :#{#favorite.user.id}, :#{#favorite.beach.id}, NOW())
    ON CONFLICT (user_id, beach_id) DO NOTHING
    """, nativeQuery = true)
void insertIgnoreDuplicate(@Param("favorite") UserFavorite favorite);
```

**결론**: 복잡도 증가. 방안 1이 더 간단하고 효과적.

---

## 권장 해결 방법: 방안 1 (DB 예외 처리)

### 이유
1. **간단함**: 3줄 코드 추가만으로 해결
2. **안전함**: DB UNIQUE 제약을 최종 방어선으로 활용
3. **성능**: 대부분 `existsByUserIdAndBeachId()`에서 걸러지므로 예외 발생 드묾
4. **유지보수**: 다른 개발자가 이해하기 쉬움

### 적용 범위
- ✅ `UserFavoriteService.addFavorite()` (필수)
- ✅ `UserFavoriteService.toggleFavorite()` (addFavorite 호출하므로 자동 처리)
- ❌ `removeFavorite()` (중복 삭제는 문제 없음)

### 테스트 시나리오
**수동 테스트**:
1. Postman에서 동일한 요청을 빠르게 2번 연속 전송
2. 브라우저에서 찜 버튼 빠르게 연타
3. 두 브라우저에서 동시에 찜 버튼 클릭

**예상 결과**:
- ✅ 첫 번째 요청: 200 OK, 찜 추가
- ✅ 두 번째 요청: 400 Bad Request, "이미 찜한 해수욕장입니다."
- ❌ 500 Internal Server Error 발생 안 함

---

## 구현 체크리스트

- [ ] `UserFavoriteService.java`에 `DataIntegrityViolationException` import 추가
- [ ] `addFavorite()` 메서드에 try-catch 블록 추가
- [ ] 로컬 환경에서 동시성 테스트 (Postman 연속 요청)
- [ ] 에러 로그 확인 (DataIntegrityViolationException이 IllegalStateException으로 변환되는지)
- [ ] PR 코멘트에 해결 완료 응답

---

## 참고 자료

**관련 이슈**:
- GitHub PR Comment: "현재 로직은 (1) 존재 확인 (2) 저장이 분리되어 있어서..."

**DB 제약조건**:
```sql
-- V6__add_user_favorites.sql:17-18
CONSTRAINT uk_user_beach
    UNIQUE (user_id, beach_id)
```

**Spring Data 문서**:
- [DataIntegrityViolationException](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/dao/DataIntegrityViolationException.html)
- [@Transactional Isolation Levels](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)

---

## 결론

**채택 방안**: 방안 1 (DB 예외 처리)

**예상 소요 시간**: 5분 (코드 수정 + 테스트)

**위험도**: 낮음 (기존 로직에 안전 장치 추가만)

**우선순위**: Medium (사용자 경험 개선, 하지만 발생 빈도 낮음)