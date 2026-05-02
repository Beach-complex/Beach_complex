# Backend CD 장애 연쇄 분석: YAML 주석, EC2 SSH 인바운드, Firebase 빈 의존성, 헬스체크 오탐

> 목적: 2026년 4월 백엔드 CD 장애를 한 문서에서 다시 따라갈 수 있도록, 증상/원인/확인 방법/해결/재발 방지까지 운영 관점으로 정리한다.
> 핵심 포인트: 이번 건은 원인 1개가 아니라, 앞 단계 문제를 해결해야 다음 단계 문제가 드러나는 연쇄 구조였다.

---

## 0) 메타 정보

- **모드:** `OPS`
- **상태:** `해결` + 일부 후속 작업 남음
- **작성자:** 박재홍(@PHJ2000), Codex 정리
- **해결 날짜(선택):** 2026-04-16 1차 안정화, 2026-04-22 문서화
- **작성일(필수):** 2026-04-22
- **컴포넌트:** infra, 배포 인프라, GitHub Actions, EC2, Spring Boot, Firebase
- **환경:** 운영 (GitHub Actions -> EC2)
- **관련 이슈/PR:**
  - Issue #213 / PR #214: PB-110 Backend CD 이미지 태그 파싱 실패 수정
  - Issue #215 / PR #216: PB-111 Backend CD 배포 단계에서 Firebase 비활성화 시 앱 부팅 실패
  - Issue #217 / PR #218: PB-112 Backend CD 헬스체크 타이밍으로 배포 실패 오탐
- **관련 파일:**
  - `.github/workflows/backend-cd.yml`
  - `src/main/java/com/beachcheck/config/FirebaseConfig.java`
  - `src/main/java/com/beachcheck/service/OutboxEventDispatcher.java`
  - `src/main/java/com/beachcheck/config/OutboxSchedulingConfig.java`
  - `src/test/java/com/beachcheck/config/OutboxFirebaseConditionContextTest.java`
- **키워드:** docker/build-push-action, appleboy/ssh-action, i/o timeout, FirebaseMessaging, UnsatisfiedDependencyException, actuator, curl 56, connection reset by peer

---

## 1) 요약 (3줄)

- **무슨 문제였나:** 백엔드 CD가 한 번에 해결되지 않고, 워크플로우 설정 문제 -> SSH 네트워크 문제 -> 앱 부팅 문제 -> 헬스체크 오탐 순서로 차례대로 드러났다.
- **원인:** 실패 지점이 서로 다른 단계에 있었고, 앞 단계 병목을 통과해야만 다음 병목이 로그에 드러나는 구조였다.
- **해결:** PB-110에서 `tags: |` 블록 안 설명 주석을 제거했고, EC2 보안 그룹에서 SSH(`22/TCP`)를 `0.0.0.0/0`으로 임시 개방해 SSH 단계를 통과시켰고, PB-111에서 `FirebaseMessaging` 빈이 없을 때 FCM 전송용 스프링 빈(`OutboxEventDispatcher`, `OutboxPublisher`, `OutboxSchedulingConfig`)이 등록되지 않도록 분리했으며, PB-112에서 헬스체크 재시도 루프를 `for i in $(seq 1 30)`으로 바꿔 셸 호환성을 맞췄다.

## 1-1) 학습 포인트

- **빠른 확인 포인트 (3):**
  1. **로그:** 지금 실패가 `워크플로우 파싱`, `SSH 연결`, `Spring Context 기동`, `헬스체크` 중 어디인지 먼저 나눈다.
  2. **설정/환경 변수:** `.github/workflows/backend-cd.yml`, EC2 보안 그룹, `APP_FIREBASE_ENABLED`, actuator 포트(`8081`)를 함께 본다.
  3. **인프라/의존성:** GitHub Actions 실행 환경의 네트워크 문제와 Spring 빈 의존성 문제는 서로 다른 문제이므로 섞어서 해석하지 않는다.
- **실무 팁 (필수):** CD 장애는 첫 에러가 전체 원인이라고 단정하지 말고, "현재 로그가 멈춘 단계"를 먼저 고정한 뒤 단계별로 하나씩 벗겨내야 한다.
- **피해야 할 오해 (선택):** Docker가 떴다고 앱이 정상이라 생각하거나, 앱 로그에 경고가 있다고 곧바로 CD 실패 원인이라고 단정하는 것.

---

## 2) 증상

### 관측된 현상

- GitHub Actions의 백엔드 CD가 여러 차례 실패했다.
- 실패 원인이 매번 같지 않았고, 하나를 해결하면 다음 단계의 다른 문제가 드러났다.
- 최종적으로는 EC2에서 컨테이너가 `Up` 상태이고 actuator health가 `UP`인데도, GitHub Actions에서는 실패로 표시되는 상황까지 발생했다.

### 에러 메시지 / 스택트레이스 (필수)

#### 2-1) YAML / Action 입력 파싱 문제

정확한 원인 로그는 Docker 빌드/푸시 단계에서 태그를 잘못 읽은 것이었다.

```text
docker/build-push-action 단계에서 이미지 태그 파싱 실패
```

핵심은 `.github/workflows/backend-cd.yml`의 `tags: |` 블록 안에 넣은 설명 주석이 주석으로 무시되지 않고, action에 문자열 그대로 전달되었다는 점이다.

#### 2-2) GitHub Actions -> EC2 SSH 연결 실패

```text
2026/04/10 09:59:52 dial tcp ***:***: i/o timeout
Error: Process completed with exit code 1.
```

#### 2-3) 앱 부팅 실패 (`FirebaseMessaging` 빈 없음)

```text
APPLICATION FAILED TO START

Description:

Parameter 2 of constructor in com.beachcheck.service.OutboxEventDispatcher required a bean of type 'com.google.firebase.messaging.FirebaseMessaging' that could not be found.

Action:

Consider defining a bean of type 'com.google.firebase.messaging.FirebaseMessaging' in your configuration.
```

#### 2-4) 헬스체크 오탐

```text
curl: (56) Recv failure: Connection reset by peer
2026/04/10 10:25:23 Process exited with status 56
Error: Process completed with exit code 1.
```

하지만 같은 흐름에서 EC2 내부 확인 결과는:

```text
HTTP/1.1 200
{"status":"UP","groups":["liveness","readiness"]}
```

### 발생 조건 / 빈도

- **언제:** 2026년 4월 10일 ~ 2026년 4월 16일 백엔드 CD 점검 과정
- **빈도:** 각 단계에서 재현 시 거의 항상 발생
- **특징:** 앞 단계 문제를 고쳐야 다음 단계 문제가 드러나는 "가려진 병목" 구조였다.

---

## 3) 영향 범위

- **영향받는 기능:** GitHub Actions 기반 백엔드 운영 배포 전체 흐름
- **영향받는 사용자/데이터:** 운영 배포 담당자의 수동 개입이 반복되었고, 배포 성공/실패 판단 신뢰도가 떨어졌다
- **데이터 손상 여부:** 확인된 데이터 손상 없음
- **운영 영향도:** `높음`

설명:
- 실제 서비스가 완전히 장시간 중단된 것은 아니었지만
- 배포 자동화가 정상 작동하지 않았고
- 운영자가 SSH 접속, 보안 그룹 수정, 컨테이너 로그 확인 등 수동 확인을 반복해야 했다

---

## 4) 재현 방법

### 전제 조건

- **워크플로우:** `.github/workflows/backend-cd.yml`
- **배포 구조:** GitHub Actions -> `appleboy/ssh-action` -> EC2 -> Docker Compose -> Spring Boot 앱
- **앱 설정:**
  - management port: `8081`
  - Firebase 비활성화 운영 환경: `APP_FIREBASE_ENABLED=false`
- **운영 확인 명령:**

```bash
docker ps -a
docker logs beach-complex-backend --tail 200
curl -i http://127.0.0.1:8081/actuator/health
```

### 재현 절차

1. GitHub Actions에서 백엔드 CD 실행
2. 빌드/푸시 -> SSH 배포 -> Docker 실행 -> 헬스체크 흐름 진행
3. 실패 로그를 확인
4. 하나의 병목을 제거한 뒤 다시 실행
5. 이전에는 가려져 있던 다음 단계 문제가 드러나는지 확인

### 재현 입력/데이터

```text
주요 관찰 포인트

1. docker/build-push-action tags 입력
2. appleboy/ssh-action 연결 여부
3. EC2 보안 그룹의 22/TCP 인바운드
4. Spring Boot 시작 로그
5. actuator /health 응답 시점
```

---

## 5) 원인 분석

> 이번 장애는 "원인 하나"가 아니라, 서로 다른 단계의 병목이 차례로 드러난 연쇄 장애였다.

## 5-1) 전체 흐름 한눈에 보기

1. `backend-cd.yml` 자체 문제로 Docker 빌드/푸시 단계에서 실패
2. 그걸 고치니 이제 SSH 단계까지 진행되고, 여기서 인바운드 규칙 문제가 드러남
3. SSH가 열리니 Docker pull/up 이후 실제 Spring Boot 부팅 실패가 드러남
4. 앱 부팅을 고치니 이번에는 앱은 뜨지만 health check 판정이 불안정해 CD가 실패로 찍힘

즉, **새 문제를 만든 것이 아니라, 원래 숨어 있던 다음 병목이 순서대로 드러난 것**이다.

---

## 5-2) 1단계. `backend-cd.yml` 내부 주석 때문에 이미지 태그 파싱 실패

### 현상

- Docker 이미지 빌드/푸시 이전에 워크플로우 입력값 해석이 깨졌다.
- `tags: |` 블록에 있던 설명 주석이 action이 읽는 문자열에 섞였다.

### 문제 패턴

```yaml
tags: |
  # latest는 현재 배포 기준점으로 쓰고, SHA 태그는 특정 이미지 추적과 롤백에 사용한다.
  ghcr.io/owner/repo-backend:latest
  ghcr.io/owner/repo-backend:${{ github.sha }}
```

### 왜 문제였나

- YAML에서 `|` 아래 내용은 여러 줄 문자열로 그대로 전달된다.
- 그래서 이 블록 안의 `# ...`는 주석처럼 보이더라도 실제로는 문자열 일부가 된다.
- 따라서 action은 첫 줄을 잘못된 태그 문자열로 읽을 수 있다.

### 최종 원인 (One-liner)

- 여러 줄 입력 블록 안의 설명 주석이 Docker 이미지 태그 값에 섞여 태그 파싱을 깨뜨렸다.

### 해결

- PB-110에서 `tags: |` 블록 안 설명 주석 제거
- 비슷한 성격의 여러 줄 입력 블록에서도 같은 방식의 설명 주석 제거

### 재발 방지 포인트

- GitHub Actions에서 multiline raw input block 안에는 설명 주석을 넣지 않는다.
- 주석이 필요하면 block 바깥으로 뺀다.

---

## 5-3) 2단계. GitHub Actions에서 EC2 SSH 접속 실패 (`i/o timeout`)

### 현상

```text
2026/04/10 09:59:52 dial tcp ***:***: i/o timeout
```

### 왜 문제였나

- 이 에러는 인증 이전 단계의 네트워크 문제다.
- 즉, private key가 맞는지 틀린지를 보기 전에, GitHub Actions 실행 환경이 EC2 `22/TCP`에 도달하지 못했다는 뜻이다.
- 당시 EC2 인바운드 규칙에서 SSH(`22/TCP`)가 GitHub Actions에서 접근 가능한 범위로 열려 있지 않았다.

### 사용자 관찰로 확정된 근거

- AWS 보안 그룹에서 SSH 인바운드(`22/TCP`)를 `0.0.0.0/0`으로 임시 개방한 뒤 다음 실행부터는
  - `Login Succeeded`
  - Docker image pull
  - Docker container create/start
  까지 진행되었다.

이 변화는 원인이 "애플리케이션 코드"가 아니라 "인바운드 규칙"이었음을 강하게 보여준다.

### 최종 원인 (One-liner)

- EC2 보안 그룹 인바운드가 GitHub Actions 실행 환경의 SSH 접근을 허용하지 않아 `appleboy/ssh-action`이 timeout으로 실패했다.

### 해결

- AWS 보안 그룹에서 SSH(`22/TCP`)를 `0.0.0.0/0`으로 임시 개방해 GitHub Actions runner가 EC2에 접속할 수 있게 했다.

### 장기 해법

- self-hosted runner 사용
- AWS SSM 기반 배포 전환
- GitHub IP 대역 자동 동기화 또는 별도 배포 통로 설계

### 재발 방지 포인트

- `i/o timeout`이면 먼저 네트워크/보안 그룹/포트 개방 여부를 본다.
- `Permission denied (publickey)`와는 완전히 다른 층이다.

빠른 구분:
- `i/o timeout` = 네트워크
- `Permission denied (publickey)` = 인증

---

## 5-4) 3단계. 앱 부팅 실패: `FirebaseMessaging` 빈 미등록

### 현상

- SSH와 Docker pull/up은 성공했지만, Spring Boot 컨테이너 내부에서 앱이 부팅 도중 죽었다.
- 핵심 로그:

```text
Parameter 2 of constructor in com.beachcheck.service.OutboxEventDispatcher required a bean of type 'com.google.firebase.messaging.FirebaseMessaging' that could not be found.
```

### 왜 처음엔 헷갈렸나

- 운영 env에 `APP_FIREBASE_ENABLED=false`가 있었기 때문에
- "Firebase를 끈 상태인데 Firebase가 없다고 실패하는 게 이상하지 않나?"라는 혼선이 생기기 쉬웠다.

하지만 실제 원인은 반대였다.

- `APP_FIREBASE_ENABLED=false`라서 Firebase 빈은 생성되지 않았는데
- 그 빈을 요구하는 Outbox 관련 빈들이 여전히 생성되면서
- Spring Context가 필수 의존성을 못 맞춘 것이다.

### 실제 의존성 체인

```text
OutboxSchedulingConfig
  -> OutboxPublisher
    -> OutboxEventDispatcher
      -> FirebaseMessaging
```

당시 코드상 핵심 포인트:
- `FirebaseConfig`는 `app.firebase.enabled=true`일 때만 빈을 만든다.
- 하지만 `OutboxEventDispatcher`는 생성자에서 `FirebaseMessaging`을 직접 요구했다.
- `OutboxSchedulingConfig`는 `OutboxPublisher`를 직접 주입받는다.

즉, `FirebaseMessaging`이 없을 때 FCM 전송용 빈까지 함께 빠져야 하는데, 당시에는 그 등록 경계가 분리되지 않은 상태였다.

### 왜 CD에서 특히 치명적이었나

- 이 문제는 Docker container 생성 단계에서는 보이지 않는다.
- 실제 애플리케이션이 Spring Context를 띄우는 순간에만 드러난다.
- 따라서 GitHub Actions 상에서는 "배포는 된 것 같은데 앱이 왜 죽지?"처럼 보이고,
- 정확한 원인 파악에는 EC2에서 `docker logs beach-complex-backend --tail 200` 확인이 필수였다.

### 최종 원인 (One-liner)

- Firebase를 비활성화해 `FirebaseMessaging` 빈은 생성되지 않았지만, 이를 생성자에서 직접 요구하는 FCM 전송용 빈(`OutboxEventDispatcher` -> `OutboxPublisher` -> `OutboxSchedulingConfig`)의 등록 경계가 분리되지 않아 Spring Context가 부팅에 실패했다.

### 해결

- PB-111에서 `OutboxFirebaseConfig`를 두고, `FirebaseMessaging` 빈이 있을 때만 `OutboxEventDispatcher`와 `OutboxPublisher`를 등록하도록 분리
- `OutboxSchedulingConfig`에 `@ConditionalOnBean(OutboxPublisher.class)`를 적용해 publisher가 없으면 스케줄러도 등록되지 않도록 변경
- Firebase 자격증명 로딩 경로를 `APP_FIREBASE_CREDENTIALS_JSON_BASE64` -> `APP_FIREBASE_CREDENTIALS_PATH` -> classpath 순으로 읽도록 운영 기준에 맞춰 확장
- `OutboxFirebaseConditionContextTest`를 추가해 `FirebaseMessaging` 유무에 따라 관련 빈 등록 여부가 달라지는지 회귀 검증

### 재발 방지 포인트

- `@ConditionalOnProperty`는 "켜는 쪽"만 볼 게 아니라 "참조하는 쪽"까지 같이 본다.
- Spring에서는 "빈 생성 조건"과 "생성자 의존성 체인"을 세트로 검토해야 한다.

---

## 5-5) 4단계. 앱은 살아 있는데 CD가 실패로 찍히는 헬스체크 오탐

### 현상

- Firebase 이슈 수정 후 컨테이너는 실제로 살아 있었다.
- 사용자 확인 결과:

```text
NAME                    IMAGE                                                                                  COMMAND               SERVICE   CREATED              STATUS              PORTS
beach-complex-backend   ghcr.io/beach-complex/beach_complex-backend:56f0c9316fc95d8a4d8383c717939ec81b831cd6   "java -jar app.jar"   app       About a minute ago   Up About a minute   0.0.0.0:8080-8081->8080-8081/tcp
```

그리고 actuator 확인:

```text
HTTP/1.1 200
{"status":"UP","groups":["liveness","readiness"]}
```

그런데 GitHub Actions는 여전히 다음처럼 실패할 수 있었다.

```text
curl: (56) Recv failure: Connection reset by peer
Process exited with status 1
```

### 왜 문제였나

- 배포 직후 헬스체크 주소는 앱 초기화 타이밍에 따라 아직 준비되지 않을 수 있다.
- 원래 단발성 `curl`만 있으면 정상인데도 실패로 잘못 판단할 수 있다.
- 이후 재시도 구조를 넣었더라도, 루프가 bash 전용에 가까운 `{1..30}` 확장에 의존하면 원격 셸이 `sh`일 때 기대한 횟수만큼 재시도하지 않을 수 있다.

즉 이 시점의 본질은:
- 앱 장애가 아니라
- 배포 판정 로직의 타이밍 문제와 셸 호환성 문제였다.

### 최종 원인 (One-liner)

- 앱 기동 완료 전에 단발성 `curl`이 먼저 실행되거나, 재시도 루프가 원격 셸에서 기대한 횟수만큼 돌지 않아, 실제로는 정상 기동한 앱이 CD에서 실패로 기록되었다.

### 해결

- 배포 헬스체크를 단발성 `curl`이 아니라 재시도 루프로 유지
- 재시도 루프를 `for i in $(seq 1 30)`으로 바꿔 원격 셸이 `bash`가 아니어도 30회 재시도가 실제로 동작하게 수정

예시:

```bash
for i in $(seq 1 30); do
  if curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health; then
    exit 0
  fi
  sleep 2
done

docker compose -f "${EC2_COMPOSE_FILE}" ps
docker logs --tail 200 beach-complex-backend || true
exit 1
```

### 재발 방지 포인트

- 헬스체크는 1회 실패로 곧바로 전체 배포 실패 처리하지 않는다.
- 실패 시 컨테이너 상태와 최근 로그를 함께 출력한다.
- 원격 셸이 bash인지 확실하지 않으면 bash 확장 문법을 피한다.

---

## 5-6) 직접 원인은 아니었지만 혼선을 준 로그: 혼잡도 스케줄러 경고

### 관찰 로그

```text
I/O error on GET request for "http://127.0.0.1:8000/congestion/current": Connection refused
```

### 왜 직접 원인이 아니었나

- 이 로그는 앱이 이미 뜬 뒤 스케줄러가 동작하면서 남긴 경고 로그다.
- `CongestionClient`는 예외를 잡고 `null`을 반환하고,
- 스케줄러는 해당 케이스를 건너뛴다.
- 즉, 운영 환경에 혼잡도 API/Lambda 주소가 아직 없어서 생긴 별도 설정 이슈이지, 당시 CD 실패의 직접 원인은 아니었다.

### 정리

- **맞는 해석:** 운영 환경 변수에 혼잡도 API 주소가 없어서 경고가 찍힘
- **틀린 해석:** 이 경고 때문에 앱이 죽고 CD가 실패함

---

## 6) 해결

### 해결 전략

- **유형:** `긴급 수정` + `설정 변경` + `배포 워크플로우 안정화`
- **접근:** 앞 단계 문제부터 하나씩 제거해 다음 병목을 드러내는 방식으로 순차 해소

### 변경 사항

#### PB-110

- `backend-cd.yml`의 multiline input block 안 설명 주석 제거
- Docker 이미지 태그 파싱 실패 해결

#### 인프라 조치

- EC2 보안 그룹에서 SSH(`22/TCP`)를 `0.0.0.0/0`으로 임시 개방
- 그 결과 GitHub Actions runner가 EC2 `22/TCP`에 도달해 `appleboy/ssh-action`의 timeout이 사라짐

#### PB-111

- `FirebaseMessaging` 빈이 없을 때 `OutboxEventDispatcher`, `OutboxPublisher`가 생성되지 않도록 `OutboxFirebaseConfig`로 분리
- `OutboxSchedulingConfig`에 `@ConditionalOnBean(OutboxPublisher.class)`를 추가해 publisher가 없으면 스케줄러도 등록되지 않도록 수정
- Firebase 자격증명을 `APP_FIREBASE_CREDENTIALS_JSON_BASE64`, `APP_FIREBASE_CREDENTIALS_PATH`, classpath 순으로 찾도록 운영용 로딩 경로 확장
- `OutboxFirebaseConditionContextTest`로 Firebase 유무에 따른 빈 등록 결과를 검증

#### PB-112

- 배포 헬스체크를 단발성 `curl`이 아니라 재시도 루프로 유지
- 재시도 루프를 `for i in $(seq 1 30)`으로 바꿔 원격 셸이 `bash`가 아니어도 30회 재시도가 실제로 동작하게 수정

### 주의/부작용

- SSH 인바운드를 넓게 여는 방식은 진단에는 빠르지만 운영 상시 정책으로는 적절하지 않다.
- Firebase 기능을 끈 상태에서 FCM 전송용 빈(`OutboxEventDispatcher`, `OutboxPublisher`, `OutboxSchedulingConfig`)까지 함께 빠지는 것이 제품 의도와 맞는지는 팀 합의가 필요하다.
- 혼잡도 API 주소 부재는 별도 운영 설정 과제로 남는다.

---

## 7) 검증

### 해결 확인

- [x] YAML 주석 제거 후 빌드/푸시 단계가 다음 단계로 진행됨
- [x] SSH 인바운드 수정 후 `Login Succeeded` 및 Docker pull 로그 확인
- [x] Firebase 빈 이슈 수정 후 컨테이너가 `Up` 상태로 유지됨
- [x] EC2 내부에서 actuator health `200 OK` 확인
- [x] PB-112 변경으로 health check 루프가 셸 호환적인 형태로 정리됨

### 실제 사용한 명령

```bash
docker ps -a
docker logs beach-complex-backend --tail 200
curl -i http://127.0.0.1:8081/actuator/health
git diff --check
```

### 추가 확인 포인트

- EC2에서 직접 확인한 결과와 GitHub Actions 결과가 다를 때는, 앱 실제 상태를 판단할 기준으로 EC2 내부 health check와 컨테이너 상태를 우선한다.
- 앱 로그의 경고는 "부팅 실패" 로그와 "기동 후 백그라운드 작업 경고"를 분리해서 해석한다.

---

## 8) 재발 방지

### 방지 조치 체크리스트

- [x] **문서화:** 장애 흐름과 단계별 진단 순서 기록
- [x] **로깅 개선:** deploy 실패 시 컨테이너 상태/로그 함께 출력
- [x] **가드레일:** Firebase 비활성화 시 FCM 전송용 빈(`OutboxEventDispatcher`, `OutboxPublisher`, `OutboxSchedulingConfig`)이 함께 빠지도록 등록 경계 분리
- [ ] **검증 로직 추가:** 워크플로우 문법 검사 또는 간단한 사전 점검 도입 검토
- [ ] **인프라 개선:** SSH 대신 SSM 또는 self-hosted runner 기반 배포 전환 검토
- [ ] **운영 설정 정리:** 혼잡도 API 주소 주입 및 활성화 플래그 정책 정리

### 남은 작업

- [ ] PB-112 머지 후 실제 `backend-cd` 재실행 결과 최종 확인
- [ ] EC2 SSH 접근 정책을 장기적으로 재설계
- [ ] 혼잡도 API/Lambda 주소 운영 환경 변수 정리
- [ ] 배포 런북에 "단계별 진단 순서" 반영

---

## 9) 운영 영향

- **영향 기간:** 2026-04-10 ~ 2026-04-16
- **사용자 영향:** 운영 배포 자동화 신뢰도 하락, 운영자가 수동 진단을 반복
- **데이터 손상 여부:** `No`
- **운영 영향도:** `높음`

---

## 10) 타임라인

- **2026-04-10:** 백엔드 CD 실패 확인
- **2026-04-10:** `backend-cd.yml`의 이미지 태그 파싱 문제가 첫 병목으로 드러남
- **2026-04-10:** PB-110에서 YAML 여러 줄 블록 내부 주석 제거
- **2026-04-10:** 이제 SSH 단계까지 진행되며 `dial tcp ... i/o timeout` 노출
- **2026-04-10:** EC2 보안 그룹에서 SSH(`22/TCP`)를 `0.0.0.0/0`으로 임시 개방
- **2026-04-10:** SSH 및 Docker pull/up 성공 후 앱 부팅 실패 로그에서 `FirebaseMessaging` 빈 누락 확인
- **2026-04-15:** PB-111로 Firebase 비활성화 시 앱 부팅 실패 수정 진행
- **2026-04-16:** EC2 내부 actuator는 `UP`인데 Actions는 실패로 찍히는 오탐 확인
- **2026-04-16:** PB-112로 헬스체크 루프 셸 호환성 수정 진행
- **2026-04-22:** 전체 흐름 troubleshooting 문서화

---

## 11) 임시 조치

- **조치 내용:**
  - YAML 문제 제거
  - SSH 인바운드 규칙 임시 완화
  - EC2 직접 접속 후 `docker ps`, `docker logs`, `curl`로 상태 확인
- **근거/리스크:**
  - 빠른 진단을 위해 네트워크를 임시 완화하는 것은 효과적이었지만 장기 운영 보안 정책으로는 위험하다
- **효과:**
  - 장애 단계를 하나씩 벗겨내며 다음 병목을 식별할 수 있었다

---

## 12) 모니터링/알람 개선

- **추가해야 할 운영 체크리스트:**
  - 빌드/푸시 실패인지
  - SSH 접속 실패인지
  - 앱 부팅 실패인지
  - 헬스체크 오탐인지
- **봐야 할 지표/상태:**
  - GitHub Actions 워크플로우 결과
  - EC2 컨테이너 상태
  - actuator `/health`
- **실패 시 남겨야 할 로그:**
  - `docker compose ps`
  - `docker logs --tail 200 beach-complex-backend`

---

## 13) 런북 업데이트

- [x] 장애 대응 절차 문서화
- [x] 점검 명령 정리
- [ ] 롤백 기준 정리
- [ ] SSH 배포를 더 안전한 운영 표준으로 전환 검토

---

## 부록 A. 이번 사건에서 가장 먼저 봐야 할 로그 분류표

| 로그/증상 | 우선 의심 단계 | 의미 |
| --- | --- | --- |
| 이미지 태그 파싱 실패 | 워크플로우/YAML | `backend-cd.yml` 입력 형식 문제 |
| `dial tcp ... i/o timeout` | 네트워크/보안 그룹 | 포트 자체에 도달 못함 |
| `Permission denied (publickey)` | 인증 | 네트워크는 열렸고 key/user 문제 |
| `No qualifying bean of type FirebaseMessaging` | 앱 부팅/Spring DI | 런타임 빈 구성 문제 |
| `curl: (56) Recv failure` + EC2 health 200 | 헬스체크 판정 로직 | 앱은 뜨지만 CD가 실패로 오탐 |

---

## 부록 B. 다음에 같은 상황이면 이 순서로 본다

1. **GitHub Actions 로그에서 실패 단계 확인**
2. **SSH 단계 실패면 EC2 보안 그룹부터 확인**
3. **컨테이너가 떴다면 EC2에서 `docker logs` 확인**
4. **앱이 떴다면 `curl http://127.0.0.1:8081/actuator/health`로 실제 상태 확인**
5. **경고 로그는 부팅 실패 로그와 분리해서 해석**

이 순서를 지키면 "어디까지는 정상으로 통과했고, 정확히 어느 층에서 막혔는지"를 훨씬 빨리 좁힐 수 있다.
