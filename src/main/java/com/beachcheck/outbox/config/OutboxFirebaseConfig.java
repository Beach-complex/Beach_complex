package com.beachcheck.outbox.config;

import com.beachcheck.notification.repository.NotificationRepository;
import com.beachcheck.outbox.repository.OutboxEventRepository;
import com.beachcheck.outbox.service.OutboxEventDispatcher;
import com.beachcheck.outbox.service.OutboxPublisher;
import com.beachcheck.outbox.tracing.OutboxTracing;
import com.google.firebase.messaging.FirebaseMessaging;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Why: Firebase가 활성화됐을 때만 Outbox 전송 관련 빈을 한곳에서 조건부 등록하기 위해.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>app.firebase.enabled=false면 dispatcher/publisher를 등록하지 않는다.
 *   <li>조건부 생성 정책은 서비스 클래스가 아니라 configuration 레이어에서 관리한다.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "app.firebase",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class OutboxFirebaseConfig {

  @Bean
  public OutboxEventDispatcher outboxEventDispatcher(
      OutboxEventRepository outboxEventRepository,
      NotificationRepository notificationRepository,
      FirebaseMessaging firebaseMessaging,
      Tracer tracer) {
    return new OutboxEventDispatcher(
        outboxEventRepository, notificationRepository, firebaseMessaging, tracer);
  }

  @Bean
  public OutboxPublisher outboxPublisher(
      OutboxEventRepository outboxEventRepository,
      OutboxEventDispatcher outboxEventDispatcher,
      OutboxTracing outboxTracing,
      @Value("${app.outbox.polling.batch-size:10}") int batchSize) {
    return new OutboxPublisher(
        outboxEventRepository, outboxEventDispatcher, outboxTracing, batchSize);
  }
}
