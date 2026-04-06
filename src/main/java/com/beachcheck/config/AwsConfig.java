package com.beachcheck.config;

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
   * </ul>
   *
   * <p>Contract(Output): AwsCredentialsProvider 구현체 반환. 자격증명 없으면 호출 시점에 SdkClientException 발생.
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
