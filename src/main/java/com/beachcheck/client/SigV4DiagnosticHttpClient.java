package com.beachcheck.client;

import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;

/** Why: Spring request factory가 Apache HttpClient에 넘긴 직후의 요청 snapshot을 수집하기 위해. */
public class SigV4DiagnosticHttpClient extends CloseableHttpClient {
  private final CloseableHttpClient delegate;

  public SigV4DiagnosticHttpClient(CloseableHttpClient delegate) {
    this.delegate = delegate;
  }

  @Override
  protected CloseableHttpResponse doExecute(
      HttpHost target, ClassicHttpRequest request, HttpContext context) throws IOException {
    SigV4Diagnostics.captureSpringLayer(request, determineEffectiveTarget(target, request));
    return this.delegate.execute(target, request, context);
  }

  @Override
  public void close() throws IOException {
    this.delegate.close();
  }

  @Override
  public void close(CloseMode closeMode) {
    this.delegate.close(closeMode);
  }

  private HttpHost determineEffectiveTarget(HttpHost target, ClassicHttpRequest request) {
    if (target != null) {
      return target;
    }

    try {
      return HttpHost.create(request.getUri());
    } catch (URISyntaxException | IllegalArgumentException ex) {
      return null;
    }
  }
}
