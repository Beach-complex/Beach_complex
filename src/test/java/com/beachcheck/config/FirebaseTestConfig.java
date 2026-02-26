package com.beachcheck.config;

import static org.mockito.Mockito.mock;

import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Firebase 테스트 설정
 *
 * <p>Why: 통합 테스트에서 FirebaseMessaging Mock 제공하여 의존성 문제 해결
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>@TestConfiguration으로 테스트 환경에만 적용
 *   <li>@Primary로 FirebaseConfig의 실제 빈보다 우선순위 높게 설정 (실제 FirebaseConfig는 test 환경에서 비활성화되어 있지만 명시적
 *       우선순위 지정)
 *   <li>ApiTest, IntegrationTest 베이스 클래스에 @Import로 자동 적용
 * </ul>
 *
 * <p>Context: test 환경에서는 app.firebase.enabled=false로 FirebaseMessaging 빈이 생성되지 않지만,
 * SchedulingConfig → OutboxPublisher → FirebaseMessaging 의존성 체인으로 인해 ApplicationContext 로딩이 실패함. 모든
 * 통합 테스트에 Mock을 제공하여 문제 해결.
 */
@TestConfiguration
public class FirebaseTestConfig {

  /**
   * FirebaseMessaging Mock 빈 생성
   *
   * <p>Why: test 환경에서 FirebaseMessaging 의존성을 만족시키기 위함
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>Mockito.mock()으로 Mock 객체 생성
   *   <li>@Primary로 실제 FirebaseConfig보다 우선순위 높게 설정 (test 환경에서 FirebaseConfig는 비활성화되어 있지만 명시적 우선순위
   *       지정)
   *   <li>개별 테스트에서 @MockBean으로 추가 설정 가능 (예: OutboxPublisherIntegrationTest)
   * </ul>
   */
  @Bean
  @Primary
  public FirebaseMessaging firebaseMessaging() {
    return mock(FirebaseMessaging.class);
  }
}
