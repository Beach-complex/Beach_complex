package com.beachcheck.auth.integration;

import static com.beachcheck.support.fixture.EmailVerificationTestFixtures.emailUser;
import static com.beachcheck.support.fixture.UniqueTestFixtures.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.beachcheck.auth.repository.EmailJobStore;
import com.beachcheck.auth.repository.EmailJobStore.Job;
import com.beachcheck.auth.service.EmailJobScheduler;
import com.beachcheck.auth.service.EmailJobWorker;
import com.beachcheck.auth.service.EmailPayloadCipher;
import com.beachcheck.auth.service.EmailSender;
import com.beachcheck.auth.service.EmailVerificationService;
import com.beachcheck.auth.service.SmtpFailureClassifier;
import com.beachcheck.support.base.IntegrationTest;
import com.beachcheck.user.domain.User;
import com.beachcheck.user.repository.UserRepository;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailJobIntegrationTest extends IntegrationTest {
  @Autowired private EmailVerificationService verification;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EmailJobWorker worker;
  @Autowired private EmailPayloadCipher cipher;
  @Autowired private EmailJobStore store;
  @MockBean private EmailSender sender;
  private User user;
  private UUID jobId;

  @BeforeEach
  void prepare() {
    jdbc.update("DELETE FROM email_job_attempts");
    jdbc.update("DELETE FROM email_jobs");
    user = users.save(emailUser(uniqueEmail("durable"), false));
    verification.sendVerification(user);
    jobId =
        jdbc.queryForObject(
            "SELECT id FROM email_jobs WHERE recipient = ?", UUID.class, user.getEmail());
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM email_job_attempts");
    jdbc.update("DELETE FROM email_jobs");
    users.deleteById(user.getId());
  }

  @Test
  void pendingSurvivesWorkerRecreationAndSuccessIsNotSentAgain() {
    var restarted =
        new EmailJobWorker(store, sender, cipher, new SmtpFailureClassifier(), Tracer.NOOP, "");
    new EmailJobScheduler(restarted).poll();
    worker.poll();
    assertThat(status()).isEqualTo("SUCCEEDED");
    assertThat(attempts()).isEqualTo(1);
    verify(sender, times(1)).send(anyString(), eq(user.getEmail()), anyString(), anyString());
    assertThat(
            jdbc.queryForObject("SELECT payload FROM email_jobs WHERE id = ?", String.class, jobId))
        .isNull();
    assertThat(history()).containsExactly("ACCEPTED");
  }

  @Test
  void staleSendingRecoversAndOldWorkerCannotOverwriteNewClaim() {
    Job old = store.claim().orElseThrow();
    assertThat(store.claim()).isEmpty();
    expireLease();
    Job recovered = store.claim().orElseThrow();
    assertThat(recovered.attempt()).isEqualTo(2);
    assertThat(store.finish(old, "SUCCEEDED", "ACCEPTED", 0)).isFalse();
    assertThat(status()).isEqualTo("SENDING");
    assertThat(store.finish(recovered, "SUCCEEDED", "ACCEPTED", 0)).isTrue();
    assertThat(history()).containsExactly("UNKNOWN", "ACCEPTED");
  }

  @Test
  void concurrentWorkersOnlySendOnce() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                start.await();
                worker.poll();
                return true;
              });
      var secondWorker =
          new EmailJobWorker(store, sender, cipher, new SmtpFailureClassifier(), Tracer.NOOP, "");
      var second =
          pool.submit(
              () -> {
                start.await();
                secondWorker.poll();
                return true;
              });
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }
    verify(sender, times(1)).send(anyString(), eq(user.getEmail()), anyString(), anyString());
    assertThat(attempts()).isEqualTo(1);
  }

  @Test
  void smtpAcceptanceThenDatabaseFailureLeavesUncertainAttemptAndMayDuplicate() {
    jdbc.execute(
        "ALTER TABLE email_job_attempts ADD CONSTRAINT test_reject_accepted CHECK (outcome <> 'ACCEPTED') NOT VALID");
    try {
      assertThatThrownBy(worker::poll)
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      jdbc.execute("ALTER TABLE email_job_attempts DROP CONSTRAINT test_reject_accepted");
    }
    assertThat(status()).isEqualTo("SENDING");
    assertThat(history()).containsExactly("STARTED");
    worker.poll();
    verify(sender, times(1)).send(anyString(), anyString(), anyString(), anyString());
    expireLease();
    worker.poll();
    assertThat(status()).isEqualTo("SUCCEEDED");
    verify(sender, times(2)).send(anyString(), anyString(), anyString(), anyString());
    assertThat(history()).containsExactly("UNKNOWN", "ACCEPTED");
  }

  @Test
  void retryBudgetAndExponentialBackoffAreDurableAndDoNotResetCooldown() {
    doThrow(new MailSendException("transient"))
        .when(sender)
        .send(anyString(), anyString(), anyString(), anyString());
    var tokenCreatedAt =
        jdbc.queryForObject(
            "SELECT created_at FROM email_verification_tokens WHERE user_id = ?",
            java.sql.Timestamp.class,
            user.getId());
    for (int attempt = 1; attempt <= 4; attempt++) {
      worker.poll();
      assertThat(attempts()).isEqualTo(attempt);
      if (attempt < 4) {
        assertThat(status()).isEqualTo("RETRY_WAIT");
        double delay =
            jdbc.queryForObject(
                "SELECT EXTRACT(EPOCH FROM (next_retry_at - last_attempt_at)) FROM email_jobs WHERE id = ?",
                Double.class,
                jobId);
        assertThat(delay)
            .isBetween((double) (5 << (attempt - 1)), (double) (5 << (attempt - 1)) + 3);
        worker.poll(); // 아직 기한 전: SMTP 및 횟수 증가 없음
        assertThat(attempts()).isEqualTo(attempt);
        jdbc.update(
            "UPDATE email_jobs SET next_retry_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?",
            jobId);
      }
    }
    assertThat(status()).isEqualTo("FAILED");
    worker.poll();
    verify(sender, times(4)).send(anyString(), anyString(), anyString(), anyString());
    assertThat(history()).hasSize(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT created_at FROM email_verification_tokens WHERE user_id = ?",
                java.sql.Timestamp.class,
                user.getId()))
        .isEqualTo(tokenCreatedAt);
    assertThatThrownBy(() -> verification.resendVerification(user.getEmail()))
        .isInstanceOf(IllegalStateException.class);
    jdbc.update(
        "UPDATE email_verification_tokens SET created_at = CURRENT_TIMESTAMP - INTERVAL '4 minutes' WHERE user_id = ?",
        user.getId());
    verification.resendVerification(user.getEmail());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM email_jobs WHERE recipient = ?",
                Integer.class,
                user.getEmail()))
        .isEqualTo(2);
    assertThat(status()).isEqualTo("FAILED");
  }

  @Test
  void permanentFailureDoesNotRetry() {
    doThrow(new MailAuthenticationException("bad credentials"))
        .when(sender)
        .send(anyString(), anyString(), anyString(), anyString());
    worker.poll();
    worker.poll();
    assertThat(status()).isEqualTo("FAILED");
    assertThat(attempts()).isEqualTo(1);
    assertThat(history()).containsExactly("PERMANENT_FAILURE");
  }

  @Test
  void fourthAbandonedClaimNeverGetsFifthAttempt() {
    for (int attempt = 1; attempt <= 4; attempt++) {
      assertThat(store.claim().orElseThrow().attempt()).isEqualTo(attempt);
      expireLease();
    }
    worker.poll();
    assertThat(status()).isEqualTo("FAILED");
    assertThat(attempts()).isEqualTo(4);
    assertThat(history()).containsExactly("UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN");
    verifyNoInteractions(sender);
  }

  @Test
  void expiredAndSupersededTokensAreNotDelivered() {
    jdbc.update(
        "UPDATE email_verification_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE user_id = ?",
        user.getId());
    worker.poll();
    assertThat(status()).isEqualTo("FAILED");
    assertThat(attempts()).isZero();
    verifyNoInteractions(sender);
  }

  @Test
  void closingAndStartingApplicationContextsRecoversPendingAndStaleSending() {
    try (var first = restartContext()) {
      first.getBean(EmailJobStore.class).claim().orElseThrow();
    }
    expireLease();
    verification.sendVerification(user); // 다른 PENDING 작업도 재시작 후 처리
    try (var restarted = restartContext()) {
      restarted.publishEvent(
          new org.springframework.boot.context.event.ApplicationReadyEvent(
              new org.springframework.boot.SpringApplication(),
              new String[0],
              restarted,
              java.time.Duration.ZERO));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM email_jobs WHERE status = 'SUCCEEDED'", Integer.class))
        .isEqualTo(2);
    assertThat(attempts()).isEqualTo(2);
    verify(sender, times(2)).send(anyString(), anyString(), anyString(), anyString());
  }

  private org.springframework.context.annotation.AnnotationConfigApplicationContext
      restartContext() {
    var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
    context.register(RestartTransactions.class);
    context.registerBean(
        org.springframework.transaction.PlatformTransactionManager.class,
        () ->
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                jdbc.getDataSource()));
    context.registerBean(
        EmailJobStore.class, () -> new EmailJobStore(new JdbcTemplate(jdbc.getDataSource()), 300));
    context.registerBean(
        EmailJobWorker.class,
        () ->
            new EmailJobWorker(
                context.getBean(EmailJobStore.class),
                sender,
                cipher,
                new SmtpFailureClassifier(),
                Tracer.NOOP,
                ""));
    context.registerBean(
        EmailJobScheduler.class,
        () -> new EmailJobScheduler(context.getBean(EmailJobWorker.class)));
    context.refresh();
    return context;
  }

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  @org.springframework.transaction.annotation.EnableTransactionManagement
  static class RestartTransactions {}

  @Test
  void enqueueFailureRollsBackNewTokenAsWell() {
    int before =
        jdbc.queryForObject(
            "SELECT count(*) FROM email_verification_tokens WHERE user_id = ?",
            Integer.class,
            user.getId());
    jdbc.execute(
        "ALTER TABLE email_jobs ADD CONSTRAINT test_reject_pending CHECK (status <> 'PENDING') NOT VALID");
    try {
      assertThatThrownBy(() -> verification.sendVerification(user))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      jdbc.execute("ALTER TABLE email_jobs DROP CONSTRAINT test_reject_pending");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM email_verification_tokens WHERE user_id = ?",
                Integer.class,
                user.getId()))
        .isEqualTo(before);
  }

  @Test
  void manualResendInvalidatesOldPendingJobWithoutResettingItsAttempts() {
    jdbc.update(
        "UPDATE email_verification_tokens SET created_at = CURRENT_TIMESTAMP - INTERVAL '4 minutes' WHERE user_id = ?",
        user.getId());
    verification.resendVerification(user.getEmail());
    worker.poll();
    worker.poll();
    assertThat(status()).isEqualTo("FAILED");
    assertThat(attempts()).isZero();
    verify(sender, times(1)).send(anyString(), eq(user.getEmail()), anyString(), anyString());
  }

  private void expireLease() {
    jdbc.update(
        "UPDATE email_jobs SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?",
        jobId);
  }

  private String status() {
    return jdbc.queryForObject("SELECT status FROM email_jobs WHERE id = ?", String.class, jobId);
  }

  private int attempts() {
    return jdbc.queryForObject(
        "SELECT attempts FROM email_jobs WHERE id = ?", Integer.class, jobId);
  }

  private java.util.List<String> history() {
    return jdbc.queryForList(
        "SELECT outcome FROM email_job_attempts WHERE job_id = ? ORDER BY attempt",
        String.class,
        jobId);
  }
}
