package com.beachcheck.service;

import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("이메일 이벤트 리스너 단위 테스트")
class EmailEventListenerTest {

  private static final String USER_EMAIL = "user@test.com";
  private static final String VERIFICATION_LINK = "https://example.com/verify?token=abc";

  @Mock private AsyncEmailService asyncEmailService;

  @Test
  @DisplayName("이벤트 payload를 비동기 서비스로 전달")
  void handleEmailVerificationEvent_forwardsEventPayload() {
    EmailEventListener listener = new EmailEventListener(asyncEmailService);
    EmailVerificationEvent event = new EmailVerificationEvent(USER_EMAIL, VERIFICATION_LINK);

    listener.handleEmailVerificationEvent(event);

    then(asyncEmailService).should().sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);
  }
}
