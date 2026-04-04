# Spring Lambda Function URL 호출 시 SignatureDoesNotMatch

## 0) 메타 정보

- **Mode:** `DEV`
- **Status:** `Resolved`
- **작성자:** 박건우(@geonusp)
- **해결 날짜(선택):** 2026-04-03
- **작성일(필수):** 2026-03-31
- **컴포넌트:** infra (Lambda Function URL, SigV4, AWS SDK v2, Spring RestClient, Apache HttpClient 5)
- **환경:** local
- **관련 이슈/PR:** feat: PB-104 CongestionClient Lambda Function URL SigV4 인증 적용
- **키워드:** SignatureDoesNotMatch, HttpClientErrorException$Forbidden, Lambda Function URL, AWS_IAM, SigV4, Content-Length, Apache HttpClient 5, ProcessCredentialsProvider

---

## 1) 요약 (3줄)

- **무슨 문제였나:** Spring 스케줄러가 Lambda Function URL(`AWS_IAM`)을 호출할 때 항상 HTTP 403 `SignatureDoesNotMatch`가 발생했다.
- **원인:** 빈 `GET` 요청을 SigV4로 서명할 때 `content-length: 0`을 canonical request에 포함했지만, Spring `HttpComponentsClientHttpRequest`가 실제 전송 직전 이 헤더를 제거해 (`content-length: null`) AWS가 다른 canonical request를 재계산했다.
- **해결:** signer 입력에서 `Content-Length`, `Transfer-Encoding` 같은 전송 계층 관리 헤더를 제외하고, 빈 GET에는 body를 붙이지 않도록 수정했다.

## 1-1) 학습 포인트 (최대 3개)

- **Fast checks (3):** (1) `Authorization`의 `SignedHeaders`에 `content-length` 또는 `transfer-encoding`이 들어가는지 먼저 본다. (2) `signed -> spring -> apache` 레이어별 비교 로그로 요청이 언제 달라지는지 끊어서 확인한다. (3) 403이 404/200으로 바뀌면 인증 문제는 해결되고 다음 이슈는 비즈니스 레이어다.
- **Rule of thumb (필수, 1문장):** SigV4에서는 실제 전송 시 동일하게 나갈 수 없는 전송 계층 관리 헤더를 서명 대상에 넣지 말아야 한다.
- **Anti-pattern (선택):** 래핑된 `HttpRequest` 헤더만 보고 canonical request가 맞다고 판단하지 말 것. 반드시 request factory/HttpClient 경계에서 다시 확인해야 한다.

---

## 2) 증상 (Symptom)

### 관측된 현상
- `BeachConditionScheduler`가 혼잡도 조회를 수행할 때마다 `CongestionClient` 호출이 실패했다.
- 해결 전에는 AWS가 HTTP 403을 반환하고 본문에 `The request signature we calculated does not match the signature you provided.`를 내려줬다.
- 해결 후 동일 호출은 더 이상 403이 아니라 Lambda 애플리케이션 레벨 404 `해수욕장을 찾을 수 없음`을 반환했다. 이는 SigV4 인증 단계는 통과했다는 뜻이다.
- 디버깅 과정에서 Fiddler/프록시를 켠 경우, 별도로 `PKIX path building failed`, AWS CLI `CERTIFICATE_VERIFY_FAILED` 같은 TLS 오류도 관측됐다. 이 오류는 본 증상의 파생 이슈였다.

### 에러 메시지 / 스택트레이스 (필수)
해결 전 403:
```text
2026-03-30T20:46:06.405+09:00  WARN 83908 --- [beach-complex] [   scheduling-1] com.beachcheck.client.CongestionClient   : 혼잡도 조회 실패 - beachCode=SONGJEONG

org.springframework.web.client.HttpClientErrorException$Forbidden: 403 Forbidden: "{"message":"The request signature we calculated does not match the signature you provided. Check your AWS Secret Access Key and signing method. Consult the service documentation for details."}"
```

원인 확인 로그:
```text
2026-04-03T00:26:03.009+09:00 DEBUG ... AwsSigV4Interceptor : ... SignedHeaders=content-length;host;x-amz-date;x-amz-security-token ...
2026-04-03T00:26:03.010+09:00  WARN ... SigV4Diagnostics  : signed -> spring mismatch: [contentLengthHeader=0 -> null, entityContentLength=0 -> null, ...]
```

해결 후 로그:
```text
2026-04-03T00:38:05.726+09:00 DEBUG ... AwsSigV4Interceptor : ... SignedHeaders=host;x-amz-date;x-amz-security-token ...
2026-04-03T00:38:05.726+09:00 DEBUG ... SigV4Diagnostics   : signed -> spring matched
2026-04-03T00:38:05.726+09:00 DEBUG ... SigV4Diagnostics   : spring -> apache matched
2026-04-03T00:38:05.927+09:00  WARN ... CongestionClient   : org.springframework.web.client.HttpClientErrorException$NotFound: 404 Not Found: "{"detail":"해수욕장을 찾을 수 없음"}"
```

### 발생 조건 / 빈도
- **언제:** 로컬에서 Spring Boot 앱 실행 후 스케줄러가 혼잡도 조회를 수행할 때
- **빈도:** 해결 전에는 항상 재현

---

## 3) 영향 범위 (DEV 기준 최소 작성)

- **영향받는 기능:** `BeachConditionScheduler` -> `CongestionClient` -> Lambda Function URL 혼잡도 조회 전체
- **영향받는 사용자/데이터:** 로컬 개발/테스트 환경만 영향. 운영 데이터 영향 없음
- **심각도(개발 단계):** `High`
  - High: 해결 전에는 외부 혼잡도 연동이 완전히 막혀 관련 기능 개발/테스트가 중단됐다.

---

## 4) 재현 방법 (Reproduction)

### 전제 조건
- **브랜치/커밋:** `feat/PB-104-congestionclient-lambda-url-sigv4` / `01bf6c5b78ac8919b7e8e8c67b6c11d974cf5a1a`
- **의존성/버전:** Java 21, Spring Boot 3.3.0, Gradle, AWS SDK v2 BOM 2.42.23, Apache HttpClient 5.3.1
- **환경변수/설정:**
  - `AWS_PROFILE=gunwoo`
  - `AWS_REGION=us-east-1`
  - `AWS_CLI_COMMAND=C:\Program Files\Amazon\AWSCLIV2\aws.exe`
  - `CONGESTION_BASE_URL=https://vfhbaio7buzpf7frsaqcwvtyd40lzfso.lambda-url.us-east-1.on.aws`
  - 프록시/Fiddler 설정은 실제 재현 확인 시 제거해야 한다

### 재현 절차
1. `aws sso login --profile gunwoo` 로 SSO 세션을 갱신한다.
2. Spring Boot 애플리케이션을 실행한다.
3. 스케줄러가 `CongestionClient.fetchCurrent()`를 수행하거나 동일 경로를 호출하게 만든다.
4. 해결 전에는 403 `SignatureDoesNotMatch`가 발생했다.
5. 수정 후 동일 절차에서는 403이 사라지고 Lambda 핸들러 응답(예: 404 `해수욕장을 찾을 수 없음`)이 반환된다.

### 재현 입력/데이터
- 요청/페이로드/SQL/테스트 케이스:
```text
GET https://vfhbaio7buzpf7frsaqcwvtyd40lzfso.lambda-url.us-east-1.on.aws/congestion/current?beach_id=IMRANG

또는

GET https://vfhbaio7buzpf7frsaqcwvtyd40lzfso.lambda-url.us-east-1.on.aws/congestion/current?beach_id=SONGJEONG
```

---

## 5) 원인 분석 (Root Cause)

### 가설 목록

#### 배제된 가설 (요약)

| 가설 | 의심 이유 | 배제 근거 |
|------|-----------|-----------|
| 1: host mismatch | Lambda가 수신한 `Host` 기준으로 검증하므로 host가 다르면 즉시 불일치 | `uriHost` = `signedHost` 로그 확인 |
| 2: signing scope 오류 | `region`/`service` 값이 다르면 scope 오류 | `20260402/us-east-1/lambda/aws4_request` 로그 확인 |
| 3: session token 누락 | SSO temporary credentials는 session token 필수 | `X-Amz-Security-Token` 로그 존재, 테스트 통과 |
| 4: 서명 시점이 너무 이르다 | 서명 후 헤더/URI가 바뀌면 canonical request 깨짐 | 시점 문제 아님. 실제 원인은 가설 5 |
| 6: `DefaultCredentialsProvider` 오염 | 환경변수/프로파일 우선순위로 의도와 다른 credentials 선택 가능 | `ProcessCredentialsProvider`로 강제 후에도 403 유지 |
| 7: profile이 root/IAM/SSO 혼재 | `root`와 SSO 설정이 같은 profile에 섞일 가능성 | `beach-ai`(root)와 `gunwoo`(SSO)가 별도 profile로 분리 확인 |
| 8: CLI와 Java SDK가 다른 credentials 사용 | CLI는 정상인데 SDK 서명만 계속 실패 | credentials 통일 후에도 403 유지. 원인은 canonical request 불일치 |
| 9: Fiddler/프록시가 직접 원인 | 디버깅 중 TLS 오류 다수 발생 | 프록시 해제 후에도 403 재현. 디버깅 환경 오염 문제 |

#### 확정된 가설 (상세)

### 근거 (로그/코드/설정/DB 상태)
- **로그/지표:**
  - `uriHost`와 `signedHost`는 일관되게 동일했다.
  - `signingScope=20260402/us-east-1/lambda/aws4_request`가 확인됐다.
  - `X-Amz-Security-Token`이 로그에 존재했다.
  - `Using AWS CLI export-credentials provider for profile=gunwoo` 시작 로그가 확인됐다.
  - 2026-04-03 00:26:03 KST 로그에서 `SignedHeaders=content-length;host;x-amz-date;x-amz-security-token` 상태였고, 곧바로 `signed -> spring mismatch: [contentLengthHeader=0 -> null, entityContentLength=0 -> null]`가 관측됐다.
  - 2026-04-03 00:38:05 KST 로그에서는 `SignedHeaders=host;x-amz-date;x-amz-security-token` 상태로 바뀐 뒤 `signed -> spring matched`, `spring -> apache matched`가 모두 확인됐다.
  - 같은 2026-04-03 00:38:05.927+09:00 요청은 더 이상 403이 아니라 404 `해수욕장을 찾을 수 없음`을 반환했다.
- **코드 포인트:**
  - `src/main/java/com/beachcheck/client/CongestionClient.java`: `GET /congestion/current?beach_id=...` 호출
  - `src/main/java/com/beachcheck/client/AwsSigV4Interceptor.java`: signer 입력에서 `Content-Length`, `Transfer-Encoding` 제외, 빈 safe method body 미부착
  - `src/main/java/com/beachcheck/config/AwsConfig.java`: `ProcessCredentialsProvider`로 AWS CLI `export-credentials` 경로 사용
  - `src/main/java/com/beachcheck/client/SigV4Diagnostics.java` **(진단용, 이후 제거)**: `signed -> spring -> apache` 레이어별 비교 로그
  - `src/main/java/com/beachcheck/client/SigV4DiagnosticHttpClient.java` **(진단용, 이후 제거)**: Spring request factory 경계 스냅샷
  - `src/main/java/com/beachcheck/client/SigV4DiagnosticApacheRequestInterceptor.java` **(진단용, 이후 제거)**: Apache execution chain 스냅샷
- **설정 포인트:**
  - `C:\Users\pro\.aws\config` 에는 다음 두 프로파일이 공존했다.
    - `[profile beach-ai] login_session = arn:aws:iam::911107441116:root`
    - `[profile gunwoo] sso_session = gunwoo`, `sso_account_id = 911107441116`, `sso_role_name = AdministratorAccess`
  - `C:\Users\pro\.aws\credentials` 파일은 존재하지 않았다.
  - `aws sts get-caller-identity --profile gunwoo --region us-east-1 --no-verify-ssl` 결과는 `arn:aws:sts::911107441116:assumed-role/AWSReservedSSO_AdministratorAccess_.../gunwoo` 였다.
- **DB 상태:** 해당 없음

### 확정 가설 상세

#### 가설 5: 빈 GET의 `content-length` 서명 문제
- **의심 이유:** 초기 로그에서 `SignedHeaders=content-length;host;x-amz-date;x-amz-security-token` 이 찍혔고, 빈 GET에서는 `content-length: 0`이 실제 전송 계층에서 빠질 수 있다고 의심했다.
- **확인 방법:** `signed -> spring` 비교 로그로 실제 차이를 확인한 뒤, signer 입력에서 `Content-Length`, `Transfer-Encoding`을 제외하고 빈 safe method에는 body stream도 붙이지 않도록 수정했다.
- **결과:** **확정 원인.** 수정 후 `SignedHeaders=host;x-amz-date;x-amz-security-token` 으로 바뀌었고 403이 사라졌다.

#### 가설 10: 서명된 요청과 실제 전송 요청이 다르다
- **의심 이유:** AWS 공식 문서도 프록시 또는 클라이언트 핸들러가 헤더를 수정할 수 있다고 안내한다.
- **확인 방법:** `SigV4Diagnostics`로 `signed -> spring -> apache` 비교를 추가했다.
- **결과:** **가설 5 보완.** 가설 5의 원인이 파이프라인 어느 레벨에서 발생하는지 특정했다. 차이는 Apache 레벨이 아니라 Spring request factory 경계에서 발생했고, 실제 차이 항목은 `content-length: 0` 제거였다.

### 최종 원인 (One-liner)
- 빈 `GET` 요청에 대해 `AwsSigV4Interceptor`가 `content-length: 0`을 canonical request에 포함해 서명했지만, Spring `HttpComponentsClientHttpRequest`가 실제 전송 직전 그 헤더를 제거해 AWS가 다른 canonical request를 재계산하면서 `SignatureDoesNotMatch`가 발생했다.

---

## 6) 해결 (Fix)

### 해결 전략
- **유형:** `Code change`
- **접근:** signer 입력을 실제 outbound request와 맞추고, 레이어별 비교 로그로 수정 효과를 즉시 검증하는 방향으로 해결했다.

### 변경 사항

#### 영구 변경
- `AwsSigV4Interceptor`
  - signer 입력 생성 시 `Content-Length`, `Transfer-Encoding`을 제외
  - 빈 safe method(`GET`, `HEAD`, `OPTIONS`, `TRACE`)에는 body `contentStreamProvider`를 붙이지 않음
- `AwsSigV4InterceptorTest`
  - 빈 GET 요청에서 `Authorization`의 `SignedHeaders`에 `content-length`가 포함되지 않는 테스트 추가
- 기존 `AwsConfig`의 `ProcessCredentialsProvider` 경로는 그대로 유지

#### 진단용 (이후 제거 예정)
> 원인을 레이어별로 특정하기 위해 추가한 임시 도구. 디버깅 완료 후 제거한다.

- `SigV4Diagnostics`: `signed -> spring -> apache` 레이어별 스냅샷 및 diff 로그. 전송 계층 관리 헤더가 `SignedHeaders`에 없으면 해당 차이는 불일치로 보지 않도록 조정
- `SigV4DiagnosticHttpClient`: Spring request factory 경계에서 요청 스냅샷 캡처
- `SigV4DiagnosticApacheRequestInterceptor`: Apache execution chain 내부에서 요청 스냅샷 캡처
- `CongestionClientConfig`: 위 진단 도구를 붙이기 위해 `HttpComponentsClientHttpRequestFactory`로 교체. 진단 도구 제거 시 함께 정리 대상

### 주의/부작용
- 래핑된 최종 `HttpRequest` 객체 자체에는 원본 `Content-Length: 0` 헤더가 남아 있을 수 있다. 중요한 것은 헤더 존재 자체가 아니라 `Authorization`의 `SignedHeaders`에 포함되는지 여부다.
- 수정 후 동일 요청이 404 `해수욕장을 찾을 수 없음`을 반환할 수 있다. 이는 SigV4 회귀가 아니라 Lambda 비즈니스 로직까지 요청이 도달했다는 뜻이다.
- 향후 `POST`/`PUT` 등에서 전송 계층 관리 헤더를 다시 서명 대상에 넣으면 비슷한 문제가 재발할 수 있다.

---

## 7) 검증 (Verification)

### 해결 확인
- [x] 동일 재현 절차에서 더 이상 403 `SignatureDoesNotMatch`가 발생하지 않음
- [x] 관련 테스트 통과 (unit)
- [x] 로컬 실행/빌드 정상

### 실행한 커맨드/테스트
```bash
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.beachcheck.client.AwsSigV4InterceptorTest"
aws sso login --profile gunwoo
aws sts get-caller-identity --profile gunwoo --region us-east-1 --no-verify-ssl
```

### 추가 확인(선택)
- 성능/부하에 영향 없는지: 해당 없음
- 회귀 가능성 체크:
  - 2026-04-03 00:38:05 KST 로그에서 `SignedHeaders=host;x-amz-date;x-amz-security-token` 확인
  - 같은 시각 로그에서 `signed -> spring matched`, `spring -> apache matched` 확인
  - 같은 요청이 403이 아니라 404 `해수욕장을 찾을 수 없음`으로 변경된 것 확인

---

## 8) 재발 방지 (Prevention)

### 방지 조치 체크리스트
- [x] **테스트 추가**: 빈 GET 요청이 `content-length`를 SigV4 `SignedHeaders`에 포함하지 않는 단위 테스트 추가
- [x] **검증 로직 추가**: `signed -> spring -> apache` 비교 로그 추가 (진단용, 이후 제거 예정)
- [ ] **가드레일**: `app.aws.profile` 가 설정됐는데 `app.aws.cli-command` 실행이 불가능하면 즉시 fail-fast
- [x] **로깅 개선**: SigV4 핵심 진단 헤더/스코프 로그 추가
- [x] **문서화**: 본 문서 갱신
- [ ] **알림/모니터링**(배포 후): DEV 이슈이므로 현재 해당 없음

### 남은 작업(Action Items)
- [ ] Lambda 쪽 `beach_id` 매핑 확인 (`SONGJEONG`, `ILGWANG` 등)
- [ ] 진단용 `SigV4Diagnostics` 로그를 상시 유지할지, 문제 해결 후 축소할지 결정
- [ ] `AWS_CLI_COMMAND` 기본값(`aws`) fallback을 유지할지 fail-fast로 바꿀지 결정

---

## 9) 참고 자료 (References)

- AWS IAM User Guide: [AWS API 요청에 대한 Signature Version 4 서명 문제 해결](https://docs.aws.amazon.com/ko_kr/IAM/latest/UserGuide/reference_sigv-troubleshooting.html)
- AWS CLI Command Reference: [aws configure export-credentials](https://docs.aws.amazon.com/cli/latest/reference/configure/export-credentials.html)
- AWS SDKs and Tools Reference Guide: [Process credential provider](https://docs.aws.amazon.com/sdkref/latest/guide/feature-process-credentials.html)
- AWS SDKs and Tools Reference Guide: [IAM Identity Center credential provider](https://docs.aws.amazon.com/sdkref/latest/guide/feature-sso-credentials.html)
- RFC 9112 §6 Message Body: [https://www.rfc-editor.org/rfc/rfc9112#section-6](https://www.rfc-editor.org/rfc/rfc9112#section-6) — `Content-Length`·`Transfer-Encoding`이 메시지 프레이밍 헤더임을 정의. "Request message framing is independent of method semantics"
