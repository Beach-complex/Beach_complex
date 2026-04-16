package com.beachcheck.config;

import com.beachcheck.client.AwsSigV4Interceptor;
import com.beachcheck.client.CongestionInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

@Configuration
public class AwsConfig {
  private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);

  /**
   * Why: SigV4 서명에 필요한 AWS 자격증명을 주입 가능한 Bean으로 관리하기 위해.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>app.aws.profile이 지정되면 ProfileCredentialsProvider로 해당 프로파일을 읽는다. sso + ssooidc 모듈이 포함되어
   *       있으므로 SSO 프로파일도 정상 로드된다.
   *   <li>프로파일이 없으면 DefaultCredentialsProvider로 fallback한다. (환경변수 → 시스템 프로퍼티 → ~/.aws/credentials →
   *       EC2 IAM Role 순으로 탐색)
   * </ul>
   *
   * <p>Contract(Output): AwsCredentialsProvider 구현체 반환. 자격증명 없으면 호출 시점에 SdkClientException 발생.
   */
  @Bean
  public AwsCredentialsProvider awsCredentialsProvider(
      @Value("${app.aws.profile:}") String profile) {
    if (StringUtils.hasText(profile)) {
      log.info("AWS ProfileCredentialsProvider 사용 (profile={})", profile);
      return ProfileCredentialsProvider.create(profile);
    }

    log.info("AWS 자격증명: DefaultCredentialsProvider 사용");
    return DefaultCredentialsProvider.builder().build();
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
