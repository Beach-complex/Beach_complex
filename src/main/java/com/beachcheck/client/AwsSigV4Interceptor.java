package com.beachcheck.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

@Component
public class AwsSigV4Interceptor implements CongestionInterceptor {
  private static final Logger log = LoggerFactory.getLogger(AwsSigV4Interceptor.class);
  private static final String SIGNING_NAME = "lambda";
  private static final String X_AMZ_DATE = "X-Amz-Date";
  private static final String X_AMZ_SECURITY_TOKEN = "X-Amz-Security-Token";

  /**
   * Why: Lambda Function URL의 AWS_IAM 인증 방식은 요청마다 SigV4 서명 헤더를 요구한다. RestClient는 기본적으로 SigV4를 지원하지
   * 않으므로 인터셉터로 서명 로직을 주입한다.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>모든 요청에 Aws4Signer로 서명하여 Authorization, X-Amz-Date 헤더를 추가한다.
   *   <li>서명 대상 서비스명은 "lambda"로 고정한다.
   * </ul>
   *
   * <p>Contract(Input): 서명할 HttpRequest, 요청 body byte[]
   *
   * <p>Contract(Output): 서명 헤더가 병합된 요청으로 실행한 ClientHttpResponse
   */
  private final AwsCredentialsProvider credentialsProvider;

  private final String region;
  private final Aws4Signer signer = Aws4Signer.create();

  public AwsSigV4Interceptor(
      AwsCredentialsProvider credentialsProvider, @Value("${app.aws.region}") String region) {
    this.credentialsProvider = credentialsProvider;
    this.region = region;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    SdkHttpFullRequest sdkRequest = toSdkRequest(request, body);
    AwsCredentials credentials = credentialsProvider.resolveCredentials();

    Aws4SignerParams signerParams =
        Aws4SignerParams.builder()
            .signingName(SIGNING_NAME)
            .signingRegion(Region.of(region))
            .awsCredentials(credentials)
            .build();

    SdkHttpFullRequest signedRequest = signer.sign(sdkRequest, signerParams);
    Map<String, List<String>> signedHeaders = signedRequest.headers();

    HttpRequest signedHttpRequest = new HttpRequestWrapper(request) { // super(request)와 동일한 효과
          @Override // 익명 클래스를 "원래 있는 클래스"로 풀어쓰면 암묵적으로 상속 발생!
          public HttpHeaders getHeaders() {
            HttpHeaders merged = new HttpHeaders(); // 업캐스팅
            merged.putAll( // 원본 헤더 먼저 넣기
                super.getHeaders()); // super.getHeaders()를 통해 부모가 감싸고 있던 원본 요청의 헤더를 모두 가져와 merged에
            // 복사
            signedHeaders.forEach(merged::put); // 서명 헤더 나중에 -> 같은 키면 덮어씀
            return merged;
          }
        };

    logSignedRequest(signedHttpRequest, body);
    try (SigV4Diagnostics.Scope ignored = SigV4Diagnostics.open(signedHttpRequest, body)) {
      return execution.execute(signedHttpRequest, body);
    }
  }

  private SdkHttpFullRequest toSdkRequest(HttpRequest request, byte[] body) {
    SdkHttpFullRequest.Builder builder =
        SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.fromValue(request.getMethod().name()))
            .uri(request.getURI());

    if (shouldAttachBodyForSigning(
        request.getMethod(),
        body)) { // 빈 body + safe method 조합에서는 contentStreamProvider를 붙이지 않아 Content-Length 서명 문제 방지
      builder.contentStreamProvider(() -> new ByteArrayInputStream(body));
    }

    request
        .getHeaders()
        .forEach(
            (headerName, headerValues) -> {
              if (shouldSkipTransportManagedHeader(
                  headerName)) { // Content-Length, Transfer-Encoding 값과 무관하게 항상 제외 (Apache
                // HttpClient가 전송 시점에 이 헤더들을 제거하거나 재결정하기 때문)
                return;
              }
              builder.putHeader(headerName, headerValues);
            });
    return builder.build();
  }

  /**
   * Why: Content-Length, Transfer-Encoding은 HTTP 메시지 프레이밍 헤더(RFC 9112 §6)로, Spring request factory가
   * 실제 전송 직전 제거하거나 재결정한다. 서명 시점에 이 헤더를 canonical request에 포함하면, 전송 시점의 canonical request와 달라져
   * SignatureDoesNotMatch가 발생한다.
   *
   * <p>Policy: Content-Length, Transfer-Encoding은 서명 대상에서 제외한다.
   *
   * <p>Contract(Input): HTTP 헤더 이름 문자열
   *
   * <p>Contract(Output): true면 서명 입력에서 제외, false면 포함
   */
  private boolean shouldSkipTransportManagedHeader(String headerName) {
    return HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(headerName)
        || HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(headerName);
  }

  /**
   * Why: 빈 stream을 contentStreamProvider에 붙이면 AWS SDK가 Content-Length: 0을 canonical request에 자동
   * 삽입한다. body가 없는 safe method에서는 stream 자체를 붙이지 않아야 Content-Length가 서명에 포함되지 않는다.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>body가 있으면 메서드 무관하게 stream을 붙인다.
   *   <li>body가 없는 safe method(GET/HEAD/OPTIONS/TRACE)는 stream을 붙이지 않는다.
   *   <li>body가 없는 non-safe method(POST/PUT 등)는 빈 ByteArrayInputStream을 contentStreamProvider에 붙인다.
   * </ul>
   *
   * <p>Contract(Input): HTTP 메서드, 요청 body byte[]
   *
   * <p>Contract(Output): true면 contentStreamProvider 첨부, false면 미첨부
   */
  private boolean shouldAttachBodyForSigning(HttpMethod method, byte[] body) {
    if (body.length > 0) {
      return true;
    }

    return !isSafeMethod(method);
  }

  /**
   * Why: RFC 7231 기준 safe method는 서버 상태를 변경하지 않으므로 body를 포함하지 않는 것이 일반적이다. 빈 body + safe method
   * 조합에서만 contentStreamProvider를 생략해 Content-Length 서명 문제를 방지한다.
   *
   * <p>Policy: GET, HEAD, OPTIONS, TRACE를 safe method로 간주한다.
   *
   * <p>Contract(Output): true면 safe method
   */
  private boolean isSafeMethod(HttpMethod method) {
    return method == HttpMethod.GET
        || method == HttpMethod.HEAD
        || method == HttpMethod.OPTIONS
        || method == HttpMethod.TRACE;
  }

  private void logSignedRequest(HttpRequest request, byte[] body) {
    if (!log.isDebugEnabled()) {
      return;
    }

    HttpHeaders headers = request.getHeaders();
    log.debug(
        "SigV4 request prepared method={} uri={} uriHost={} signedHost={} contentType={} bodyLength={} signingScope={} headers={}",
        request.getMethod(),
        request.getURI(),
        request.getURI().getHost(),
        headers.getFirst(HttpHeaders.HOST),
        headers.getFirst(HttpHeaders.CONTENT_TYPE),
        body.length,
        extractCredentialScope(headers.getFirst(HttpHeaders.AUTHORIZATION)),
        maskedDiagnosticHeaders(headers));
  }

  private Map<String, String> maskedDiagnosticHeaders(HttpHeaders headers) {
    Map<String, String> masked = new LinkedHashMap<>();
    masked.put(
        HttpHeaders.AUTHORIZATION, maskAuthorization(headers.getFirst(HttpHeaders.AUTHORIZATION)));
    masked.put(X_AMZ_DATE, headers.getFirst(X_AMZ_DATE));
    masked.put(X_AMZ_SECURITY_TOKEN, maskToken(headers.getFirst(X_AMZ_SECURITY_TOKEN)));
    masked.put(HttpHeaders.HOST, headers.getFirst(HttpHeaders.HOST));
    masked.put(HttpHeaders.CONTENT_TYPE, headers.getFirst(HttpHeaders.CONTENT_TYPE));
    return masked;
  }

  private String extractCredentialScope(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }

    int credentialStart = authorization.indexOf("Credential=");
    if (credentialStart < 0) {
      return null;
    }

    credentialStart += "Credential=".length();
    int credentialEnd = authorization.indexOf(',', credentialStart);
    String credential =
        (credentialEnd >= 0
                ? authorization.substring(credentialStart, credentialEnd)
                : authorization.substring(credentialStart))
            .trim();

    int accessKeySeparator = credential.indexOf('/');
    return accessKeySeparator >= 0 ? credential.substring(accessKeySeparator + 1) : null;
  }

  private String maskAuthorization(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }

    String masked = authorization.replaceAll("Credential=([^/]+)", "Credential=****");
    return masked.replaceAll("Signature=([0-9a-fA-F]+)", "Signature=****");
  }

  private String maskToken(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }

    if (token.length() <= 8) {
      return "****";
    }

    return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
  }
}
