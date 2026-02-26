package com.beachcheck.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import com.beachcheck.base.IntegrationTest;
import com.beachcheck.domain.User;
import com.beachcheck.repository.EmailVerificationTokenRepository;
import com.beachcheck.repository.UserRepository;
import com.beachcheck.service.AsyncEmailService;
import com.beachcheck.service.EmailVerificationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailAfterCommitIntegrationTest extends IntegrationTest {

  @Autowired private EmailVerificationService emailVerificationService;
  @Autowired private UserRepository userRepository;
  @Autowired private EmailVerificationTokenRepository tokenRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockBean private AsyncEmailService asyncEmailService;

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
  @DisplayName("커밋 후 이메일 비동기 전송이 호출된다")
  void sendVerification_afterCommit_triggersListener() {
    // given
    User user = saveUser();

    // when
    transactionTemplate.executeWithoutResult(
        unused -> {
          User managedUser = userRepository.findById(user.getId()).orElseThrow();
          emailVerificationService.sendVerification(managedUser);

          // 트랜잭션 내부에서는 AFTER_COMMIT 리스너가 아직 동작하지 않아야 한다.
          then(asyncEmailService).shouldHaveNoInteractions();
        });

    ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
    then(asyncEmailService)
        .should()
        .sendVerificationEmailAsync(emailCaptor.capture(), linkCaptor.capture());

    // then
    assertThat(emailCaptor.getValue()).isEqualTo(user.getEmail());
    assertThat(linkCaptor.getValue()).contains("?token=");
    assertThat(tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())).isPresent();
  }

  @Test
  @DisplayName("롤백 시 이메일 비동기 전송이 호출되지 않는다")
  void sendVerification_rollback_doesNotTriggerListener() {
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
    then(asyncEmailService).shouldHaveNoInteractions();
    assertThat(tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
  }

  private User saveUser() {
    User user = new User();
    user.setEmail("after-commit-" + UUID.randomUUID() + "@test.com");
    user.setName("after-commit-tester");
    user.setPassword("encoded-password");
    user.setEnabled(false);
    user.setRole(User.Role.USER);
    user.setAuthProvider(User.AuthProvider.EMAIL);

    User saved = userRepository.save(user);
    createdUserIds.add(saved.getId());
    return saved;
  }
}
