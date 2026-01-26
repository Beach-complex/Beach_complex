# Windows Testcontainers 통합테스트 실패 (InvalidPathException / DockerClientProviderStrategy)

## 문제 상황

### 통합테스트 실패 증상
- **테스트:** Testcontainers 기반 통합테스트 전부
- **증상 1:** `InvalidPathException`으로 컨텍스트 초기화 실패
- **증상 2:** `DockerClientProviderStrategy` 오류로 Docker 클라이언트 탐색 실패

### 발생 로그

```
java.nio.file.InvalidPathException: Illegal char ':' at index 53:
C:\Users\ParkJaeHong\AppData\Local\GitHubDesktop\binc:\Users\ParkJaeHong\.vscode\extensions\...
```

```
java.lang.IllegalStateException at DockerClientProviderStrategy.java:277
```

---

## 근본 원인

### 원인 1: PATH 환경변수 오타
- `...GitHubDesktop\bin` 뒤에 세미콜론이 빠져 다음 경로와 붙어 있음.
- Testcontainers가 DockerMachine 탐색 중 PATH를 파싱하다가 예외 발생.

### 원인 2: Docker Desktop 미실행
- Docker 데몬이 실행 중이지 않아 Testcontainers가 유효한 Docker 클라이언트를 찾지 못함.

---

## 해결 방법

### 1) PATH 영구 수정

- 환경 변수 편집 → Path → 텍스트 편집에서 아래 패턴 확인
  - 잘못된 예시:
    ```
    ...\GitHubDesktop\binC:\Users\ParkJaeHong\.vscode\extensions\...
    ```
  - 올바른 예시:
    ```
    ...\GitHubDesktop\bin;C:\Users\ParkJaeHong\.vscode\extensions\...
    ```

### 2) Gradle 데몬 재시작

```
.\gradlew.bat --stop
```

새 터미널을 열고 테스트를 다시 실행한다.

### 3) Docker Desktop 실행 확인

```
docker version
```

정상 응답이 나오면 테스트를 다시 실행한다.

---

## 확인

```
.\gradlew.bat test --tests com.beachcheck.integration.ReservationControllerIntegrationTest
```

---

## 학습 포인트

### 1) Testcontainers 초기화는 PATH 파싱에 의존
- DockerMachine 탐색 과정에서 PATH 문자열을 파싱한다.
- Windows에서는 경로 구분자 누락이 바로 `InvalidPathException`으로 이어진다.

### 2) Docker 데몬은 필수 의존성
- Docker Desktop이 꺼져 있으면 Testcontainers는 클라이언트를 찾지 못한다.
- 오류가 `DockerClientProviderStrategy` 단계에서 발생한다.

---

## 대안 (우회)

DockerMachine 탐색이 불필요한 경우 다음 환경변수로 우회 가능:

```
TESTCONTAINERS_DOCKER_MACHINE_ENABLED=false
```

---

## 트러블슈팅 타임라인 (상세)

### 1) 최초 실패

실행:
```
.\gradlew.bat test --tests com.beachcheck.integration.ReservationControllerIntegrationTest
```

핵심 로그:
```
java.nio.file.InvalidPathException: Illegal char ':' at index 53:
C:\Users\ParkJaeHong\AppData\Local\GitHubDesktop\binc:\Users\ParkJaeHong\.vscode\extensions\...
```

### 2) PATH 확인 및 임시 수정

PATH 출력:
```
$env:Path
```

문제 구간:
```
...GitHubDesktop\binc:\Users\ParkJaeHong\.vscode\extensions...
```

세션 임시 수정:
```
$env:Path = $env:Path -replace 'GitHubDesktop\\bin(?=c:\\Users\\ParkJaeHong\\.vscode\\extensions)', 'GitHubDesktop\\bin;'
```

### 3) 영구 PATH 수정

- 환경 변수 편집 -> Path -> 텍스트 편집에서 아래 구간 확인
  - 잘못된 예시:
    ```
    ...\GitHubDesktop\binC:\Users\ParkJaeHong\.vscode\extensions\...
    ```
  - 올바른 예시:
    ```
    ...\GitHubDesktop\bin;C:\Users\ParkJaeHong\.vscode\extensions\...
    ```

수정 후 새 터미널에서 PATH 재확인:
```
$env:Path
```

### 4) Gradle 데몬 재시작

```
.\gradlew.bat --stop
```

### 5) 다음 실패 (Docker 미실행)

핵심 로그:
```
java.lang.IllegalStateException at DockerClientProviderStrategy.java:277
```

원인: Docker Desktop이 꺼져 있음.

### 6) Docker Desktop 실행 후 재시도

```
.\gradlew.bat test --tests com.beachcheck.integration.ReservationControllerIntegrationTest
```

### 7) 기능 테스트 실패 (응답 포맷/상태코드)

reservedAtUtc 포맷 불일치:
```
JSON path "$.reservedAtUtc" expected:<2026-...Z> but was:<1768627959.000000000>
```

인증 없음 응답 코드 불일치:
```
Status expected:<401> but was:<403>
```

### 8) application-test.yml 설정 수정

오타 수정:
```
WRITE_DATES_AS_TIMESTAMPS: false
```
```
write-dates-as-timestamps: false
```

### 9) WebConfig Jackson 설정 보정

원인: `WebConfig.jackson2ObjectMapperBuilder()`가 설정을 덮어씀.
조치: `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` 비활성화 추가.

### 10) Security 401 응답 설정

원인: 미인증 요청이 403 반환.
조치: `authenticationEntryPoint`를 설정하여 401 반환.

### 11) 최종 성공

```
BUILD SUCCESSFUL
```

---

## 추가: 고정 Clock 설정 시 BeanDefinitionOverrideException

### 증상

통합테스트에서 `Clock` 빈을 테스트용으로 등록했을 때 컨텍스트 로딩 실패:

```
BeanDefinitionOverrideException
```

### 원인

프로덕션 설정(`AppConfig`)의 `Clock` 빈과 테스트 설정의 `Clock` 빈이 충돌했다.
테스트 환경에서 기본적으로 빈 오버라이드가 허용되지 않아 컨텍스트가 중단됨.

### 해결

`@TestConfiguration`으로 `Clock` 빈을 추가하는 대신 `@MockBean Clock`을 사용한다.

예시:
```
@MockBean private Clock clock;

@BeforeEach
void setUp() {
  when(clock.instant()).thenReturn(FIXED_NOW);
  when(clock.getZone()).thenReturn(ZoneOffset.UTC);
}
```

### 결과

테스트에서 고정 시간(`FIXED_NOW`)을 안정적으로 사용 가능하고,
빈 충돌 없이 컨텍스트가 정상 로딩됨.
