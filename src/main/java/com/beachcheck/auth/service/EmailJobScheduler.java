package com.beachcheck.auth.service;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"app.mail.enabled", "app.email.jobs.polling-enabled"},
    havingValue = "true")
public class EmailJobScheduler {
  private static final Logger log = LoggerFactory.getLogger(EmailJobScheduler.class);
  // 기존 emailTaskExecutor의 core pool 크기만큼만 소비 작업을 예약한다.
  private static final int WORKERS = 3;
  private final Semaphore slots = new Semaphore(WORKERS);
  private final EmailJobWorker worker;
  private final Executor executor;

  public EmailJobScheduler(
      EmailJobWorker worker, @Qualifier("emailTaskExecutor") Executor executor) {
    this.worker = worker;
    this.executor = executor;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(fixedDelayString = "${app.email.jobs.poll-interval-ms:1000}")
  public void poll() {
    for (int i = 0; i < WORKERS && slots.tryAcquire(); i++) {
      try {
        // 실행기에 대기 중인 동안에는 DB 작업을 선점하지 않는다.
        executor.execute(this::runWorker);
      } catch (RuntimeException failure) {
        slots.release();
        log.error("Email job dispatch failed: errorType={}", failure.getClass().getName());
        return;
      }
    }
  }

  private void runWorker() {
    try {
      worker.poll();
    } catch (RuntimeException failure) {
      // DB/SMTP 예외 원문은 주소·인증 링크를 포함할 수 있다.
      log.error("Email job polling failed: errorType={}", failure.getClass().getName());
    } finally {
      slots.release();
    }
  }
}
