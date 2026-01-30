package com.beachcheck.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class RetryConfig {
  // @EnableRetry 어노테이션을 통해 Spring Retry 기능 활성화
}
