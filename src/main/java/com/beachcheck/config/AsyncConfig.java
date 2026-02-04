package com.beachcheck.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

  /**
   * 비동기 작업용 Thread Pool 설정
   *
   * <p>Why: 알림 발송과 같은 시간이 오래 걸리는 작업을 비동기로 처리하여 메인 스레드를 블로킹하지 않기 위함.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>Core Pool Size: 5 (기본 유지 스레드 개수, 항상 살아있음)
   *   <li>Max Pool Size: 10 (최대 스레드 개수, 부하 시 5→10까지 증가)
   *   <li>Queue Capacity: 100 (대기 큐 크기, 스레드 풀이 가득 찰 때 대기)
   *   <li>Thread Name Prefix: "notification-" (로그 추적 용이성)
   * </ul>
   *
   * <p>Contract(Output): TaskExecutor 인스턴스 반환
   *
   * @return 비동기 작업용 Executor
   */
  @Bean(name = "notificationTaskExecutor")
  public Executor notificationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("notification-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "emailTaskExecutor")
  public Executor emailTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(6);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("email-");
    executor.initialize();
    return executor;
  }

  // TODO: 성능 테스트 후 가상 스레드 사용 검토
}
