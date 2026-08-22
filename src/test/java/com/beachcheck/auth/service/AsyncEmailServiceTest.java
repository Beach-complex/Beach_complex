package com.beachcheck.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("비동기 이메일 서비스 단위 테스트")
class AsyncEmailServiceTest {

  private static final String FROM_EMAIL = "no-reply@test.com";
  private static final String USER_EMAIL = "user@test.com";
  private static final String VERIFICATION_LINK = "https://example.com/verify?token=abc";
  private static final String SUBJECT = "이메일 인증";
  private static final String EXPIRATION_TEXT = "30분";
  private static final int EXPIRATION_MINUTES = 30;

  @Mock private RetryingEmailDeliveryService emailDeliveryService;
  @Captor private ArgumentCaptor<String> fromCaptor;
  @Captor private ArgumentCaptor<String> toCaptor;
  @Captor private ArgumentCaptor<String> subjectCaptor;
  @Captor private ArgumentCaptor<String> bodyCaptor;

  @Test
  @DisplayName("인증 메일 payload를 조립해 전송 위임")
  void sendVerificationEmailAsync_success() {
    AsyncEmailService service = newService();

    service.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);

    then(emailDeliveryService)
        .should()
        .sendVerificationEmail(
            fromCaptor.capture(),
            toCaptor.capture(),
            subjectCaptor.capture(),
            bodyCaptor.capture());
    assertThat(fromCaptor.getValue()).isEqualTo(FROM_EMAIL);
    assertThat(toCaptor.getValue()).isEqualTo(USER_EMAIL);
    assertThat(subjectCaptor.getValue()).isEqualTo(SUBJECT);
    assertThat(bodyCaptor.getValue()).contains(VERIFICATION_LINK);
    assertThat(bodyCaptor.getValue()).contains(EXPIRATION_TEXT);
  }

  @Test
  @DisplayName("예상하지 못한 전송 예외는 호출자에게 그대로 전파")
  void sendVerificationEmailAsync_unexpectedFailure_propagates() {
    AsyncEmailService service = newService();
    doThrow(new IllegalStateException("delivery failed"))
        .when(emailDeliveryService)
        .sendVerificationEmail(eq(FROM_EMAIL), eq(USER_EMAIL), eq(SUBJECT), anyString());

    assertThatThrownBy(() -> service.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("delivery failed");
  }

  private AsyncEmailService newService() {
    return new AsyncEmailService(emailDeliveryService, Tracer.NOOP, FROM_EMAIL, EXPIRATION_MINUTES);
  }
}
