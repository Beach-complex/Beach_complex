-- Why: Firebase Cloud Messaging을 통한 푸시 알림 기능을 지원하기 위해 users 테이블에 알림 관련 필드를 추가한다.
-- Policy: notification_enabled는 opt-out 방식으로 기본값 true를 가지며,
--         fcm_token은 NULL 허용(로그인 전 또는 권한 거부 시).
-- Contract(Input): notification_enabled는 NULL 불가 (DEFAULT TRUE),
--                  fcm_token은 NULL 가능 (최대 500자).
-- Contract(Output): 기존 사용자는 notification_enabled = TRUE로 자동 설정된다.

ALTER TABLE users
    ADD COLUMN fcm_token VARCHAR(500),
    ADD COLUMN notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 문서화를 위한 컬럼 설명 추가
COMMENT ON COLUMN users.fcm_token IS '푸시 알림용 Firebase Cloud Messaging 토큰';
COMMENT ON COLUMN users.notification_enabled IS '알림 수신 동의 여부 (opt-out 방식)';
