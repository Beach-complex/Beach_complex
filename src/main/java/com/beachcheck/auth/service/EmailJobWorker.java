package com.beachcheck.auth.service;

import com.beachcheck.auth.repository.EmailJobStore;
import com.beachcheck.auth.repository.EmailJobStore.Job;
import com.beachcheck.global.util.HashUtils;
import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailJobWorker {
  private static final Logger log = LoggerFactory.getLogger(EmailJobWorker.class);
  private final EmailJobStore store;
  private final EmailSender sender;
  private final EmailPayloadCipher cipher;
  private final SmtpFailureClassifier classifier;
  private final Tracer tracer;
  private final String from;

  public EmailJobWorker(
      EmailJobStore store,
      EmailSender sender,
      EmailPayloadCipher cipher,
      SmtpFailureClassifier classifier,
      Tracer tracer,
      @Value("${app.email-verification.from-address:}") String from) {
    this.store = store;
    this.sender = sender;
    this.cipher = cipher;
    this.classifier = classifier;
    this.tracer = tracer;
    this.from = from;
  }

  public void poll() {
    // 직렬로 소량 처리하여 메모리 큐에 선점한 작업을 오래 보관하지 않는다.
    for (int i = 0; i < 20; i++) {
      var claimed = store.claim();
      if (claimed.isEmpty()) return;
      deliver(claimed.get());
    }
  }

  private void deliver(Job job) {
    Span.Builder builder =
        tracer.spanBuilder().setNoParent().name("email.verification.send").kind(Span.Kind.CONSUMER);
    String parent = job.traceparent();
    if (parent != null && parent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]")) {
      String[] parts = parent.split("-");
      builder.addLink(
          new Link(
              tracer
                  .traceContextBuilder()
                  .traceId(parts[1])
                  .spanId(parts[2])
                  .sampled(parts[3].equals("01"))
                  .build()));
    }
    Span work = builder.start().tag("email.operation", "verification");
    try (Tracer.SpanInScope ignored = tracer.withSpan(work)) {
      String status = "SUCCEEDED";
      String outcome = "ACCEPTED";
      Span smtp =
          tracer
              .nextSpan()
              .name("email.smtp.send")
              .tag("email.retry.attempt", String.valueOf(job.attempt()))
              .start();
      try (Tracer.SpanInScope smtpScope = tracer.withSpan(smtp)) {
        sender.send(
            from,
            job.recipient(),
            "이메일 인증",
            "아래 링크를 클릭하여 이메일을 인증해주세요:\n\n" + cipher.decrypt(job.payload()));
        smtp.tag("email.delivery.outcome", "success");
      } catch (RuntimeException failure) {
        boolean retry = classifier.retryable(failure);
        status = retry && job.attempt() < 4 ? "RETRY_WAIT" : "FAILED";
        outcome = retry ? "TRANSIENT_SMTP_FAILURE" : "PERMANENT_FAILURE";
        smtp.tag("email.delivery.outcome", "error");
        smtp.tag("error.type", failure.getClass().getName());
        smtp.error(new IllegalStateException("SMTP 이메일 전송 실패"));
      } finally {
        smtp.end();
      }
      // 성공 기록 실패는 SMTP 실패로 분류하지 않는다. SENDING을 보존해 lease 만료 후 복구한다.
      boolean recorded = store.finish(job, status, outcome, 5000L << (job.attempt() - 1));
      work.tag(
          "email.delivery.outcome",
          recorded ? status.toLowerCase(java.util.Locale.ROOT) : "stale_claim");
      log.info(
          "Email job={} attempt={} outcome={} recipientHash={}",
          job.id(),
          job.attempt(),
          recorded ? status : "STALE_CLAIM",
          HashUtils.sha256Hex(job.recipient()));
    } finally {
      work.end();
    }
  }
}
