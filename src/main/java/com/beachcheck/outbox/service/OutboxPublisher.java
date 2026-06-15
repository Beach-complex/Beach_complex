package com.beachcheck.outbox.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.beachcheck.outbox.domain.OutboxEvent;
import com.beachcheck.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Why: Outbox 패턴의 폴링 루프 담당. 발송 로직은 OutboxEventDispatcher로 위임하여 self-invocation 없이 REQUIRES_NEW
 * 트랜잭션이 정상 적용되도록 함.
 *
 * <p>Policy: readOnly 트랜잭션으로 이벤트 목록 조회 후 dispatcher에 위임
 *
 * <p>TODO(설정 항목 증가 시): @ConfigurationProperties(prefix = "app.outbox.polling") + @Validated 도입 검토 -
 * 문자열 키 오타/경로 불일치를 컴파일 타임에 차단 - @Min 등으로 batchSize > 0 제약을 애플리케이션 시작 시점에 fail-fast 검증
 */
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxEventDispatcher outboxEventDispatcher;
  private final int batchSize;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      OutboxEventDispatcher outboxEventDispatcher,
      int batchSize) {
    this.outboxEventRepository = outboxEventRepository;
    this.outboxEventDispatcher = outboxEventDispatcher;
    this.batchSize = batchSize;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void processPendingOutboxEvents() {
    Instant now = Instant.now();
    List<OutboxEvent> pendingEvents =
        outboxEventRepository.findPendingEvents(now, PageRequest.of(0, batchSize));

    log.info("Outbox 폴링 대상 이벤트 조회 완료", kv("outboxEventCount", pendingEvents.size()));

    for (OutboxEvent event : pendingEvents) {
      outboxEventDispatcher.dispatch(event);
    }
  }
}
