package com.beachcheck.config;

import com.beachcheck.client.AwsSigV4Interceptor;
import com.beachcheck.client.CongestionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

@Configuration
public class AwsConfig {

  /**
   * Why: SigV4 서명에 필요한 AWS 자격증명을 주입 가능한 Bean으로 관리하기 위해.
   *
   * <p>Policy: DefaultCredentialsProvider는 아래 순서로 자격증명을 조회하며, 먼저 찾은 곳에서 멈춘다.
   *
   * <ul>
   *   <li>환경변수: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY (로컬 개발, CI)
   *   <li>Java 시스템 프로퍼티: aws.accessKeyId, aws.secretAccessKey
   *   <li>~/.aws/credentials: AWS CLI로 설정한 로컬 프로파일
   *   <li>EC2/ECS 인스턴스 프로파일: 서버에 붙은 IAM Role (운영 환경, 키 없이 자동 인증)
   * </ul>
   *
   * <p>Contract(Output): AwsCredentialsProvider 구현체 반환. 자격증명 없으면 호출 시점에 SdkClientException 발생.
   */
  @Bean
  public AwsCredentialsProvider awsCredentialsProvider() {
    return DefaultCredentialsProvider.create();
  }

  /**
   * Why: CongestionClient가 Lambda Function URL을 호출할 때 SigV4 서명 여부를 환경별로 분리하기 위해.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>CONGESTION_SIGV4_ENABLED=true(기본값): AwsSigV4Interceptor를 등록한다. 운영 및 로컬 통합 테스트에 사용.
   *   <li>CONGESTION_SIGV4_ENABLED=false: NoOp 인터셉터를 등록한다. 자격증명 없는 로컬 개발 환경에서 다른 기능 테스트 시 사용.
   * </ul>
   *
   * <p>Contract(Output): CongestionInterceptor 구현체 반환.
   */
  @Bean
  public CongestionInterceptor congestionInterceptor(
      AwsCredentialsProvider credentialsProvider,
      @Value("${app.aws.region}") String region,
      @Value("${app.congestion.sigv4-enabled:true}") boolean sigv4Enabled) {
    if (!sigv4Enabled) {
      return (request, body, execution) -> execution.execute(request, body);
    }
    return new AwsSigV4Interceptor(credentialsProvider, region);
  }
}
