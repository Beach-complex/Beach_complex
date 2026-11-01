package com.beachcheck.auth.service;

import jakarta.mail.MessagingException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

@Component
public class SmtpFailureClassifier {
  public boolean retryable(Throwable failure) {
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    return !permanent(failure, visited) && failure instanceof MailSendException;
  }

  private boolean permanent(Throwable error, Set<Throwable> visited) {
    if (error == null || !visited.add(error)) return false;
    if (error instanceof jakarta.mail.AuthenticationFailedException
        || error instanceof jakarta.mail.internet.AddressException) return true;
    if (error instanceof MailAuthenticationException
        || error instanceof MailParseException
        || error instanceof MailPreparationException) return true;
    if (error instanceof SMTPAddressFailedException smtp && smtp.getReturnCode() >= 500)
      return true;
    if (error instanceof SMTPSendFailedException smtp && smtp.getReturnCode() >= 500) return true;
    if (error instanceof jakarta.mail.SendFailedException send
        && send.getInvalidAddresses() != null
        && send.getInvalidAddresses().length > 0) return true;
    if (error instanceof MailSendException mail) {
      for (Exception nested : mail.getMessageExceptions()) {
        if (permanent(nested, visited)) return true;
      }
    }
    if (error instanceof MessagingException mail && permanent(mail.getNextException(), visited))
      return true;
    return permanent(error.getCause(), visited);
  }
}
