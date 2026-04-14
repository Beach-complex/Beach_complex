package com.beachcheck.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.core.exception.SdkClientException;

@SuppressWarnings("resource") // MockClientHttpResponse는 byte[] 래퍼로 close()가 no-op
@ExtendWith(MockitoExtension.class)
@DisplayName("AwsSigV4Interceptor 단위 테스트")
class AwsSigV4InterceptorTest {

  private static final String REGION = "us-east-1";
  private static final URI LAMBDA_URI =
      URI.create("https://test.lambda-url.us-east-1.on.aws/congestion/current?beach_id=haeundae");

  @Mock private AwsCredentialsProvider credentialsProvider;
  @Mock private ClientHttpRequestExecution execution;

  private AwsSigV4Interceptor interceptor;

  @BeforeEach
  void setUp() {
    given(credentialsProvider.resolveCredentials())
        .willReturn(AwsBasicCredentials.create("test-access-key", "test-secret-key"));
    interceptor = new AwsSigV4Interceptor(credentialsProvider, REGION);
  }

  @Nested
  @DisplayName("intercept - SigV4 서명 헤더 삽입")
  class InterceptTest {

    @Test
    @DisplayName("TC1 - 서명 후 실행된 요청에 Authorization, X-Amz-Date 헤더가 추가되고 scope가 맞는다")
    void shouldAddSigV4Headers() throws IOException {
      // Given
      MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, LAMBDA_URI);
      given(execution.execute(any(), any()))
          .willReturn(new MockClientHttpResponse(new byte[] {}, HttpStatus.OK));

      // When
      interceptor.intercept(request, new byte[] {}, execution);

      // Then
      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      then(execution).should().execute(captor.capture(), any());
      HttpRequest signedRequest = captor.getValue();
      assertThat(signedRequest.getHeaders()).containsKey(HttpHeaders.AUTHORIZATION);
      assertThat(signedRequest.getHeaders()).containsKey("X-Amz-Date");
      assertThat(signedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
          .contains("/" + REGION + "/lambda/aws4_request");
    }

    @Test
    @DisplayName("TC2 - 자격증명 조회 실패 시 SdkClientException이 전파된다")
    void shouldPropagateException_whenCredentialsResolveFails() {
      // Given
      given(credentialsProvider.resolveCredentials())
          .willThrow(SdkClientException.create("자격증명 없음"));

      MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, LAMBDA_URI);

      // When & Then
      assertThatThrownBy(() -> interceptor.intercept(request, new byte[] {}, execution))
          .isInstanceOf(SdkClientException.class);
    }

    @Test
    @DisplayName("TC3 - 세션 자격증명 사용 시 X-Amz-Security-Token 헤더가 추가된다")
    void shouldAddSecurityToken_whenSessionCredentialsUsed() throws IOException {
      // Given
      given(credentialsProvider.resolveCredentials())
          .willReturn(
              AwsSessionCredentials.create(
                  "test-access-key", "test-secret-key", "test-session-token"));
      interceptor = new AwsSigV4Interceptor(credentialsProvider, REGION);

      MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, LAMBDA_URI);
      given(execution.execute(any(), any()))
          .willReturn(new MockClientHttpResponse(new byte[] {}, HttpStatus.OK));

      // When
      interceptor.intercept(request, new byte[] {}, execution);

      // Then
      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      then(execution).should().execute(captor.capture(), any());
      HttpRequest signedRequest = captor.getValue();
      assertThat(signedRequest.getHeaders()).containsKey("X-Amz-Security-Token");
      assertThat(signedRequest.getHeaders().getFirst("X-Amz-Security-Token"))
          .isEqualTo("test-session-token");
    }
  }

  @Nested
  @DisplayName("shouldSkipTransportManagedHeader() - aws 서명에서 Content-Length·Transfer-Encoding 제외")
  class SkipTransportManagedHeaderTest {

    @Test
    @DisplayName("TC1-1 - GET 요청 헤더에 Content-Length가 있어도 SignedHeaders에 포함되지 않는다")
    void shouldNotSignContentLength_forGetRequest() throws IOException {
      // Given
      MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, LAMBDA_URI);
      request.getHeaders().setContentLength(1);
      given(execution.execute(any(), any()))
          .willReturn(new MockClientHttpResponse(new byte[] {}, HttpStatus.OK));

      // When
      interceptor.intercept(request, new byte[] {}, execution);

      // Then
      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      then(execution).should().execute(captor.capture(), any());
      HttpRequest signedRequest = captor.getValue();
      assertThat(signedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
          .doesNotContain("content-length");
    }

    @Test
    @DisplayName("TC1-2 - HEAD 요청 헤더에 Content-Length가 있어도 SignedHeaders에 포함되지 않는다")
    void shouldNotSignContentLength_forHeadRequest() throws IOException {
      // Given
      MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.HEAD, LAMBDA_URI);
      request.getHeaders().setContentLength(0);
      given(execution.execute(any(), any()))
          .willReturn(new MockClientHttpResponse(new byte[] {}, HttpStatus.OK));

      // When
      interceptor.intercept(request, new byte[] {}, execution);

      // Then
      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      then(execution).should().execute(captor.capture(), any());
      HttpRequest signedRequest = captor.getValue();
      assertThat(signedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
          .doesNotContain("content-length");
    }
  }

  // TODO: shouldAttachBodyForSigning() 검증
  //   body hash가 canonical request에 반영되는지(safe method or 빈 body → empty hash  non-safe/body 있음 → 실제
  // hash)는
  //   단위 테스트 경계에서 직접 검증하기 어렵고, 결국 Lambda Function URL에 실제 요청을 보내 AWS가 서명을 수락하는지로 검증해야 할 듯 (E2E).

}
