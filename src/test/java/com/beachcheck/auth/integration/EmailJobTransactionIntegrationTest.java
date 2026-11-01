package com.beachcheck.auth.integration;

import static com.beachcheck.support.fixture.EmailVerificationTestFixtures.emailUser;
import static com.beachcheck.support.fixture.UniqueTestFixtures.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.auth.repository.EmailVerificationTokenRepository;
import com.beachcheck.auth.service.EmailVerificationService;
import com.beachcheck.support.base.IntegrationTest;
import com.beachcheck.user.domain.User;
import com.beachcheck.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@org.springframework.test.context.TestPropertySource(properties = "app.mail.enabled=true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailJobTransactionIntegrationTest extends IntegrationTest {

  @Autowired private EmailVerificationService emailVerificationService;
  @Autowired private UserRepository userRepository;
  @Autowired private EmailVerificationTokenRepository tokenRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @org.springframework.boot.test.mock.mockito.MockBean
  private com.beachcheck.auth.service.EmailSender sender;

  @Autowired private JdbcTemplate jdbc;

  private final List<UUID> createdUserIds = new ArrayList<>();
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUpTransactionTemplate() {
    transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @AfterEach
  void cleanUp() {
    transactionTemplate.executeWithoutResult(
        unused -> {
          if (!createdUserIds.isEmpty()) {
            for (UUID id : createdUserIds) {
              jdbc.update(
                  "DELETE FROM email_jobs WHERE recipient IN (SELECT email FROM users WHERE id = ?)",
                  id);
            }
            entityManager
                .createQuery("delete from EmailVerificationToken t where t.user.id in :userIds")
                .setParameter("userIds", createdUserIds)
                .executeUpdate();
            userRepository.deleteAllByIdInBatch(createdUserIds);
          }
        });
    createdUserIds.clear();
  }

  @Test
  @DisplayName("커밋 시 인증 토큰과 이메일 작업이 함께 저장된다")
  void sendVerification_commit_persistsTokenAndJob() {
    // given
    User user = saveUser();

    // when
    transactionTemplate.executeWithoutResult(
        unused -> {
          User managedUser = userRepository.findById(user.getId()).orElseThrow();
          emailVerificationService.sendVerification(managedUser);

          // BEFORE_COMMIT 리스너는 아직 실행되지 않았으며 SMTP도 호출하지 않는다.
          assertThat(jobCount(user)).isZero();
        });

    // then
    assertCommittedJob(user);
  }

  @Test
  @DisplayName("롤백 시 인증 토큰과 이메일 작업이 모두 남지 않는다")
  void sendVerification_rollback_persistsNeither() {
    // given
    User user = saveUser();

    // when
    transactionTemplate.executeWithoutResult(
        status -> {
          User managedUser = userRepository.findById(user.getId()).orElseThrow();
          emailVerificationService.sendVerification(managedUser);
          status.setRollbackOnly();
        });

    // then
    assertThat(jobCount(user)).isZero();
    assertThat(tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
  }

  private User saveUser() {
    User user = emailUser(uniqueEmail("after-commit"), false);

    User saved = userRepository.save(user);
    createdUserIds.add(saved.getId());
    return saved;
  }

  private void assertCommittedJob(User user) {
    assertThat(jobCount(user)).isEqualTo(1);
    assertThat(tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())).isPresent();
  }

  private int jobCount(User user) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM email_jobs WHERE recipient = ?", Integer.class, user.getEmail());
  }
}
