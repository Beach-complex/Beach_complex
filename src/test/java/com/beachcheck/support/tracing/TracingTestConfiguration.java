package com.beachcheck.support.tracing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TracingTestConfiguration {

  @Bean
  RecordingSpanExporter recordingSpanExporter() {
    return new RecordingSpanExporter();
  }
}
