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
import software.amazon.awssdk.auth.credentials.ProcessCredentialsProvider;

@Configuration
public class AwsConfig {
  private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);

  /**
   * Why: SigV4 서명에 필요한 AWS 자격증명을 주입 가능한 Bean으로 관리하기 위해.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>로컬에서 AWS_PROFILE이 지정되면 AWS CLI의 export-credentials 결과를 그대로 사용한다.
   *   <li>프로파일이 없으면 DefaultCredentialsProvider로 fallback한다.
   *       <p>Policy: DefaultCredentialsProvider는 아래 순서로 자격증명을 조회하며, 먼저 찾은 곳에서 멈춘다.
   *       <ul>
   *         <li>환경변수: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY (로컬 개발, CI)
   *         <li>Java 시스템 프로퍼티: aws.accessKeyId, aws.secretAccessKey
   *         <li>~/.aws/credentials: AWS CLI로 설정한 로컬 프로파일
   *         <li>EC2/ECS 인스턴스 프로파일: 서버에 붙은 IAM Role (운영 환경, 키 없이 자동 인증)
   *       </ul>
   *       <p>Contract(Output): AwsCredentialsProvider 구현체 반환. 자격증명 없으면 호출 시점에 SdkClientException
   *       발생.
   */
  @Bean
  public AwsCredentialsProvider awsCredentialsProvider(
      @Value("${app.aws.profile:}") String profile,
      @Value("${app.aws.cli-command:aws}") String awsCliCommand) {
    if (StringUtils.hasText(profile)) {
      String command =
          String.format(
              "%s configure export-credentials --profile %s --format process",
              quoteArg(awsCliCommand), quoteArg(profile));
      log.info("AWS CLI export-credentials Provider 사용 (profile={})", profile);
      return ProcessCredentialsProvider.builder().command(command).build();
    }

    log.info("AWS 자격증명: DefaultCredentialsProvider 사용");
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

  /**
   * Why: ProcessCredentialsProvider에 넘기는 CLI 커맨드 문자열은 셸이 공백으로 인자를 분리하므로, 프로파일명이나 커맨드 경로에 공백·탭이 포함되면
   * 인자가 잘못 쪼개지기 때문.
   *
   * <p>Policy: 공백 또는 탭이 포함된 인자는 큰따옴표로 감싸고, 내부에 이미 큰따옴표가 있으면 백슬래시로 이스케이프한다. 공백이 없으면 그대로 반환한다.
   *
   * <p>Contract(Input): null·빈 문자열이면 그대로 반환(호출부에서 별도 처리).
   *
   * <p>Contract(Output): 셸 인자로 안전하게 전달 가능한 문자열.
   */
  private String quoteArg(String value) {
    if (!StringUtils.hasText(value)) {
      return value;
    }
    if (value.contains(" ") || value.contains("\t")) {
      return "\"" + value.replace("\"", "\\\"") + "\"";
    }
    return value;
  }
}
