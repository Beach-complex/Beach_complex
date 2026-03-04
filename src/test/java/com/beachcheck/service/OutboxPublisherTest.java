package com.beachcheck.service;

import static com.beachcheck.domain.OutboxEvent.OutboxEventType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

/**
 * Why: OutboxPublisher의 폴링-위임 루프만 검증. 발송 로직은 OutboxEventDispatcherTest에서 검증.
 *
 * <p>Policy: BDDMockito 스타일, Given-When-Then 구조
 *
 * <p>Contract(Input): Mock 객체 (OutboxEventRepository, OutboxEventDispatcher)
 *
 * <p>Contract(Output): dispatcher.dispatch() 호출 여부 및 횟수
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  @Mock private OutboxEventRepository outboxEventRepository;

  @Mock private OutboxEventDispatcher outboxEventDispatcher;

  private OutboxPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new OutboxPublisher(outboxEventRepository, outboxEventDispatcher, 10);
  }

  @Nested
  @DisplayName("processPendingOutboxEvents()")
  class ProcessPendingOutboxEventsTests {

    @Test
    @DisplayName("TC1 - PENDING 이벤트를 폴링하여 dispatcher에 위임")
    void shouldDelegateToDispatcher_whenPendingEventExists() {
      // Given
      OutboxEvent event = createPendingEvent(UUID.randomUUID());

      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of(event));

      // When
      publisher.processPendingOutboxEvents();

      // Then
      then(outboxEventDispatcher).should().dispatch(event);
    }

    @Test
    @DisplayName("TC2 - PENDING 이벤트가 없으면 dispatcher 호출 안 함")
    void shouldNotCallDispatcher_whenNoPendingEvents() {
      // Given
      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of());

      // When
      publisher.processPendingOutboxEvents();

      // Then
      then(outboxEventDispatcher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("TC3 - 배치 사이즈 10개 제한으로 폴링")
    void shouldQueryWithBatchSizeLimit() {
      // Given
      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of());

      // When
      publisher.processPendingOutboxEvents();

      // Then
      then(outboxEventRepository)
          .should()
          .findPendingEvents(any(Instant.class), eq(PageRequest.of(0, 10)));
    }
  }

  private OutboxEvent createPendingEvent(UUID notificationId) {
    return OutboxEvent.createPending(notificationId, OutboxEventType.PUSH_NOTIFICATION, null);
  }
}
