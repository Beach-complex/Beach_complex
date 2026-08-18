package com.beachcheck.global.tracing;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** HTTP 경계에서 baggage 없이 W3C Trace Context(traceparent, tracestate)만 전파한다. */
@Configuration(proxyBeanMethods = false)
public class W3cTracePropagationConfig {

  @Bean
  ContextPropagators w3cContextPropagators() {
    return ContextPropagators.create(W3CTraceContextPropagator.getInstance());
  }
}
