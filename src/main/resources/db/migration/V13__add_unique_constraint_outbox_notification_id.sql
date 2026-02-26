-- Why: 하나의 알림에 대해 중복 Outbox 이벤트 생성을 방지하여 멱등성을 보장한다.
--      애플리케이션 로직 오류로 같은 notification_id를 가진 이벤트가 재생성되더라도
--      DB 레벨에서 차단하여 중복 푸시 발송을 방지한다.
-- Policy: notification_id는 outbox_events 테이블 내에서 유일해야 한다.
--         하나의 알림(Notification)은 하나의 Outbox 이벤트만 가질 수 있다.
-- Contract(Input): 기존 데이터에 중복 notification_id가 없어야 마이그레이션이 성공한다.
-- Contract(Output): 동일한 notification_id로 두 번째 INSERT 시도 시 UNIQUE 제약 위반 예외가 발생한다.

ALTER TABLE outbox_events
ADD CONSTRAINT uq_outbox_events_notification_id UNIQUE (notification_id);

COMMENT ON CONSTRAINT uq_outbox_events_notification_id ON outbox_events
IS '알림 ID 유일성 제약: 하나의 알림에 대해 하나의 Outbox 이벤트만 존재 (멱등성 보장)';