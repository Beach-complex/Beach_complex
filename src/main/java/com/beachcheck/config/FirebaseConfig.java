package com.beachcheck.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

  private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

  /**
   * Firebase Admin SDK 초기화
   *
   * <p>Why: FCM(Firebase Cloud Messaging)을 통한 푸시 알림 발송을 위해 Firebase Admin SDK를 초기화한다.
   *
   * <p>Policy: 서비스 계정 키 파일(firebase-service-account.json)은 resources 디렉토리에 위치해야 하며, 파일이 없거나 잘못된 경우
   * 애플리케이션 시작이 실패한다.
   *
   * <p>Contract(Input): resources/firebase-service-account.json 파일 필요
   *
   * <p>Contract(Output): FirebaseApp 인스턴스 반환, 초기화 실패 시 IOException 발생
   *
   * @return FirebaseApp 인스턴스
   * @throws IOException 서비스 계정 키 파일을 읽을 수 없는 경우
   */
  @Bean
  public FirebaseApp firebaseApp() throws IOException {
    // Why: Firebase Admin SDK가 이미 초기화된 경우 재초기화를 방지
    if (!FirebaseApp.getApps().isEmpty()) {
      log.info("FirebaseApp이 이미 초기화되어 있습니다. 기존 인스턴스를 반환합니다.");
      return FirebaseApp.getInstance();
    }

    try (InputStream serviceAccount =
        new ClassPathResource("firebase-service-account.json").getInputStream()) {

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
              + "firebase-service-account.json가 src/main/resources/에 존재하는지 확인해주세요.",
          e);
      throw e;
    }
  }
}
