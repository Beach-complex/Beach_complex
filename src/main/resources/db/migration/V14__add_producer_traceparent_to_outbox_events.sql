-- Why: Outbox 생산 시점의 W3C trace context를 소비 처리 trace와 Span Link로 연결하기 위해 저장한다.
-- Policy: 기존 row와 active span이 없는 요청은 NULL을 허용하고 형식 검증은 애플리케이션에서 수행한다.
-- Contract(Input): outbox_events 테이블이 존재해야 한다.
-- Contract(Output): producer_traceparent를 최대 64자로 저장할 수 있다.
ALTER TABLE outbox_events ADD COLUMN producer_traceparent VARCHAR(64);
COMMENT ON COLUMN outbox_events.producer_traceparent IS 'Outbox 적재 시점 producer span의 W3C traceparent; legacy 또는 active span 없음은 NULL';
