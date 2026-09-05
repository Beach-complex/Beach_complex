package com.beachcheck.global.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.observation.ClientRequestObservationContext;

@Configuration(proxyBeanMethods = false)
public class HttpClientTracingConfiguration {

  // JDBC 핸들러도 PropagatingSenderTracingObservationHandler를 상속하므로 Boot의 기본
  // sender 핸들러 자동 등록이 생략된다. HTTP 전용으로 등록해 JDBC 오류 정제는 유지한다.
  @Bean
  @Order(MicrometerTracingAutoConfiguration.SENDER_TRACING_OBSERVATION_HANDLER_ORDER)
  PropagatingSenderTracingObservationHandler<ClientRequestObservationContext>
      httpClientTracingObservationHandler(Tracer tracer, Propagator propagator) {
    return new PropagatingSenderTracingObservationHandler<>(tracer, propagator) {
      @Override
      public boolean supportsContext(Observation.Context context) {
        return context instanceof ClientRequestObservationContext;
      }
    };
  }
}
