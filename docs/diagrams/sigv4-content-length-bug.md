# SigV4 Content-Length 서명 불일치 버그 다이어그램

| 날짜 | 작성자 | 변경 내용 |
|:---:|:---:|:---|
| 2026-04-04 | 박건우(@geonusp) | 문서 생성 |
| 2026-04-04 | 박건우(@geonusp) | 제거 위치 정정 (RequestContent → Spring addHeaders) |

관련 트러블슈팅: `docs/troubleshooting/lambda-function-url-sigv4-signature-mismatch.md`

---

```mermaid
sequenceDiagram
    participant SC as BeachConditionScheduler
    participant CC as CongestionClient
    participant SV as AwsSigV4Interceptor
    participant HC as HttpComponentsClientHttpRequest
    participant AW as AWS Lambda Function URL

    SC->>CC: fetchCurrent("SONGJEONG")
    CC->>SV: intercept()

    Note over SV: ❶ 서명 시점
    Note over SV: request.getHeaders() 전체 읽음
    Note over SV: Content-Length:0 포함하여 canonical request 구성
    Note over SV: SignedHeaders = content-length + host + x-amz-date + x-amz-security-token
    Note over SV: Signature = HMAC-SHA256(...) → "abc123"

    SV->>HC: execution.execute(signedRequest, body)
    Note over SV,HC: Authorization - SignedHeaders에 content-length 포함 선언
    Note over SV,HC: Spring ClientHttpRequest에 Content-Length: 0 존재

    Note over HC: ❷ Spring → Apache 객체 변환
    Note over HC: executeInternal() → addHeaders() 호출
    Note over HC: addHeaders()는 Content-Length, Transfer-Encoding을
    Note over HC: Apache ClassicHttpRequest에 복사하지 않음 ✂️
    Note over HC: (Spring Framework 명시적 제외 처리)
    Note over HC: Content-Length: 0 이 여기서 사라짐

    HC->>AW: TCP 전송
    Note over HC,AW: Authorization - SignedHeaders에 content-length 포함 선언 (그대로)
    Note over HC,AW: ❌ 실제 요청에 Content-Length 헤더 없음

    Note over AW: ❸ AWS SigV4 검증
    Note over AW: SignedHeaders 선언 기준으로 canonical request 재구성
    Note over AW: content-length 헤더 탐색 → 없음 → 공백으로 처리
    Note over AW: AWS 재계산 Signature → "xyz789"
    Note over AW: abc123 ≠ xyz789 → 불일치

    AW-->>HC: 403 SignatureDoesNotMatch
    HC-->>SV: HttpClientErrorException$Forbidden
    SV-->>CC: 예외 전파
    CC-->>SC: 혼잡도 조회 실패 로그
```

---

## 핵심 모순

| 시점 | Content-Length | Signature |
|:-----|:--------------|:----------|
| 서명 시점 (AwsSigV4Interceptor) | 존재 (`content-length: 0`) | `abc123` |
| 전송 시점 (addHeaders 이후) | 제거됨 | - |
| AWS 검증 시점 | 없음 → 공백으로 재계산 | `xyz789` |

**범인:** Spring `HttpComponentsClientHttpRequest.addHeaders()`가 Apache 객체로 변환 시 `Content-Length`, `Transfer-Encoding`을 명시적으로 복사하지 않음

**소스:**
- [HttpComponentsClientHttpRequest.java - addHeaders()](https://github.com/spring-projects/spring-framework/blob/main/spring-web/src/main/java/org/springframework/http/client/HttpComponentsClientHttpRequest.java)
- [RequestContent.java](https://github.com/apache/httpcomponents-core/blob/master/httpcore5/src/main/java/org/apache/hc/core5/http/protocol/RequestContent.java)
