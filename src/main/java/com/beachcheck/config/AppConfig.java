package com.beachcheck.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
  /**
   * Why: 시간 소스를 주입 가능하게 만들어 테스트와 동작 일관성을 확보하기 위해. Policy: 애플리케이션은 UTC 기반 Clock 빈을 사용한다.
   * Contract(Input): Clock 빈이 필요하다. Contract(Output): UTC 시스템 Clock을 반환한다.
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
