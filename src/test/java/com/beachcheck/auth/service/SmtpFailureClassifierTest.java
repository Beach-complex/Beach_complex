package com.beachcheck.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.InternetAddress;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

class SmtpFailureClassifierTest {
  private final SmtpFailureClassifier classifier = new SmtpFailureClassifier();

  @Test
  void transientResponsesAndNetworkFailuresAreRetryable() throws Exception {
    assertThat(
            classifier.retryable(
                new MailSendException("network", new java.net.SocketTimeoutException())))
        .isTrue();
    assertThat(
            classifier.retryable(
                new MailSendException(
                    "smtp",
                    new SMTPSendFailedException("DATA", 451, "temporary", null, null, null, null))))
        .isTrue();
    assertThat(
            classifier.retryable(
                new MailSendException(
                    "smtp",
                    new SMTPAddressFailedException(
                        new InternetAddress("test@example.com"), "RCPT", 450, "temporary"))))
        .isTrue();
  }

  @Test
  void permanentResponsesAndBadCredentialsFailImmediately() throws Exception {
    assertThat(classifier.retryable(new MailAuthenticationException("auth"))).isFalse();
    assertThat(classifier.retryable(new MailParseException("invalid"))).isFalse();
    assertThat(
            classifier.retryable(
                new MailSendException(
                    "smtp",
                    new SMTPSendFailedException("DATA", 554, "permanent", null, null, null, null))))
        .isFalse();
    var smtp =
        new SMTPAddressFailedException(
            new InternetAddress("test@example.com"), "RCPT", 550, "unknown");
    assertThat(classifier.retryable(new MailSendException(java.util.Map.of("message", smtp))))
        .isFalse();
    assertThat(
            classifier.retryable(
                new MailSendException("auth", new AuthenticationFailedException())))
        .isFalse();
    assertThat(classifier.retryable(new IllegalStateException("unexpected"))).isFalse();
  }
}
