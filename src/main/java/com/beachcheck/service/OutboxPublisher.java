package com.beachcheck.service;

import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Why: Outbox 패턴의 폴링 루프 담당. 발송 로직은 OutboxEventDispatcher로 위임하여 self-invocation 없이 REQUIRES_NEW
 * 트랜잭션이 정상 적용되도록 함. Policy: readOnly 트랜잭션으로 이벤트 목록 조회 후 dispatcher에 위임
 */
@Service
public class OutboxPublisher {

  private final OutboxEventRepository outboxEventRepository;
  private final OutboxEventDispatcher outboxEventDispatcher;
  private final int batchSize;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      OutboxEventDispatcher outboxEventDispatcher,
      @Value("${app.outbox.polling.batch-size:10}") int batchSize) {
    this.outboxEventRepository = outboxEventRepository;
    this.outboxEventDispatcher = outboxEventDispatcher;
    this.batchSize = batchSize;
  }

  @Transactional(readOnly = true)
  public void processPendingOutboxEvents() {
    Instant now = Instant.now();
    List<OutboxEvent> pendingEvents =
        outboxEventRepository.findPendingEvents(now, PageRequest.of(0, batchSize));

    for (OutboxEvent event : pendingEvents) {
      outboxEventDispatcher.dispatch(event);
    }
  }
}
