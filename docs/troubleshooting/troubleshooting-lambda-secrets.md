# Lambda + Secrets Manager 연동 실패 (Mangum 이벤트 형식 오류 / 리전 불일치)

## 0) 메타 정보

- **Mode:** `DEV`
- **Status:** `Resolved`
- **작성자:** 박건우(@geonusp)
- **해결 날짜:** 2026-03-28
- **작성일:** 2026-03-28
- **컴포넌트:** infra (Lambda, Secrets Manager)
- **환경:** dev
- **관련 이슈/PR:** 외부 AI 서버 레포 [`Beach-complex/beach_complex_ai`](https://github.com/Beach-complex/beach_complex_ai), 문제 발생 기준 커밋 [`f613dc4`](https://github.com/Beach-complex/beach_complex_ai/commit/f613dc4bcefaba6c33c885b7ac3c198774a85209)
- **키워드:** Mangum, RuntimeError, AccessDeniedException, AWS_REGION, region mismatch, reserved env var

---

## 1) 요약 (3줄)

- **무슨 문제였나:** Lambda 콘솔 테스트에서 Mangum 이벤트 인식 실패 → 리전 불일치로 Secrets Manager AccessDeniedException → 예약 환경변수 등록 시도 오류로 연속 3단계 실패
- **원인:** 테스트 이벤트가 Mangum이 지원하는 HTTP 형식이 아니었고, secrets_client 초기화 시 리전 기본값(`ap-northeast-2`)이 실제 시크릿 리전(`us-east-1`)과 달라 AccessDeniedException 발생
- **해결:** API Gateway v2 이벤트 형식으로 교체, `os.environ["AWS_REGION"]`으로 코드 수정

## 1-1) 학습 포인트

- **Fast checks (3):**
  (1) Log: Lambda 에러 메시지에서 `Mangum` / `AccessDeniedException` 키워드로 단계 구분
  (2) Config/Env: SECRET_NAME 값과 Secrets Manager 보안 암호 이름 일치 여부, boto3 클라이언트 초기화 리전과 시크릿 리전 일치 여부
  (3) Infra/Dependency: `AWS_REGION` 환경변수 기본값이 코드에 하드코딩되어 있지 않은지 확인
- **Rule of thumb (필수):** Lambda 콘솔 테스트는 반드시 API Gateway v2 형식(`version: "2.0"` + `requestContext.http`)으로 해야 Mangum이 정상 동작한다
- **Anti-pattern:** `AWS_REGION`을 Lambda 환경변수로 직접 등록하려 하면 예약어 오류 발생 — Lambda가 자동 주입하므로 `os.environ["AWS_REGION"]`으로 바로 읽어야 함

---

## 2) 증상 (Symptom)

### 관측된 현상
- Lambda 콘솔 테스트 시 500 에러 반환
- Mangum이 이벤트 핸들러를 인식하지 못함
- Secrets Manager 조회 시 AccessDeniedException (리전 불일치)
- 환경변수 `AWS_REGION` 저장 시 예약어 오류

### 에러 메시지 / 스택트레이스

**1단계 — Mangum 이벤트 인식 실패:**
```text
[ERROR] RuntimeError: The adapter was unable to infer a handler to use for the event.
This is likely related to how the Lambda function was invoked.
File "/var/task/mangum/adapter.py", line 71, in __call__
    handler = self.infer(event, context)
File "/var/task/mangum/adapter.py", line 56, in infer
    raise RuntimeError(
```

**2단계 — 리전 불일치로 인한 Secrets Manager AccessDeniedException:**
```text
{
  "statusCode": 500,
  "body": "{\"detail\":{\"msg\":\"OpenWeather API 키 조회 실패: Secrets Manager 조회 실패: AccessDeniedException\"}}"
}
```

**3단계 — Lambda 예약 환경변수 등록 시도:**
```text
Lambda was unable to configure your environment variables because the environment variables
you have provided contains reserved keys that are currently not supported for modification.
Reserved keys used in this request: AWS_REGION
```

### 발생 조건 / 빈도
- **언제:** Lambda 콘솔에서 최초 테스트 시 매번 발생
- **빈도:** 항상 (설정 완료 전까지)

---

## 3) 영향 범위

- **영향받는 기능:** 전체 API (`/congestion/current`, `/congestion/hourly`, `/beaches`)
- **영향받는 사용자/데이터:** 테스트 환경만, 실 사용자 없음
- **심각도(개발 단계):** `High` — Lambda 실행 자체가 불가능한 상태

---

## 4) 재현 방법

> 이 문서는 단일 장애가 아니라 설정 누락이 순차적으로 드러난 케이스다.
> 따라서 아래 3개 케이스를 독립적으로 재현해야 문서의 에러 흐름과 원인이 일치한다.

### 공통 전제 조건
- **외부 레포:** [`Beach-complex/beach_complex_ai`](https://github.com/Beach-complex/beach_complex_ai)
- **브랜치/커밋:** `main` / [`f613dc4`](https://github.com/Beach-complex/beach_complex_ai/commit/f613dc4bcefaba6c33c885b7ac3c198774a85209) (`feat: AWS Lambda 배포 환경 설정 적용`)
- **코드 파일:** [`main.py`](https://github.com/Beach-complex/beach_complex_ai/blob/f613dc4bcefaba6c33c885b7ac3c198774a85209/main.py)
- **의존성/버전:** Python 3.13, FastAPI, Mangum, boto3
- **배포 상태:** 위 커밋 기준 코드를 Lambda에 배포한 상태

### 케이스 A — Mangum 이벤트 형식 오류
#### 전제 조건
- Lambda 테스트 이벤트를 콘솔 기본 `Hello World` 템플릿으로 생성

#### 재현 절차
1. Lambda 콘솔 `Test` 탭에서 기본 `Hello World` 이벤트를 선택한다.
2. 함수를 실행한다.

#### 기대 결과
- `The adapter was unable to infer a handler to use for the event.` 런타임 에러가 발생한다.

#### 재현 입력 (실패하는 이벤트)
```json
{
  "key1": "value1",
  "key2": "value2",
  "key3": "value3"
}
```

### 케이스 B — 리전 불일치
#### 전제 조건
- API Gateway v2 이벤트 사용
- 시크릿 리전은 `us-east-1`
- 문제 발생 기준 코드에서 `AWS_REGION = os.getenv("AWS_REGION", "ap-northeast-2")` 를 사용

#### 재현 절차
1. 코드 기본 리전이 `ap-northeast-2` 인 상태로 `/congestion/current?beach_id=haeundae` 를 실행한다.

#### 기대 결과
- `OpenWeather API 키 조회 실패: Secrets Manager 조회 실패: AccessDeniedException` 응답이 반환된다.

### 케이스 C — 리전 불일치 해결 시도 중 예약 환경변수 오류
#### 전제 조건
- 케이스 B의 리전 불일치 원인이 아래 코드의 기본값(`ap-northeast-2`)임을 확인한 상황
```python
# 문제 코드 — 기본값이 실제 시크릿 리전(us-east-1)과 불일치
AWS_REGION = os.getenv("AWS_REGION", "ap-northeast-2")
```
- 이를 Lambda 환경변수로 덮어써서 해결하려고 시도

#### 재현 절차
1. Lambda 콘솔 환경변수 편집에서 `AWS_REGION` 키를 직접 추가하려고 시도한다.
2. 저장한다.

#### 기대 결과
- `Reserved keys used in this request: AWS_REGION` 오류가 발생한다.
- Lambda가 `AWS_REGION`을 자동 주입하는 예약어이므로 직접 등록 불가.
- → 코드에서 `os.environ["AWS_REGION"]`으로 직접 읽는 방식으로 전환하여 해결.

---

## 5) 원인 분석 (Root Cause)

### 가설 목록
- [x] 가설 1: 테스트 이벤트 형식이 Mangum 지원 형식이 아님
- [x] 가설 2: secrets_client 초기화 시 리전이 `ap-northeast-2` 기본값으로 설정되어 `us-east-1` 시크릿 조회 불가 → AccessDeniedException 발생

### 근거

- **로그/지표:**
  - `Mangum adapter.py:56` — 이벤트에 `requestContext` 또는 `version: "2.0"` 없어서 핸들러 타입 추론 실패
  - `AccessDeniedException` — 리전 불일치로 boto3가 잘못된 엔드포인트에 요청, 시크릿을 찾지 못해 발생
- **코드 포인트:**
  - 외부 AI 서버 레포 [`main.py`](https://github.com/Beach-complex/beach_complex_ai/blob/f613dc4bcefaba6c33c885b7ac3c198774a85209/main.py) — `AWS_REGION`, `secrets_client` 가 모듈 레벨에서 초기화됨
  - 문제 발생 기준 커밋 [`f613dc4`](https://github.com/Beach-complex/beach_complex_ai/commit/f613dc4bcefaba6c33c885b7ac3c198774a85209) — `AWS_REGION = os.getenv("AWS_REGION", "ap-northeast-2")` 기본값이 `us-east-1` 시크릿과 불일치

### 최종 원인 (One-liner)
Mangum 이벤트 형식 오류 + 리전 기본값 하드코딩(`ap-northeast-2`)이 순차적으로 드러나 전체 스택이 동작하지 않음

---

## 6) 해결 (Fix)

### 해결 전략
- **유형:** `Config change`
- **접근:** `AWS_REGION`을 Lambda 환경변수로 주입하려다 예약어 오류 → Lambda가 자동 주입하는 값임을 확인 후 코드에서 `os.environ["AWS_REGION"]`으로 직접 읽는 방식으로 전환

### 변경 사항

**코드 변경 (`main.py`):**
```python
# 변경 전
AWS_REGION = os.getenv("AWS_REGION", "ap-northeast-2")

# 변경 후 — Lambda가 AWS_REGION을 자동 주입하므로 직접 읽음
AWS_REGION = os.environ["AWS_REGION"]
```

- 외부 코드 기준 파일: [`main.py`](https://github.com/Beach-complex/beach_complex_ai/blob/main/main.py)
- 문제 발생 기준 커밋: [`f613dc4`](https://github.com/Beach-complex/beach_complex_ai/commit/f613dc4bcefaba6c33c885b7ac3c198774a85209)

**Lambda 테스트 이벤트 (정상 동작 형식):**
```json
{
  "version": "2.0",
  "routeKey": "GET /congestion/current",
  "rawPath": "/congestion/current",
  "rawQueryString": "beach_id=haeundae",
  "headers": { "content-type": "application/json" },
  "requestContext": {
    "http": {
      "method": "GET",
      "path": "/congestion/current",
      "protocol": "HTTP/1.1",
      "sourceIp": "127.0.0.1",
      "userAgent": "test"
    },
    "requestId": "test",
    "routeKey": "GET /congestion/current",
    "stage": "$default"
  },
  "isBase64Encoded": false
}
```
결과 
```json
{
  "statusCode": 200,
  "body": "{\"beach_id\":\"haeundae\",\"beach_name\":\"해운대해수욕장\",\"input\":{\"timestamp\":\"2026-03-28T08:07:23.584416+09:00\",\"weather\":{\"temp_c\":14.03000000000003,\"rain_mm\":0.0,\"wind_mps\":1.22},\"is_weekend_or_holiday\":true},\"rule_based\":{\"score_raw\":0.13999999999999999,\"score_pct\":14.0,\"level\":\"여유\"},\"ai\":null}",
  "headers": {
    "content-length": "306",
    "content-type": "application/json"
  },
  "isBase64Encoded": false
}
```

### 주의/부작용
- `os.environ["AWS_REGION"]`은 키가 없으면 `KeyError` 발생 — 로컬 실행 시 환경변수 직접 설정 필요

---

## 7) 검증 (Verification)

### 해결 확인
- [x] `/health` 테스트 → `statusCode: 200`
- [x] `/congestion/current?beach_id=haeundae` 테스트 → 날씨/혼잡도 데이터 정상 반환
- [ ] 관련 테스트 통과 (unit/integration) — 미작성
- [x] Lambda 콘솔 실행 정상

### 추가 확인
- Secrets Manager 조회 로그 정상 출력 확인
- OpenWeather API 호출 로그 `[OpenWeather] called: ... status: 200` 확인

---

## 8) 재발 방지 (Prevention)

### 방지 조치 체크리스트
- [ ] **문서화:** Lambda 테스트 이벤트 JSON을 `docs/` 또는 README에 보관
- [ ] **검증 로직 추가:** `SECRET_NAME` 미설정 시 Lambda 초기화 단계에서 명확한 에러 메시지 출력
- [ ] **로깅 개선:** Secrets Manager 조회 성공/실패 로그 명시적으로 출력
- [ ] **가드레일:** 배포 전 `AWS_REGION` 코드 기본값과 실제 시크릿 리전 일치 여부 확인 항목 추가

### 남은 작업(Action Items)
- [ ] GitHub Actions 배포 파이프라인 구축 (자동 ZIP 패키징 → Lambda 업데이트)
- [ ] 로컬 실행 시 `AWS_REGION` 환경변수 설정 방법 README에 추가

---

## 9) 참고 자료 (References)

- [Mangum — Supported event types](https://mangum.fastapiexpert.com/) — HTTP API(v2), REST API(v1), ALB 이벤트 형식 구분
- [AWS Lambda — 정의된 런타임 환경 변수](https://docs.aws.amazon.com/ko_kr/lambda/latest/dg/configuration-envvars.html#configuration-envvars-runtime) — `AWS_REGION` 등 예약어 목록
- [AWS Secrets Manager — GetSecretValue IAM 권한](https://docs.aws.amazon.com/ko_kr/secretsmanager/latest/userguide/auth-and-access_examples.html) — 최소 권한 정책 예시
- [AWS Lambda Function URL 이벤트 형식 (HTTP API v2)](https://docs.aws.amazon.com/ko_kr/lambda/latest/dg/urls-invocation.html#urls-payloads) — `version: "2.0"` + `requestContext.http` 구조
