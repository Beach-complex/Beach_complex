package com.beachcheck.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NOOP 이메일 전송기 단위 테스트")
class NoopEmailSenderTest {

  @Test
  @DisplayName("send 호출 시 예외 없이 종료")
  void send_doesNotThrow() {
    NoopEmailSender sender = new NoopEmailSender();

    assertThatCode(() -> sender.send("from@test.com", "to@test.com", "subject", "body"))
        .doesNotThrowAnyException();
  }
}

