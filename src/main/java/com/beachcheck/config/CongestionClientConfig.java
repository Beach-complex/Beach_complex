package com.beachcheck.config;

import com.beachcheck.client.SigV4DiagnosticApacheRequestInterceptor;
import com.beachcheck.client.SigV4DiagnosticHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@Configuration
public class CongestionClientConfig {
  @Bean
  @Qualifier("congestionClientRequestFactory")
  public HttpComponentsClientHttpRequestFactory congestionClientRequestFactory() {
    CloseableHttpClient delegate =
        HttpClients.custom()
            .useSystemProperties()
            .addRequestInterceptorLast(new SigV4DiagnosticApacheRequestInterceptor())
            .build();
    return new HttpComponentsClientHttpRequestFactory(new SigV4DiagnosticHttpClient(delegate));
  }
}
