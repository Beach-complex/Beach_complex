package com.beachcheck.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.lang.Nullable;

/**
 * Why: SigV4 서명 직후 요청과 Spring/Apache HTTP 계층이 실제 전송 직전에 가진 요청을 같은 포맷으로 비교하기 위해.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>서명된 요청 snapshot은 인터셉터에서 ThreadLocal에 저장한다.
 *   <li>Spring request factory 계층과 Apache HttpClient 계층에서 같은 항목을 다시 추출해 비교 로그를 남긴다.
 *   <li>민감 정보는 마스킹 후 로그에 남긴다.
 * </ul>
 */
public final class SigV4Diagnostics {
  private static final Logger log = LoggerFactory.getLogger(SigV4Diagnostics.class);
  private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
  private static final String X_AMZ_DATE = "X-Amz-Date";
  private static final String X_AMZ_SECURITY_TOKEN = "X-Amz-Security-Token";
  private static final ThreadLocal<DiagnosticContext> CONTEXT = new ThreadLocal<>();

  private SigV4Diagnostics() {}

  public static Scope open(HttpRequest request, byte[] body) {
    if (!log.isDebugEnabled() && !log.isWarnEnabled()) {
      return Scope.NOOP;
    }

    RequestSnapshot signed = RequestSnapshot.fromSignedRequest(request, body);
    DiagnosticContext context =
        new DiagnosticContext(Long.toHexString(REQUEST_SEQUENCE.incrementAndGet()), signed);
    CONTEXT.set(context);
    log.debug("SigV4 diagnostics [{}] signed={}", context.requestId(), signed.toLogMap());
    return Scope.ACTIVE;
  }

  public static void captureSpringLayer(ClassicHttpRequest request, @Nullable HttpHost target) {
    DiagnosticContext context = CONTEXT.get();
    if (context == null) {
      return;
    }

    RequestSnapshot spring = RequestSnapshot.fromClassicRequest(request, target);
    context.setSpringSnapshot(spring);
    log.debug("SigV4 diagnostics [{}] spring={}", context.requestId(), spring.toLogMap());
    logComparison(context.requestId(), "signed", context.signedSnapshot(), "spring", spring);
  }

  public static void captureApacheLayer(
      org.apache.hc.core5.http.HttpRequest request,
      @Nullable EntityDetails entityDetails,
      HttpContext httpContext) {
    DiagnosticContext diagnosticContext = CONTEXT.get();
    if (diagnosticContext == null) {
      return;
    }

    HttpHost target = extractTargetHost(httpContext);
    RequestSnapshot apache = RequestSnapshot.fromApacheRequest(request, entityDetails, target);
    log.debug("SigV4 diagnostics [{}] apache={}", diagnosticContext.requestId(), apache.toLogMap());
    RequestSnapshot baseline =
        diagnosticContext.springSnapshot() != null
            ? diagnosticContext.springSnapshot()
            : diagnosticContext.signedSnapshot();
    String baselineName = diagnosticContext.springSnapshot() != null ? "spring" : "signed";
    logComparison(diagnosticContext.requestId(), baselineName, baseline, "apache", apache);
  }

  private static void logComparison(
      String requestId,
      String expectedLayer,
      RequestSnapshot expected,
      String actualLayer,
      RequestSnapshot actual) {
    List<String> diffs = expected.diff(actual);
    if (diffs.isEmpty()) {
      log.debug("SigV4 diagnostics [{}] {} -> {} matched", requestId, expectedLayer, actualLayer);
      return;
    }

    log.warn(
        "SigV4 diagnostics [{}] {} -> {} mismatch: {}",
        requestId,
        expectedLayer,
        actualLayer,
        diffs);
  }

  private static String nullSafe(@Nullable Object value) {
    return value != null ? value.toString() : null;
  }

  private static String firstHeaderOrNull(HttpHeaders headers, String name) {
    return headers.getFirst(name);
  }

  private static String firstHeaderOrNull(ClassicHttpRequest request, String name) {
    return request.getFirstHeader(name) != null ? request.getFirstHeader(name).getValue() : null;
  }

  private static String firstHeaderOrNull(
      org.apache.hc.core5.http.HttpRequest request, String name) {
    return request.getFirstHeader(name) != null ? request.getFirstHeader(name).getValue() : null;
  }

  private static String extractPathAndQuery(URI uri) {
    String path = uri.getRawPath();
    if (path == null || path.isBlank()) {
      path = "/";
    }
    return uri.getRawQuery() != null ? path + "?" + uri.getRawQuery() : path;
  }

  private static String normalizeHost(@Nullable String headerHost, URI uri) {
    if (headerHost != null && !headerHost.isBlank()) {
      return headerHost;
    }
    return uri.getRawAuthority();
  }

  private static String normalizeHost(
      @Nullable String headerHost, ClassicHttpRequest request, @Nullable HttpHost target) {
    if (headerHost != null && !headerHost.isBlank()) {
      return headerHost;
    }
    if (request.getAuthority() != null) {
      return request.getAuthority().toString();
    }
    if (target != null) {
      return target.toHostString();
    }
    return null;
  }

  private static String normalizeHost(
      @Nullable String headerHost,
      org.apache.hc.core5.http.HttpRequest request,
      @Nullable HttpHost target) {
    if (headerHost != null && !headerHost.isBlank()) {
      return headerHost;
    }
    if (request.getAuthority() != null) {
      return request.getAuthority().toString();
    }
    if (target != null) {
      return target.toHostString();
    }
    return null;
  }

  private static HttpHost extractTargetHost(HttpContext context) {
    try {
      HttpClientContext clientContext = HttpClientContext.adapt(context);
      return clientContext.getHttpRoute() != null
          ? clientContext.getHttpRoute().getTargetHost()
          : null;
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private static String maskAuthorization(@Nullable String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }

    String masked = authorization.replaceAll("Credential=([^/]+)", "Credential=****");
    return masked.replaceAll("Signature=([0-9a-fA-F]+)", "Signature=****");
  }

  private static String maskToken(@Nullable String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    if (token.length() <= 8) {
      return "****";
    }
    return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
  }

  public interface Scope extends AutoCloseable {
    Scope ACTIVE = CONTEXT::remove;
    Scope NOOP = () -> {};

    @Override
    void close();
  }

  private static final class DiagnosticContext {
    private final String requestId;
    private final RequestSnapshot signedSnapshot;
    private RequestSnapshot springSnapshot;

    private DiagnosticContext(String requestId, RequestSnapshot signedSnapshot) {
      this.requestId = requestId;
      this.signedSnapshot = signedSnapshot;
    }

    private String requestId() {
      return requestId;
    }

    private RequestSnapshot signedSnapshot() {
      return signedSnapshot;
    }

    private RequestSnapshot springSnapshot() {
      return springSnapshot;
    }

    private void setSpringSnapshot(RequestSnapshot springSnapshot) {
      this.springSnapshot = springSnapshot;
    }
  }

  private record RequestSnapshot(
      String method,
      String pathAndQuery,
      String host,
      String authorization,
      String xAmzDate,
      String xAmzSecurityToken,
      String contentLengthHeader,
      String transferEncodingHeader,
      Long entityContentLength,
      Boolean chunked,
      String targetHost) {

    private static RequestSnapshot fromSignedRequest(HttpRequest request, byte[] body) {
      HttpHeaders headers = request.getHeaders();
      return new RequestSnapshot(
          request.getMethod().name(),
          extractPathAndQuery(request.getURI()),
          normalizeHost(firstHeaderOrNull(headers, HttpHeaders.HOST), request.getURI()),
          maskAuthorization(firstHeaderOrNull(headers, HttpHeaders.AUTHORIZATION)),
          firstHeaderOrNull(headers, X_AMZ_DATE),
          maskToken(firstHeaderOrNull(headers, X_AMZ_SECURITY_TOKEN)),
          firstHeaderOrNull(headers, HttpHeaders.CONTENT_LENGTH),
          firstHeaderOrNull(headers, HttpHeaders.TRANSFER_ENCODING),
          (long) body.length,
          null,
          null);
    }

    private static RequestSnapshot fromClassicRequest(
        ClassicHttpRequest request, @Nullable HttpHost target) {
      HttpEntity entity = request.getEntity();
      return new RequestSnapshot(
          request.getMethod(),
          request.getPath(),
          normalizeHost(firstHeaderOrNull(request, HttpHeaders.HOST), request, target),
          maskAuthorization(firstHeaderOrNull(request, HttpHeaders.AUTHORIZATION)),
          firstHeaderOrNull(request, X_AMZ_DATE),
          maskToken(firstHeaderOrNull(request, X_AMZ_SECURITY_TOKEN)),
          firstHeaderOrNull(request, HttpHeaders.CONTENT_LENGTH),
          firstHeaderOrNull(request, HttpHeaders.TRANSFER_ENCODING),
          entity != null ? entity.getContentLength() : null,
          entity != null ? entity.isChunked() : null,
          target != null ? target.toURI() : null);
    }

    private static RequestSnapshot fromApacheRequest(
        org.apache.hc.core5.http.HttpRequest request,
        @Nullable EntityDetails entityDetails,
        @Nullable HttpHost target) {
      return new RequestSnapshot(
          request.getMethod(),
          request.getPath(),
          normalizeHost(firstHeaderOrNull(request, HttpHeaders.HOST), request, target),
          maskAuthorization(firstHeaderOrNull(request, HttpHeaders.AUTHORIZATION)),
          firstHeaderOrNull(request, X_AMZ_DATE),
          maskToken(firstHeaderOrNull(request, X_AMZ_SECURITY_TOKEN)),
          firstHeaderOrNull(request, HttpHeaders.CONTENT_LENGTH),
          firstHeaderOrNull(request, HttpHeaders.TRANSFER_ENCODING),
          entityDetails != null ? entityDetails.getContentLength() : null,
          entityDetails != null ? entityDetails.isChunked() : null,
          target != null ? target.toURI() : null);
    }

    private Map<String, Object> toLogMap() {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("method", method);
      values.put("pathAndQuery", pathAndQuery);
      values.put("host", host);
      values.put("authorization", authorization);
      values.put("xAmzDate", xAmzDate);
      values.put("xAmzSecurityToken", xAmzSecurityToken);
      values.put("contentLengthHeader", contentLengthHeader);
      values.put("transferEncodingHeader", transferEncodingHeader);
      values.put("entityContentLength", entityContentLength);
      values.put("chunked", chunked);
      values.put("targetHost", targetHost);
      return values;
    }

    private List<String> diff(RequestSnapshot other) {
      List<String> diffs = new ArrayList<>();
      addDiff(diffs, "method", method, other.method);
      addDiff(diffs, "pathAndQuery", pathAndQuery, other.pathAndQuery);
      addDiff(diffs, "host", host, other.host);
      addDiff(diffs, "authorization", authorization, other.authorization);
      addDiff(diffs, "xAmzDate", xAmzDate, other.xAmzDate);
      addDiff(diffs, "xAmzSecurityToken", xAmzSecurityToken, other.xAmzSecurityToken);
      if (isSignedHeader("content-length") || other.isSignedHeader("content-length")) {
        addDiff(diffs, "contentLengthHeader", contentLengthHeader, other.contentLengthHeader);
        addDiff(diffs, "entityContentLength", entityContentLength, other.entityContentLength);
      }
      if (isSignedHeader("transfer-encoding") || other.isSignedHeader("transfer-encoding")) {
        addDiff(
            diffs, "transferEncodingHeader", transferEncodingHeader, other.transferEncodingHeader);
        addDiff(diffs, "chunked", chunked, other.chunked);
      }
      return diffs;
    }

    private static void addDiff(List<String> diffs, String field, Object expected, Object actual) {
      if (!Objects.equals(expected, actual)) {
        diffs.add(field + "=" + nullSafe(expected) + " -> " + nullSafe(actual));
      }
    }

    private boolean isSignedHeader(String headerName) {
      if (authorization == null || authorization.isBlank()) {
        return false;
      }

      int signedHeadersStart = authorization.indexOf("SignedHeaders=");
      if (signedHeadersStart < 0) {
        return false;
      }

      signedHeadersStart += "SignedHeaders=".length();
      int signedHeadersEnd = authorization.indexOf(',', signedHeadersStart);
      String signedHeadersValue =
          signedHeadersEnd >= 0
              ? authorization.substring(signedHeadersStart, signedHeadersEnd)
              : authorization.substring(signedHeadersStart);

      Set<String> signedHeaders =
          new HashSet<>(Arrays.asList(signedHeadersValue.toLowerCase().split(";")));
      return signedHeaders.contains(headerName.toLowerCase());
    }
  }
}
