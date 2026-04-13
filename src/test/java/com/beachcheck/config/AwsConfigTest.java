package com.beachcheck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.beachcheck.client.CongestionInterceptor;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

@SuppressWarnings("resource")
@ExtendWith(MockitoExtension.class)
@DisplayName("AwsConfig 단위 테스트")
class AwsConfigTest {

  private static final String REGION = "us-east-1";
  private static final URI LAMBDA_URI =
      URI.create("https://test.lambda-url.us-east-1.on.aws/congestion/current?beach_id=haeundae");

  @Mock private AwsCredentialsProvider credentialsProvider;
  @Mock private ClientHttpRequestExecution execution;

  private final AwsConfig awsConfig = new AwsConfig();

  @Test
  @DisplayName("TC1 - sigv4-enabled=false 시 NoOp 인터셉터가 등록되어 Authorization 헤더가 추가되지 않는다")
  void shouldReturnNoOpInterceptor_whenSigV4Disabled() throws IOException {
    // Given
    CongestionInterceptor interceptor =
        awsConfig.congestionInterceptor(credentialsProvider, REGION, false);
    MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, LAMBDA_URI);
    given(execution.execute(any(), any()))
        .willReturn(new MockClientHttpResponse(new byte[] {}, HttpStatus.OK));

    // When
    interceptor.intercept(request, new byte[] {}, execution);

    // Then
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    then(execution).should().execute(captor.capture(), any());
    assertThat(captor.getValue().getHeaders()).doesNotContainKey("Authorization");
  }

  @Test
  @DisplayName("TC2 - sigv4-enabled=true 시 SigV4 인터셉터가 등록되어 Authorization 헤더가 추가된다")
  void shouldReturnSigV4Interceptor_whenSigV4Enabled() throws IOException {
    // Given
    given(credentialsProvider.resolveCredentials())
        .willReturn(AwsBasicCredentials.create("test-access-key", "test-secret-key"));
    CongestionInterceptor interceptor =
        awsConfig.congestionInterceptor(credentialsProvider, REGION, true);
    MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, LAMBDA_URI);
    given(execution.execute(any(), any()))
        .willReturn(new MockClientHttpResponse(new byte[] {}, HttpStatus.OK));

    // When
    interceptor.intercept(request, new byte[] {}, execution);

    // Then
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    then(execution).should().execute(captor.capture(), any());
    assertThat(captor.getValue().getHeaders()).containsKey("Authorization");
  }
}
