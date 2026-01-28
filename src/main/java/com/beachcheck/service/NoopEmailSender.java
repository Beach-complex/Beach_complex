package com.beachcheck.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    prefix = "app.mail",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoopEmailSender implements EmailSender {
  private static final Logger log = LoggerFactory.getLogger(NoopEmailSender.class);

  @Override
  public void send(String from, String to, String subject, String body) {
    log.info("[NOOP] 이메일 전송 건너뜀 - to: {}, subject: {}", to, subject);
  }
}
