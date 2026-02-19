-- Why: Outbox 패턴을 통해 푸시 알림 이벤트의 트랜잭션 원자성을 보장하고,
--      앱 크래시 시 이벤트 유실을 방지하기 위해 outbox_events 테이블을 생성한다.
-- Policy: status는 PENDING/SENT/FAILED_RETRIABLE/FAILED_PERMANENT만 허용하고,
--         event_type은 PUSH_NOTIFICATION만 허용한다.
--         알림 삭제 시 해당 알림의 Outbox 이벤트도 연쇄 삭제한다.
-- Contract(Input): notification_id, status, event_type, retry_count, created_at은 NULL 불가.
--                  payload, next_retry_at, processed_at은 NULL 가능.
-- Contract(Output): PENDING/FAILED_RETRIABLE 상태이고 next_retry_at <= now인 이벤트를 폴링한다.

CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    notification_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_outbox_events_notification FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
);

-- Why: 폴링 워커가 처리 대상 이벤트를 빠르게 조회하기 위해 복합 인덱스를 생성한다.
--      (status, next_retry_at) 조합은 WHERE e.status IN (...) AND e.nextRetryAt <= :now 쿼리를 최적화한다.
CREATE INDEX idx_outbox_events_status_next_retry ON outbox_events(status, next_retry_at);

-- Why: 알림과 Outbox 이벤트를 조인할 때 성능을 최적화하기 위해 인덱스를 생성한다.
CREATE INDEX idx_outbox_events_notification_id ON outbox_events(notification_id);

-- Why: 폴링 시 createdAt 오름차순 정렬 성능을 최적화하기 위해 인덱스를 생성한다.
CREATE INDEX idx_outbox_events_created_at ON outbox_events(created_at ASC);

-- 문서화를 위한 테이블/컬럼 설명 추가
COMMENT ON TABLE outbox_events IS 'Outbox 패턴 기반 푸시 알림 이벤트 큐 (at-least-once 보장)';
COMMENT ON COLUMN outbox_events.notification_id IS '발송할 알림 ID (notifications 테이블 참조)';
COMMENT ON COLUMN outbox_events.status IS '이벤트 상태: PENDING(처리 대기), SENT(전송 완료), FAILED_RETRIABLE(일시 실패/재시도 대상), FAILED_PERMANENT(영구 실패/재시도 제외)';
COMMENT ON COLUMN outbox_events.event_type IS '이벤트 유형: PUSH_NOTIFICATION (푸시 알림 발송)';
COMMENT ON COLUMN outbox_events.payload IS 'FCM 발송 정보 (JSON 형태)';
COMMENT ON COLUMN outbox_events.retry_count IS '재시도 횟수 (exponential backoff 계산용)';
COMMENT ON COLUMN outbox_events.next_retry_at IS '다음 재시도 시각 (폴링 워커가 이 시각 이후에 처리)';
COMMENT ON COLUMN outbox_events.processed_at IS '처리 완료 시각 (SENT 또는 FAILED_PERMANENT로 전이 시 기록)';
COMMENT ON COLUMN outbox_events.created_at IS '이벤트 생성 시각';
