# Spring + AWS SDK v2 SSO 자격증명 로드 실패

## 0) 메타 정보

- **Mode:** `DEV`
- **Status:** `Resolved`
- **작성자:** 박건우(@geonusp)
- **작성일:** 2026-03-30
- **컴포넌트:** infra (AWS SDK v2, IAM Identity Center, AwsSigV4Interceptor)
- **환경:** local
- **관련 이슈/PR:** PB-104
- **키워드:** SdkClientException, Unable to load credentials, AWS_PROFILE, SSO, DefaultCredentialsProvider, sso module, ssooidc

---

## 1) 요약 (3줄)

- **무슨 문제였나:** Spring 스케줄러 실행 시 `SdkClientException: Unable to load credentials` 발생하여 Lambda 호출 불가
- **원인:** `build.gradle`에 `software.amazon.awssdk:sso` / `ssooidc` 모듈이 누락되어 SDK가 SSO 프로파일 자격증명을 해석하지 못함
- **해결:** `sso`, `ssooidc` 의존성 추가 후 `aws sso login --profile gunwoo` 재실행으로 해결

## 1-1) 학습 포인트

- **Fast checks (3):**
  (1) Log: `SdkClientException: Unable to load credentials from any of the providers` 키워드
  (2) Config: `aws sts get-caller-identity --profile {프로파일명} --region {AZ명}` 로 CLI 자격증명 정상 여부 먼저 확인
  (3) Infra: AWS CLI 정상 ≠ SDK 정상. SSO 캐시 토큰을 SDK가 읽을 수 있는지 별도 확인 필요
- **Rule of thumb (필수):** SSO 프로파일 사용 시 `sso` + `ssooidc` 두 모듈 모두 필수 — 하나라도 빠지면 `"Profile file contained no credentials"` 오류로 나타남
- **Anti-pattern:** `software.amazon.awssdk:auth` 하나만 추가하고 SSO 프로파일을 사용하는 것 — `auth`는 SSO 프로파일 파싱을 지원하지 않음

---

## 2) 증상 (Symptom)

### 관측된 현상
- `BeachConditionScheduler` 실행 시 매번 오류 발생
- `aws sts get-caller-identity --profile gunwoo --region us-east-1` 는 정상 동작

### 에러 메시지 / 스택트레이스

초기 오류 (프로파일명 `default` 탐색):
```text
software.amazon.awssdk.core.exception.SdkClientException: Unable to load credentials from any of the providers in the chain
AwsCredentialsProviderChain(credentialsProviders=[
  SystemPropertyCredentialsProvider(): Unable to load credentials from system settings.,
  EnvironmentVariableCredentialsProvider(): Unable to load credentials from system settings.,
  WebIdentityTokenFileCredentialsProvider(): AWS_WEB_IDENTITY_TOKEN_FILE must be set.,
  ProfileCredentialsProvider(profileName=default): Profile file contained no credentials for profile 'default',
  ContainerCredentialsProvider(): Neither AWS_CONTAINER_CREDENTIALS_FULL_URI or AWS_CONTAINER_CREDENTIALS_RELATIVE_URI are set.,
  InstanceProfileCredentialsProvider(): Failed to load credentials from IMDS.
])
```

`AWS_PROFILE=gunwoo` 환경변수 설정 후에도 동일 유형 오류 (프로파일은 탐색하지만 자격증명 해석 불가):
```text
ProfileCredentialsProvider(profileName="gunwoo", ...): Profile file contained no credentials for profile '"gunwoo"':
ProfileFile(sections=[profiles, sso-session, services],
  profiles=[
    Profile(name=beach-ai, properties=[login_session]),
    Profile(name=gunwoo, properties=[sso_session, sso_account_id, sso_role_name, region])
  ])
```

### 발생 조건 / 빈도
- **언제:** 매 :00, :30분 스케줄러 실행 시
- **빈도:** 항상

---

## 3) 영향 범위

- **영향받는 기능:** `BeachConditionScheduler` → `CongestionClient` → Lambda 호출 전체
- **영향받는 사용자/데이터:** 로컬 테스트 환경만
- **심각도(개발 단계):** `High` — Lambda 호출 자체가 불가능한 상태

---

## 4) 현재까지 시도한 것

### 시도 1 — IntelliJ Run Configuration 환경변수 설정

```
AWS_PROFILE = gunwoo
AWS_REGION  = us-east-1
```

→ 실패. 동일 오류 발생.

### 시도 2 — AWS SSO 로그인 재시도

```bash
aws sso login --profile gunwoo
# 브라우저 로그인 완료
aws sts get-caller-identity --profile gunwoo --region us-east-1
# 정상 응답 확인
```

→ CLI는 정상이나 Spring SDK는 여전히 실패.

---

## 5) 원인 분석 (Root Cause)

### 가설 목록

- [x] 가설 1: `DefaultCredentialsProvider`가 `AWS_PROFILE` 환경변수를 IntelliJ Run Configuration에서 못 읽음
  → 부분 원인. 환경변수 설정 후 프로파일 탐색은 됐으나 자격증명 로드는 여전히 실패
- [ ] 가설 2: AWS SDK v2가 SSO 캐시 토큰(`~/.aws/sso/cache/`)을 읽지 못함
- [ ] 가설 3: `ProfileCredentialsProvider`가 `default` 프로파일만 탐색하고 `gunwoo` 프로파일을 탐색하지 않음
- [x] 가설 4: `build.gradle`에 `software.amazon.awssdk:sso` / `ssooidc` 모듈 누락
  → **실제 원인**. SDK v2는 SSO 프로파일 처리를 별도 모듈로 분리. 해당 모듈 없으면 `sso_session` 프로파일을 파싱조차 못 함

### 근거

- `gunwoo` 프로파일 구조: `properties=[sso_session, sso_account_id, sso_role_name, region]` — SSO 기반 프로파일
- `build.gradle`에 `software.amazon.awssdk:auth`, `regions`만 있고 `sso`, `ssooidc`는 미포함
- AWS SDK v2 공식 문서: SSO 프로파일 사용 시 `sso`·`ssooidc` 모듈 필수 명시
- `sso`/`ssooidc` 추가 후 자격증명 로드 성공 확인

### 최종 원인 (One-liner)

`software.amazon.awssdk:sso` / `ssooidc` 모듈 누락으로 SDK가 SSO 타입 프로파일을 해석하지 못해 자격증명 로드 실패

---

## 6) 남은 시도

- [x] PowerShell에서 직접 환경변수 설정 후 `./gradlew bootRun` 실행
  → 프로파일 탐색은 됐으나 자격증명 로드 실패 (sso 모듈 누락이 원인)
- [ ] `~/.aws/config`에 `[default]` 프로파일 추가 (`gunwoo`와 동일 내용)
- [ ] `~/.aws/sso/cache/` 디렉터리에 토큰 파일 존재 여부 확인

---

## 7) 해결 (Resolution)

### 적용한 조치

**`build.gradle`에 SSO 모듈 추가:**

```groovy
// AWS SDK v2 — Lambda Function URL SigV4 인증
implementation platform('software.amazon.awssdk:bom:2.42.23')
implementation 'software.amazon.awssdk:auth'
implementation 'software.amazon.awssdk:regions'
implementation 'software.amazon.awssdk:sso'       // SSO 프로파일 자격증명 로더
implementation 'software.amazon.awssdk:ssooidc'   // OIDC 토큰 갱신 지원
```

**SSO 로그인 (세션 만료 시마다 필요):**

```bash
aws sso login --profile gunwoo
```

### 해결 확인

- `SdkClientException: Unable to load credentials` 오류 해소
- `ProfileCredentialsProvider`가 `gunwoo` SSO 프로파일에서 자격증명 정상 로드

### 재발 조건

SSO 세션은 만료 기간이 있으므로 토큰 만료 시 `aws sso login --profile gunwoo` 재실행 필요.
`aws sts get-caller-identity --profile gunwoo` 로 세션 유효 여부 사전 확인 가능.

---

