# 예약 동시성 중복 예약이 400으로 내려오는 문제

| 날짜 | 작성자 | 변경 내용 |
|:---:|:---:|:---|
| 2026-01-20 | - | 문서 생성 |

## 문제 상황

### 증상
- 예약 동시성 테스트에서 일부 요청이 409가 아닌 400으로 실패.
- 테스트 실패 메시지:
  ```
  java.lang.AssertionError: Unexpected status: 400
  at ReservationControllerIntegrationTest.java:619
  ```

### 재현 방법
```
.\gradlew.bat test --tests com.beachcheck.integration.ReservationControllerIntegrationTest
```

## 원인

### 1) 중복 예약 검증 경로 차이
예약 생성은 두 경로로 중복을 감지한다.
- 서비스 선행 체크: `existsByUserIdAndBeachIdAndReservedAt` → `RESERVATION_DUPLICATE`(409)
- DB UNIQUE 제약: `uk_reservation_user_beach_time` → `DataIntegrityViolationException`

동시성 상황에서는 선행 체크를 통과한 뒤 DB UNIQUE 위반이 발생할 수 있다.

### 2) 예외 매핑 누락
`DataIntegrityViolationException`은 기본적으로 400으로 매핑되었고,
예약 중복 제약(`uk_reservation_user_beach_time`)은 409로 변환되지 않았다.

## 해결 과정

### 1) 제약명 기반 매핑 추가
`GlobalExceptionHandler`에서 예약 UNIQUE 제약을 409로 매핑:

```java
if ("uk_reservation_user_beach_time".equals(constraintName)
    || (message != null && message.contains("uk_reservation_user_beach_time"))) {
  ProblemDetail pd =
      ProblemDetail.forStatusAndDetail(
          HttpStatus.CONFLICT, ErrorCode.RESERVATION_DUPLICATE.getDefaultMessage());
  pd.setTitle(ErrorCode.RESERVATION_DUPLICATE.getCode());
  pd.setProperty("code", ErrorCode.RESERVATION_DUPLICATE.getCode());
  pd.setProperty("details", null);
  pd.setProperty("constraintName", "uk_reservation_user_beach_time");
  return pd;
}
```

### 2) 제약명 파싱 보강
드라이버 메시지 변화에 대비해 `ConstraintViolationException`에서
`constraintName`을 추출하도록 보강.

## 결과 검증
```
.\gradlew.bat test --tests com.beachcheck.integration.ReservationControllerIntegrationTest
```
결과: **BUILD SUCCESSFUL**

## 교훈
- 동시성 상황에서는 선행 체크가 무력화될 수 있으므로 DB 제약 기반 오류도
  API 계약으로 일관되게 변환해야 한다.

## 관련 파일
- `src/main/java/com/beachcheck/exception/GlobalExceptionHandler.java`
- `src/main/resources/db/migration/V8__create_reservations_table.sql`
- `src/test/java/com/beachcheck/integration/ReservationControllerIntegrationTest.java`
