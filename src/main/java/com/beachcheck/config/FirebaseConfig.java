package com.beachcheck.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(
    prefix = "app.firebase",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class FirebaseConfig {

  private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
  private static final String CLASSPATH_CREDENTIALS = "firebase-service-account.json";

  @Value("${app.firebase.credentials-path:}")
  private String credentialsPath;

  @Value("${app.firebase.credentials-json-base64:}")
  private String credentialsJsonBase64;

  @Bean
  public FirebaseApp firebaseApp() throws IOException {
    if (!FirebaseApp.getApps().isEmpty()) {
      log.info("FirebaseApp이 이미 초기화되어 있습니다. 기존 인스턴스를 반환합니다.");
      return FirebaseApp.getInstance();
    }

    try (InputStream serviceAccount = openCredentialsStream()) {
      FirebaseOptions options =
          FirebaseOptions.builder()
              .setCredentials(GoogleCredentials.fromStream(serviceAccount))
              .build();

      FirebaseApp app = FirebaseApp.initializeApp(options);
      log.info("FirebaseApp이 성공적으로 초기화되었습니다.");
      return app;
    } catch (IOException e) {
      log.error(
          "FirebaseApp을 초기화하는 데 실패했습니다. "
              + "APP_FIREBASE_CREDENTIALS_JSON_BASE64, APP_FIREBASE_CREDENTIALS_PATH, "
              + "또는 classpath의 firebase-service-account.json 구성을 확인해주세요.",
          e);
      throw e;
    }
  }

  @Bean
  public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }

  private InputStream openCredentialsStream() throws IOException {
    if (StringUtils.hasText(credentialsJsonBase64)) {
      log.info("Base64 환경변수로 Firebase 서비스 계정 키를 로드합니다.");
      byte[] decoded = Base64.getDecoder().decode(credentialsJsonBase64);
      return new ByteArrayInputStream(decoded);
    }

    if (StringUtils.hasText(credentialsPath)) {
      Path path = Path.of(credentialsPath);
      log.info("외부 파일 경로에서 Firebase 서비스 계정 키를 로드합니다. path={}", path);
      return Files.newInputStream(path);
    }

    ClassPathResource resource = new ClassPathResource(CLASSPATH_CREDENTIALS);
    if (resource.exists()) {
      log.info("Classpath 리소스에서 Firebase 서비스 계정 키를 로드합니다.");
      return resource.getInputStream();
    }

    throw new IOException(
        "Firebase 서비스 계정 키를 찾을 수 없습니다. "
            + "APP_FIREBASE_CREDENTIALS_JSON_BASE64, APP_FIREBASE_CREDENTIALS_PATH "
            + "또는 classpath 리소스를 설정해주세요.");
  }
}
