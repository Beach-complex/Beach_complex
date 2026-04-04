# SigV4 진단/로그 구조 다이어그램

| 날짜 | 작성자 | 변경 내용 |
|:---:|:---:|:---|
| 2026-04-04 | 박건우(@geonusp) | 문서 생성 |

---

## 1. 전체 클래스 관계

```mermaid
classDiagram
    class AwsSigV4Interceptor {
        +intercept()
        -logSignedRequest()
        -maskedDiagnosticHeaders()
        -maskAuthorization()
        -maskToken()
        -extractCredentialScope()
    }

    class SigV4Diagnostics {
        -ThreadLocal~DiagnosticContext~ CONTEXT
        -AtomicLong REQUEST_SEQUENCE
        +open(request, body) Scope
        +captureSpringLayer(request, target)
        +captureApacheLayer(request, entity, context)
        -logComparison()
    }

    class DiagnosticContext {
        -String requestId
        -RequestSnapshot signedSnapshot
        -RequestSnapshot springSnapshot
    }

    class RequestSnapshot {
        -method
        -pathAndQuery
        -host
        -authorization
        -xAmzDate
        -xAmzSecurityToken
        -contentLengthHeader
        -transferEncodingHeader
        -entityContentLength
        -chunked
        +diff(other) List~String~
        +isSignedHeader(name) boolean
    }

    class SigV4DiagnosticHttpClient {
        -CloseableHttpClient delegate
        +doExecute()
    }

    class SigV4DiagnosticApacheRequestInterceptor {
        +process()
    }

    class CongestionClientConfig {
        +congestionClientRequestFactory()
    }

    AwsSigV4Interceptor ..> SigV4Diagnostics : open()
    SigV4Diagnostics *-- DiagnosticContext
    DiagnosticContext *-- RequestSnapshot
    SigV4DiagnosticHttpClient ..> SigV4Diagnostics : captureSpringLayer()
    SigV4DiagnosticApacheRequestInterceptor ..> SigV4Diagnostics : captureApacheLayer()
    CongestionClientConfig ..> SigV4DiagnosticHttpClient : 조립
    CongestionClientConfig ..> SigV4DiagnosticApacheRequestInterceptor : 조립
```

---

## 2. 요청 흐름 + 스냅샷 수집 시점

```mermaid
sequenceDiagram
    participant SV as AwsSigV4Interceptor
    participant SD as SigV4Diagnostics
    participant TL as ThreadLocal
    participant DH as SigV4DiagnosticHttpClient
    participant AI as SigV4DiagnosticApacheRequestInterceptor

    SV->>SD: open(signedRequest, body)
    SD->>SD: RequestSnapshot.fromSignedRequest()
    SD->>TL: DiagnosticContext 저장
    Note over TL: { requestId, signedSnapshot }

    SV->>DH: delegate.execute()

    DH->>SD: captureSpringLayer(classicRequest, target)
    SD->>TL: context 조회
    SD->>SD: RequestSnapshot.fromClassicRequest()
    SD->>SD: logComparison(signed vs spring)
    Note over SD: signed → spring matched / mismatch

    DH->>AI: Apache 인터셉터 체인 실행

    AI->>SD: captureApacheLayer(request, entity, context)
    SD->>TL: context 조회
    SD->>SD: RequestSnapshot.fromApacheRequest()
    SD->>SD: logComparison(spring vs apache)
    Note over SD: spring → apache matched / mismatch

    AI-->>DH: 완료
    DH-->>SV: CloseableHttpResponse 반환
    SV->>TL: Scope.close() → ThreadLocal.remove()
```

---

## 3. ThreadLocal 생명주기

```mermaid
flowchart LR
    A["AwsSigV4Interceptor\n.intercept()"] -->|"SigV4Diagnostics.open()\n→ ThreadLocal.set(context)"| B["ThreadLocal\nDiagnosticContext"]

    B -->|"captureSpringLayer()\n→ context 조회 + springSnapshot 저장"| C["SigV4DiagnosticHttpClient\n.doExecute()"]

    B -->|"captureApacheLayer()\n→ context 조회"| D["SigV4DiagnosticApacheRequestInterceptor\n.process()"]

    C -->|"같은 스레드에서\n순차 실행"| D

    A -->|"Scope.close()\n→ ThreadLocal.remove()"| E["메모리 해제"]
```

---

## 4. RequestSnapshot 비교 항목

```mermaid
flowchart TD
    subgraph signed ["❶ signed (AwsSigV4Interceptor)"]
        S1[method]
        S2[pathAndQuery]
        S3[host]
        S4["authorization (마스킹)"]
        S5[xAmzDate]
        S6["xAmzSecurityToken (마스킹)"]
        S7["contentLengthHeader ← 핵심"]
        S8[transferEncodingHeader]
        S9[entityContentLength]
    end

    subgraph spring ["❷ spring (SigV4DiagnosticHttpClient)"]
        P1[method]
        P2[pathAndQuery]
        P3[host]
        P4["authorization (마스킹)"]
        P5[xAmzDate]
        P6["xAmzSecurityToken (마스킹)"]
        P7["contentLengthHeader ← 핵심"]
        P8[transferEncodingHeader]
        P9[entityContentLength]
    end

    subgraph apache ["❸ apache (SigV4DiagnosticApacheRequestInterceptor)"]
        A1[method]
        A2[pathAndQuery]
        A3[host]
        A4["authorization (마스킹)"]
        A5[xAmzDate]
        A6["xAmzSecurityToken (마스킹)"]
        A7["contentLengthHeader"]
        A8[transferEncodingHeader]
        A9[entityContentLength]
    end

    S7 -->|"0 → null\n✂️ 여기서 차이 발생"| P7
    P7 -->|"null → null\n일치"| A7
```

> **주의:** `content-length`, `transfer-encoding`은 `SignedHeaders`에 포함된 경우에만 비교한다.
> 서명 대상이 아닌 헤더 차이는 무시한다. (`RequestSnapshot.isSignedHeader()`)

---

## 5. 로그 출력 형태

### AwsSigV4Interceptor (영구 로그)
```
DEBUG AwsSigV4Interceptor :
  SigV4 request prepared
    method=GET
    uri=https://vfhbaio7...on.aws/congestion/current?beach_id=SONGJEONG
    uriHost=vfhbaio7...on.aws
    signedHost=vfhbaio7...on.aws
    signingScope=20260403/us-east-1/lambda/aws4_request
    headers={
      Authorization=AWS4-HMAC-SHA256 Credential=****/..., Signature=****
      X-Amz-Date=20260403T003605Z
      X-Amz-Security-Token=ASIA...OKEN
      Host=vfhbaio7...on.aws
    }
```

### SigV4Diagnostics (진단 로그 — 이후 제거 예정)
```
DEBUG SigV4Diagnostics : SigV4 diagnostics [1a] signed={method=GET, contentLengthHeader=0, ...}
DEBUG SigV4Diagnostics : SigV4 diagnostics [1a] spring={method=GET, contentLengthHeader=null, ...}
WARN  SigV4Diagnostics : SigV4 diagnostics [1a] signed -> spring mismatch:
        [contentLengthHeader=0 -> null, entityContentLength=0 -> null]

DEBUG SigV4Diagnostics : SigV4 diagnostics [1a] apache={method=GET, contentLengthHeader=null, ...}
DEBUG SigV4Diagnostics : SigV4 diagnostics [1a] spring -> apache matched
```

---

## 6. 유지/제거 구분

```mermaid
flowchart LR
    subgraph keep ["✅ 유지 (영구 로그)"]
        K1["AwsSigV4Interceptor\nlogSignedRequest()"]
        K2["maskedDiagnosticHeaders()"]
        K3["maskAuthorization()\nmaskToken()"]
        K4["extractCredentialScope()"]
    end

    subgraph remove ["🗑️ 제거 예정 (진단 도구)"]
        R1["SigV4Diagnostics"]
        R2["SigV4DiagnosticHttpClient"]
        R3["SigV4DiagnosticApacheRequestInterceptor"]
        R4["CongestionClientConfig\n(Apache 구성 전체)"]
    end
```