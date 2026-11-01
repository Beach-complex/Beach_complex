package com.beachcheck.auth.service;

import com.beachcheck.auth.repository.EmailJobStore;
import com.beachcheck.global.util.HashUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class EmailJobService {
  private final EmailJobStore store;
  private final EmailPayloadCipher cipher;
  private final Tracer tracer;

  public EmailJobService(EmailJobStore store, EmailPayloadCipher cipher, Tracer tracer) {
    this.store = store;
    this.cipher = cipher;
    this.tracer = tracer;
  }

  @Transactional
  public UUID enqueue(String email, String verificationLink) {
    String rawToken = verificationLink.substring(verificationLink.lastIndexOf("?token=") + 7);
    Span current = tracer.currentSpan();
    String parent =
        current == null
            ? null
            : "00-"
                + current.context().traceId()
                + "-"
                + current.context().spanId()
                + (Boolean.TRUE.equals(current.context().sampled()) ? "-01" : "-00");
    return store.enqueue(
        email, cipher.encrypt(verificationLink), HashUtils.sha256Hex(rawToken), parent);
  }
}
