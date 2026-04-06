package com.beachcheck.client;

import java.io.IOException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

/** Why: Apache HttpClient execution chain 안에서 내부 copy 이후의 요청 snapshot을 수집하기 위해. */
public class SigV4DiagnosticApacheRequestInterceptor implements HttpRequestInterceptor {
  @Override
  public void process(HttpRequest request, EntityDetails entity, HttpContext context)
      throws HttpException, IOException {
    SigV4Diagnostics.captureApacheLayer(request, entity, context);
  }
}
