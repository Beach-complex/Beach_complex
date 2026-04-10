package com.beachcheck.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

public class AwsSigV4Interceptor implements CongestionInterceptor {

  /**
   * Why: Lambda Function URL의 AWS_IAM 인증 방식은 요청마다 SigV4 서명 헤더를 요구한다. RestClient는 기본적으로 SigV4를 지원하지
   * 않으므로 인터셉터로 서명 로직을 주입한다.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>모든 요청에 Aws4Signer로 서명하여 Authorization, X-Amz-Date 헤더를 추가한다.
   *   <li>서명 대상 서비스명은 "lambda"로 고정한다.
   * </ul>
   *
   * <p>Contract(Input): 서명할 HttpRequest, 요청 body byte[]
   *
   * <p>Contract(Output): 서명 헤더가 병합된 요청으로 실행한 ClientHttpResponse
   */
  private final AwsCredentialsProvider credentialsProvider;

  private final String region;
  private final Aws4Signer signer = Aws4Signer.create();

  public AwsSigV4Interceptor(AwsCredentialsProvider credentialsProvider, String region) {
    this.credentialsProvider = credentialsProvider;
    this.region = region;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    SdkHttpFullRequest sdkRequest = toSdkRequest(request, body);

    Aws4SignerParams signerParams =
        Aws4SignerParams.builder()
            .signingName("lambda")
            .signingRegion(Region.of(region))
            .awsCredentials(credentialsProvider.resolveCredentials())
            .build();

    SdkHttpFullRequest signedRequest = signer.sign(sdkRequest, signerParams);
    Map<String, List<String>> signedHeaders = signedRequest.headers();

    HttpRequest signedHttpRequest = new HttpRequestWrapper(request) { // super(request)와 동일한 효과
          @Override // 익명 클래스를 "원래 있는 클래스"로 풀어쓰면 암묵적으로 상속 발생!
          public HttpHeaders getHeaders() {
            HttpHeaders merged = new HttpHeaders(); // 업캐스팅
            merged.putAll( // 원본 헤더 먼저 넣기
                super.getHeaders()); // super.getHeaders()를 통해 부모가 감싸고 있던 원본 요청의 헤더를 모두 가져와 merged에
            // 복사
            signedHeaders.forEach(merged::put); // 서명 헤더 나중에 -> 같은 키면 덮어씀
            return merged;
          }
        };

    return execution.execute(signedHttpRequest, body);
  }

  private SdkHttpFullRequest toSdkRequest(HttpRequest request, byte[] body) {
    SdkHttpFullRequest.Builder builder =
        SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.fromValue(request.getMethod().name()))
            .uri(request.getURI())
            .contentStreamProvider(() -> new ByteArrayInputStream(body));

    request.getHeaders().forEach(builder::putHeader);
    return builder.build();
  }
}
