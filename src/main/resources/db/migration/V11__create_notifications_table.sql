-- Why: 푸시 알림 발송 이력을 추적하고 실패 원인을 분석하기 위해 notifications 테이블을 생성한다.
-- Policy: type은 PEAK_AVOID/DATE_REMINDER/FAVORITE_UPDATE/WEATHER_ALERT만 허용하고,
--         status는 PENDING/SENT/FAILED만 허용한다.
--         사용자 삭제 시 해당 사용자의 알림 이력도 연쇄 삭제한다.
-- Contract(Input): user_id, type, title, message, status, created_at은 NULL 불가.
--                  sent_at, error_message, recipient_token은 NULL 가능.
-- Contract(Output): 유효하지 않은 type 또는 status 삽입은 애플리케이션 레벨에서 검증된다.

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_message VARCHAR(500),
    recipient_token VARCHAR(500),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Why: 사용자별 알림 조회, 상태별 필터링, 최신순 정렬 성능을 최적화하기 위해 인덱스를 생성한다.
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);

-- 문서화를 위한 테이블/컬럼 설명 추가
COMMENT ON TABLE notifications IS '푸시 알림 발송 이력';
COMMENT ON COLUMN notifications.user_id IS '알림을 수신한 사용자';
COMMENT ON COLUMN notifications.type IS '알림 유형: PEAK_AVOID(피크 타임 회피), DATE_REMINDER(날짜 알림), FAVORITE_UPDATE(찜 해변 정보 변경), WEATHER_ALERT(기상 특보)';
COMMENT ON COLUMN notifications.status IS '알림 상태: PENDING(발송 대기), SENT(발송 완료), FAILED(발송 실패)';
COMMENT ON COLUMN notifications.sent_at IS '알림이 성공적으로 발송된 시각';
COMMENT ON COLUMN notifications.error_message IS '발송 실패 시 에러 메시지';
COMMENT ON COLUMN notifications.recipient_token IS '알림을 받는 FCM 토큰 또는 이메일 주소 등 수신자 식별 정보';
