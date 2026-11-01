package com.beachcheck.auth.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class EmailEventListener {

  private final EmailJobService emailJobService;

  public EmailEventListener(EmailJobService emailJobService) {
    this.emailJobService = emailJobService;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void handleEmailVerificationEvent(EmailVerificationEvent event) {
    emailJobService.enqueue(event.email(), event.verificationLink());
  }
}
