package com.beachcheck.auth.integration;

import static com.beachcheck.support.fixture.EmailVerificationTestFixtures.emailUser;
import static com.beachcheck.support.fixture.UniqueTestFixtures.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.auth.service.EmailJobScheduler;
import com.beachcheck.auth.service.EmailJobWorker;
import com.beachcheck.auth.service.EmailVerificationService;
import com.beachcheck.support.base.IntegrationTest;
import com.beachcheck.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@TestPropertySource(
    properties = {
      "app.mail.enabled=false",
      "app.email.jobs.polling-enabled=true",
      "app.email.jobs.payload-key="
    })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailJobDisabledIntegrationTest extends IntegrationTest {
  @Autowired private ApplicationContext context;
  @Autowired private EmailVerificationService verification;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void disabledMailStartsWithoutKeyAndLeavesExistingJobsUntouched() {
    var user = users.save(emailUser(uniqueEmail("mail-disabled"), false));
    UUID existingJob = UUID.randomUUID();
    try {
      jdbc.update(
          "INSERT INTO email_jobs(id, recipient, payload, token_hash) VALUES (?, ?, ?, ?)",
          existingJob,
          user.getEmail(),
          "retained-ciphertext",
          "unused-hash");
      verification.sendVerification(user);
      assertThat(context.getBeansOfType(EmailJobScheduler.class)).isEmpty();
      assertThat(context.getBeansOfType(EmailJobWorker.class)).isEmpty();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM email_jobs WHERE recipient = ?",
                  Integer.class,
                  user.getEmail()))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM email_jobs WHERE id = ?", String.class, existingJob))
          .isEqualTo("PENDING");
      assertThat(
              jdbc.queryForObject(
                  "SELECT payload FROM email_jobs WHERE id = ?", String.class, existingJob))
          .isEqualTo("retained-ciphertext");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM email_verification_tokens WHERE user_id = ?",
                  Integer.class,
                  user.getId()))
          .isEqualTo(1);
    } finally {
      jdbc.update("DELETE FROM email_jobs WHERE id = ?", existingJob);
      users.deleteById(user.getId());
    }
  }
}
