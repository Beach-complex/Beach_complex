package com.beachcheck.global.config;

import com.beachcheck.global.logging.MdcTaskDecorator;
import io.micrometer.tracing.Tracer;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

  /**
   * 비동기 작업용 Thread Pool 설정
   *
   * <p>Why: 알림 발송과 같은 시간이 오래 걸리는 작업을 비동기로 처리하여 메인 스레드를 블로킹하지 않기 위함.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>Core Pool Size: 3 (기본 유지 스레드 개수, 항상 살아있음)
   *   <li>Max Pool Size: 6 (최대 스레드 개수, 부하 시 3→6까지 증가)
   *   <li>Queue Capacity: 50 (대기 큐 크기, 스레드 풀이 가득 찰 때 대기)
   *   <li>Thread Name Prefix: "email-" (로그 추적 용이성)
   *   <li>Task Decorator: MdcTaskDecorator (부모 요청 스레드의 MDC를 worker thread로 전파)
   * </ul>
   *
   * <p>Contract(Output): TaskExecutor 인스턴스 반환
   *
   * @return 비동기 작업용 Executor
   */
  @Bean(name = "emailTaskExecutor")
  public Executor emailTaskExecutor(Tracer tracer) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(6);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("email-");
    executor.setTaskDecorator(new MdcTaskDecorator(tracer));
    executor.initialize();
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (exception, method, ignoredParameters) ->
        log.error(
            "비동기 작업 처리 실패 - method={}, errorType={}", method.getName(), rootErrorType(exception));
  }

  private String rootErrorType(Throwable exception) {
    Throwable current = exception;
    for (int depth = 0;
        depth < 16 && current.getCause() != null && current.getCause() != current;
        depth++) {
      current = current.getCause();
    }
    return current.getClass().getName();
  }

  // TODO: 성능 테스트 후 가상 스레드 사용 검토
}
