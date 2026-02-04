package com.beachcheck.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailEventListener {

  private final AsyncEmailService asyncEmailService;

  public EmailEventListener(AsyncEmailService asyncEmailService) {
    this.asyncEmailService = asyncEmailService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleEmailVerificationEvent(EmailVerificationEvent event) {
    asyncEmailService.sendVerificationEmailAsync(event.email(), event.verificationLink());
  }
}
