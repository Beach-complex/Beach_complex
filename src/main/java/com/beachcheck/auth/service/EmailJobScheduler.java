package com.beachcheck.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "app.email.jobs.polling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EmailJobScheduler {
  private static final Logger log = LoggerFactory.getLogger(EmailJobScheduler.class);
  private final EmailJobWorker worker;

  public EmailJobScheduler(EmailJobWorker worker) {
    this.worker = worker;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(fixedDelayString = "${app.email.jobs.poll-interval-ms:1000}")
  public void poll() {
    try {
      worker.poll();
    } catch (RuntimeException failure) {
      // DB/SMTP 예외 원문은 주소·인증 링크를 포함할 수 있다.
      log.error("Email job polling failed: errorType={}", failure.getClass().getName());
    }
  }
}
