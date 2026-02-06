# 동시 찜 추가 요청 시 DataIntegrityViolationException 발생 문제

**컴포넌트:** db

**작성일:** 2026-01-16

| 날짜 | 작성자 | 변경 내용 |
|:---:|:---:|:---|
| 2026-01-13 | - | 초기 작성 |

## 핵심 요약

**문제:** 동시 찜 추가 요청 시 `DataIntegrityViolationException`이 서비스 레이어 try-catch로 잡히지 않음

**근본 원인:** 
- JPA는 `save()` 호출 시 즉시 INSERT하지 않고 쓰기 지연(Write-Behind)
- 실제 INSERT는 트랜잭션 커밋 시점에 발생
- 메서드가 `return`으로 종료되면 try-catch는 호출 스택에서 pop되어 소멸
- **커밋 시점(메서드 종료 후)에 발생한 예외는 메서드 내부 try-catch가 잡을 수 없음**

**해결책:** `GlobalExceptionHandler`에서 `DataIntegrityViolationException`을 처리
- 서비스 코드는 간결하게 유지 (`save()` 사용)
- 커밋 시점 예외는 전역 핸들러가 409 CONFLICT로 매핑
- 배치 최적화 유지, 유지보수성 향상

**대안:** `saveAndFlush()` + try-catch (메서드 내부에서 즉시 처리)
- 성능 저하 가능, 코드 중복, 하지만 메서드 내부 제어 가능

---

## 문제 상황

### 증상
통합 테스트 `P2-01: 동시 찜 추가 요청 처리`에서 `DataIntegrityViolationException`이 발생하여 테스트 실패
- 예상: `IllegalStateException` 9개 발생 (중복 찜 시도)
- 실제: `DataIntegrityViolationException` 발생

### 환경
- 테스트: `UserFavoriteServiceIntegrationTest`
- 메서드: `concurrentAddFavorite_handlesCorrectly()`
- 동시 요청: 10개 스레드가 동일한 user/beach 조합으로 찜 추가 시도

### 디버깅 정보
```
e = {DataIntegrityViolationException@25226} 
"org.springframework.dao.DataIntegrityViolationException: 
could not execute statement [ERROR: duplicate key value..."
```

## 원인 분석

### 1단계: 이중 체크의 필요성 검토
처음에는 "DB UNIQUE 제약이 있는데 Application Level 체크(`exists`)가 필요한가?"라는 의문에서 시작

**결론: 이중 체크 유지가 맞다**
- Application Level 체크: 99%의 중복 요청을 빠르게 차단 (성능 최적화)
- DB UNIQUE 제약: 최종 안전망 (Defense in Depth 패턴)
- 예외는 예외적 상황에만 사용해야 함 (정상 흐름에서 예외 사용은 안티패턴)

### 2단계: 예외 변환 로직 확인
`addFavorite` 메서드는 `DataIntegrityViolationException`을 `IllegalStateException`으로 변환하도록 설계됨:

```java
try {
  return favoriteRepository.save(favorite);
} catch (DataIntegrityViolationException e) {
  throw new IllegalStateException("이미 찜한 해수욕장입니다.");
}
```

그런데 왜 `DataIntegrityViolationException`이 그대로 전파되는가?

### 3단계: JPA 트랜잭션 커밋 타이밍 문제 발견

**핵심 원인: 메서드가 종료되면 try-catch는 스택에서 사라진다**

원래 코드에서 try-catch는 `save()` 호출만 감싸고 있었음:
```java
public UserFavorite addFavorite(User user, UUID beachId) {
  // exists 체크
  if (favoriteRepository.existsByUserIdAndBeachId(...)) {
    throw new IllegalStateException(...);
  }
  
  // Beach 조회
  Beach beach = beachRepository.findById(...).orElseThrow(...);
  
  // UserFavorite 생성
  UserFavorite favorite = new UserFavorite(user, beach);
  
  try {
    return favoriteRepository.save(favorite); // ← return으로 메서드 종료
  } catch (DataIntegrityViolationException e) {
    throw new IllegalStateException(...);
  }
}
```

**⚠️ 핵심 오해: "메서드 전체를 try로 감싸면 커밋 시점 예외도 잡힌다?"**

→ **아닙니다!** 메서드가 `return`되면 try-catch는 **호출 스택에서 pop되어 사라집니다**.

**진짜 문제: JPA/Hibernate의 쓰기 지연(Transactional Write-Behind)**

JPA는 성능 최적화를 위해 **쓰기 지연** 전략을 사용:

1. **`save()` 호출 시점**: 
   - DB에 즉시 INSERT하지 않음
   - 영속성 컨텍스트(메모리)에 "나중에 저장해줘" 등록만 함
   - **예외가 발생하지 않음** → try-catch가 잡을 게 없음

2. **`return` 실행**: 
   - 메서드 종료
   - **호출 스택에서 pop** → try-catch 블록 소멸

3. **AOP 프록시의 트랜잭션 커밋 (메서드 종료 후)**:
   - `flush()` 실행 → 이제야 진짜 INSERT SQL이 날아감
   - DB: "UNIQUE 제약 위반!" → `DataIntegrityViolationException` 발생
   - **하지만 try-catch는 이미 사라진 후** → 잡을 수 없음!

**동시 요청 시나리오 (시간 흐름):**
```
Thread A                                Thread B
─────────────────────────────────────────────────────────────
exists 체크 → false                     
                                        exists 체크 → false
Beach 조회 → 성공                       
save() 호출 → 메모리에만 등록 (에러 없음)
return 실행 → 메서드 종료               Beach 조회 → 성공
(try-catch 스택에서 소멸!)              save() 호출 → 메모리에만 등록 (에러 없음)
                                        return 실행 → 메서드 종료
[AOP Proxy] 트랜잭션 커밋                (try-catch 스택에서 소멸!)
  └─ flush() 실행                        
  └─ DB INSERT 성공                      [AOP Proxy] 트랜잭션 커밋
트랜잭션 완료                               └─ flush() 실행
                                           └─ DB INSERT 시도
                                           └─ ❌ UNIQUE 제약 위반!
                                           └─ DataIntegrityViolationException 발생
                                           └─ (catch할 코드가 없음, 테스트로 전파!)
```

**비유: 퇴근 후 회사에 불이 난 꼴**
- 메서드 내부 try-catch = 회사 안전 요원
- `return` = 퇴근
- 트랜잭션 커밋(flush) = 회사에서 실제 작업 실행
- 퇴근한 후에 불이 나면 안전 요원이 없어서 못 잡는 것과 같음!

## 해결 방법

### 방법 A: `GlobalExceptionHandler`에서 처리 (실무 권장) ⭐⭐⭐

**핵심 아이디어: "예외는 예외답게, 전역에서 일관성 있게 처리"**

서비스 레이어는 비즈니스 로직에만 집중하고, DB 제약 위반 예외는 컨트롤러 레이어에서 처리:

**1) 서비스 코드 (간결하게 유지):**
```java
@Transactional
@CacheEvict(value = "beachSummaries", key = "'user:' + #user.id")
public UserFavorite addFavorite(User user, UUID beachId) {
  // Pre-check (동시 요청 대부분 차단)
  if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
    throw new IllegalStateException("이미 찜한 해수욕장입니다.");
  }

  Beach beach = beachRepository.findById(beachId)
      .orElseThrow(() -> new IllegalArgumentException("해수욕장을 찾을 수 없습니다."));

  UserFavorite favorite = new UserFavorite(user, beach);
  return favoriteRepository.save(favorite); // 예외 처리는 GlobalExceptionHandler에 위임
}
```

**2) GlobalExceptionHandler 추가:**
```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
  // UNIQUE 제약 위반 판별
  if (ex.getMessage().contains("user_favorites_user_id_beach_id_key") 
      || ex.getMessage().contains("duplicate key")) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "이미 찜한 해수욕장입니다."
    );
    pd.setTitle("Duplicate Favorite");
    return pd;
  }
  
  // 기타 제약 위반
  ProblemDetail pd = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "데이터 무결성 제약 위반입니다."
  );
  pd.setTitle("Data Integrity Violation");
  return pd;
}
```

**장점:**
- ✅ 서비스 코드가 깔끔 (비즈니스 로직에만 집중)
- ✅ 예외 처리가 한곳에 집중 (유지보수성 ↑)
- ✅ 배치 최적화 유지 (불필요한 flush 없음)
- ✅ 일관된 예외 응답 (ProblemDetail)
- ✅ 동시성 문제 해결 (커밋 시점 예외도 잡힘)

**단점:**
- ⚠️ 예외 메시지로 UNIQUE 제약을 판별해야 함 (DB 종속적)

---

### 방법 B: `saveAndFlush()` + try-catch (특수 케이스)

**핵심 아이디어: "퇴근 전에 지금 당장 저장해!"**

메서드 내부에서 제어가 필요한 경우:

```java
@Transactional
@CacheEvict(value = "beachSummaries", key = "'user:' + #user.id")
public UserFavorite addFavorite(User user, UUID beachId) {
  // 이미 찜했는지 확인 (동시 요청 대비)
  if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
    throw new IllegalStateException("이미 찜한 해수욕장입니다.");
  }

  Beach beach = beachRepository.findById(beachId)
      .orElseThrow(() -> new IllegalArgumentException("해수욕장을 찾을 수 없습니다."));

  UserFavorite favorite = new UserFavorite(user, beach);

  try {
    // saveAndFlush(): 영속성 컨텍스트 등록 + 즉시 DB INSERT
    return favoriteRepository.saveAndFlush(favorite);
  } catch (DataIntegrityViolationException e) {
    // flush()가 메서드 내부에서 실행되므로 여기서 예외를 잡을 수 있음!
    throw new IllegalStateException("이미 찜한 해수욕장입니다.", e);
  }
}
```

**왜 작동하는가?**
```
실행 흐름:
1. saveAndFlush() 호출
   └─ 영속성 컨텍스트에 등록
   └─ flush() 즉시 실행 ← 여기서 DB INSERT!
   └─ UNIQUE 제약 위반 시 예외 발생
2. catch 블록이 예외를 잡음 (아직 메서드 안에 있으므로)
3. IllegalStateException으로 변환하여 던짐
4. return (메서드 종료)
5. AOP 프록시가 트랜잭션 커밋 (이미 flush됨, 할 일 없음)
```

**장점:**
- ✅ 메서드 내부에서 예외를 확실히 잡을 수 있음
- ✅ DB 독립적 (예외 메시지 파싱 불필요)
- ✅ 테스트하기 쉬움

**단점:**
- ⚠️ 즉시 flush하므로 배치 최적화 불가
- ⚠️ 서비스 코드에 예외 처리 로직 산재
- ⚠️ 정상 흐름을 예외로 제어 (성능 저하 가능)

---

### 방법 C: 메서드 전체를 try로 감싸기 (작동 안 함) ❌

**이 방법은 실제로 작동하지 않습니다!**

```java
try {
  // ... 로직
  return favoriteRepository.save(favorite); // ← return하면 try-catch 소멸
} catch (DataIntegrityViolationException e) {
  // 메서드 종료 후 커밋 시점 예외는 여기 도달 안 함!
  throw new IllegalStateException("이미 찜한 해수욕장입니다.");
}
```

**왜 안 되는가?**
- `return` 실행 → 메서드 종료 → **호출 스택에서 pop** → try-catch 소멸
- AOP 프록시의 커밋은 메서드 종료 **후**에 발생
- 커밋 시점 예외는 이미 사라진 try-catch가 잡을 수 없음

---

### 방법 D: `@Transactional` 경계 조정 (복잡함, 비권장)

`TransactionTemplate` 등 프로그래밍 방식으로 트랜잭션을 관리하면 가능하지만:
- 코드 복잡도 증가
- Spring 선언적 트랜잭션 이점 상실
- 실무에서 거의 사용 안 함

## 교훈

### 1. JPA Write-Behind의 함정 (그리고 예외)
- `save()` 호출 ≠ 즉시 DB INSERT **(일반적으로)**
- 실제 INSERT는 **트랜잭션 커밋 시점(flush)**에 배치로 실행
- **메서드가 종료되면 try-catch는 스택에서 pop되어 사라짐**
- 메서드 종료 후 커밋 시점의 예외는 메서드 내부 try-catch가 잡을 수 없음

**⚠️ 예외 케이스: flush가 메서드 중간에 발생하는 경우**
- **`GenerationType.IDENTITY`**: DB에서 PK를 받아와야 하므로 `save()` 즉시 INSERT 발생 → try-catch로 잡힘
- **Auto flush 트리거**: JPQL/Query 실행 전 정합성을 위해 자동 flush → 메서드 중간에 예외 발생 가능
- **현재 프로젝트**: `GenerationType.AUTO` (UUID, 시퀀스) → 커밋 시점 flush ✅

### 2. 해결책: `saveAndFlush()` vs `save()`

| 비교 | `save()` | `saveAndFlush()` |
|------|----------|------------------|
| **동작** | 영속성 컨텍스트 등록만 | 등록 + 즉시 flush |
| **INSERT 시점** | 커밋 시점 (메서드 종료 후) | 메서드 실행 중 |
| **예외 발생 시점** | 메서드 종료 후 | 메서드 실행 중 |
| **try-catch로 잡기** | ❌ 불가능 | ✅ 가능 |
| **성능** | 배치 최적화 가능 | 즉시 실행 |
| **실무 적합성** | 일반적 (GlobalExceptionHandler 권장) | 특수 케이스 (메서드 내부 제어 필요) |

### 3. 호출 스택의 생명주기
```
[메서드 실행 중]
  try {
    비즈니스 로직
    save() or saveAndFlush()
    return ← 여기서 호출 스택 pop!
  } catch (Exception e) { ... }  ← 메서드 종료 시 소멸
  
[메서드 종료 후 - AOP 프록시 영역]
  트랜잭션 커밋 → flush() → INSERT
  └─ 여기서 예외 발생하면?
     └─ 이미 사라진 try-catch는 잡을 수 없음!
```

### 4. 동시성 문제는 다층 방어 전략
```
Layer 1: Application Level exists 체크 → 빠른 실패
Layer 2: DB UNIQUE 제약 → 최종 안전망
Layer 3: saveAndFlush + 예외 처리 → Graceful Degradation
```

### 5. `@Transactional`과 예외 처리의 오해
- ❌ **오해**: "메서드 전체를 try로 감싸면 커밋 시점 예외도 잡힌다"
- ✅ **현실**: 메서드가 return되면 try-catch는 스택에서 사라짐
- ✅ **해결**: `saveAndFlush()`로 메서드 내부에서 flush 강제 실행

### 6. Defense in Depth vs 단순성
- "DB에 UNIQUE 제약이 있으니 Application Level 체크는 불필요하다" ❌
- "이중 체크는 중복이다" ❌
- **각 레이어는 다른 목적을 가짐:**
  - Application: 성능 최적화 + 명확한 의도 표현
  - DB: 데이터 무결성 보장 (최종 안전망)
  - saveAndFlush: 예외를 메서드 내부로 가져와 제어 가능하게 함

## 적용된 해결책

### ✅ 최종 채택: 방법 A (GlobalExceptionHandler) ⭐

**서비스 코드 (간결하게 유지):**
```java
@Transactional
@CacheEvict(value = "beachSummaries", key = "'user:' + #user.id")
public UserFavorite addFavorite(User user, UUID beachId) {
  // Pre-check: 이미 찜했는지 확인 (동시 요청 대부분 차단)
  if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
    throw new IllegalStateException("이미 찜한 해수욕장입니다.");
  }

  Beach beach = beachRepository.findById(beachId)
      .orElseThrow(() -> new IllegalArgumentException("해수욕장을 찾을 수 없습니다."));

  UserFavorite favorite = new UserFavorite(user, beach);
  return favoriteRepository.save(favorite); // 배치 최적화 유지
}
```

**GlobalExceptionHandler 추가:**
```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
  String message = ex.getMessage();
  
  // UNIQUE 제약 위반 판별
  if (message != null && 
      (message.contains("user_favorites_user_id_beach_id_key") 
       || message.contains("duplicate key value violates unique constraint"))) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "이미 찜한 해수욕장입니다."
    );
    pd.setTitle("Duplicate Favorite");
    return pd;
  }
  
  // 기타 제약 위반
  ProblemDetail pd = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "데이터 무결성 제약 위반입니다."
  );
  pd.setTitle("Data Integrity Violation");
  return pd;
}
```

**결과:**
- ✅ 동시 요청 테스트 통과: `successCount=1`, `failCount=9`
- ✅ 서비스 코드 간결: 비즈니스 로직에만 집중
- ✅ 배치 최적화 유지: 불필요한 flush 없음
- ✅ 일관된 예외 처리: 한곳에서 관리
- ✅ 확장성: 다른 제약 위반도 쉽게 추가 가능

---

### 대안: 방법 B (saveAndFlush) 주석 처리

서비스 코드에 대안 방법을 주석으로 남겨둠:
```java
// 대안: saveAndFlush() + try-catch (메서드 내부에서 예외 처리)
// try {
//   return favoriteRepository.saveAndFlush(favorite);
// } catch (DataIntegrityViolationException e) {
//   throw new IllegalStateException("이미 찜한 해수욕장입니다.", e);
// }
```

**언제 대안을 사용하나?**
- 메서드 내부에서 즉시 예외를 처리해야 하는 특수한 경우
- DB 독립적 예외 처리가 필요한 경우
- 단위 테스트 용이성이 중요한 경우

---

### 동시성 처리 전략 (3단계 방어)

```
┌─────────────────────────────────────────────────────┐
│ Layer 1: Pre-check (exists)                         │
│ └─ 99% 중복 요청 차단 (빠른 실패, 성능 최적화)        │
└─────────────────────────────────────────────────────┘
                    ↓ (Race condition 1% 통과)
┌─────────────────────────────────────────────────────┐
│ Layer 2: DB UNIQUE 제약                              │
│ └─ 트랜잭션 커밋 시점에 최종 검증 (데이터 무결성)      │
└─────────────────────────────────────────────────────┘
                    ↓ (DataIntegrityViolationException)
┌─────────────────────────────────────────────────────┐
│ Layer 3: GlobalExceptionHandler                     │
│ └─ 409 CONFLICT 응답 (사용자 친화적 에러 메시지)      │
└─────────────────────────────────────────────────────┘
```

## 관련 문서
- [favorite-refactoring-plan.md](../favorite-refactoring-plan.md) - 찜 기능 리팩토링 계획
- [service-layer-test-guide.md](../service-layer-test-guide.md) - 서비스 레이어 테스트 가이드
- [UserFavoriteService.java](../../src/main/java/com/beachcheck/service/UserFavoriteService.java) - 수정된 서비스 코드

## 검증 방법
```bash
# 통합 테스트 실행
./gradlew test --tests UserFavoriteServiceIntegrationTest.concurrentAddFavorite_handlesCorrectly

# 전체 테스트 실행
./gradlew test
```

예상 결과:
- `successCount = 1` (1개만 성공)
- `failCount = 9` (9개는 IllegalStateException으로 처리)
- DB에 1개만 저장됨
