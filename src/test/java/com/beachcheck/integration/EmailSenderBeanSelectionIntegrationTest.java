package com.beachcheck.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("EmailSender 조건부 빈 선택 통합 테스트")
class EmailSenderBeanSelectionIntegrationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  @DisplayName("스캐폴드: PR2 케이스 구현 전 테스트 러너 준비")
  void scaffold_contextRunnerInitialized() {
    assertThat(contextRunner).isNotNull();
  }
}
