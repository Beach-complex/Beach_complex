package com.beachcheck.config;

import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import com.beachcheck.service.OutboxEventDispatcher;
import com.beachcheck.service.OutboxPublisher;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Why: FirebaseMessaging 빈이 있을 때만 Outbox 전송 관련 빈을 한곳에서 조건부 등록하기 위해.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>FirebaseMessaging 빈이 없으면 dispatcher/publisher를 등록하지 않는다.
 *   <li>조건부 생성 정책은 서비스 클래스가 아니라 configuration 레이어에서 관리한다.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(FirebaseMessaging.class)
public class OutboxFirebaseConfig {

  @Bean
  public OutboxEventDispatcher outboxEventDispatcher(
      OutboxEventRepository outboxEventRepository,
      NotificationRepository notificationRepository,
      FirebaseMessaging firebaseMessaging) {
    return new OutboxEventDispatcher(
        outboxEventRepository, notificationRepository, firebaseMessaging);
  }

  @Bean
  public OutboxPublisher outboxPublisher(
      OutboxEventRepository outboxEventRepository,
      OutboxEventDispatcher outboxEventDispatcher,
      @Value("${app.outbox.polling.batch-size:10}") int batchSize) {
    return new OutboxPublisher(outboxEventRepository, outboxEventDispatcher, batchSize);
  }
}
