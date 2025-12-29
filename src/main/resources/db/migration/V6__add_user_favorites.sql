-- V6: 사용자별 찜 목록 테이블 추가

-- 1) user_favorites 테이블 생성
CREATE TABLE IF NOT EXISTS user_favorites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    beach_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- 외래키 제약조건
    CONSTRAINT fk_user_favorites_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_favorites_beach
        FOREIGN KEY (beach_id) REFERENCES beaches(id) ON DELETE CASCADE,

    -- 중복 방지: 한 사용자가 같은 해수욕장을 여러 번 찜할 수 없음
    CONSTRAINT uk_user_beach
        UNIQUE (user_id, beach_id)
);

-- 2) 성능 최적화 인덱스 생성
-- 사용자별 찜 목록 조회 시 사용 (SELECT * FROM user_favorites WHERE user_id = ?)
CREATE INDEX IF NOT EXISTS idx_user_favorites_user_id
    ON user_favorites(user_id);

-- 해수욕장별 찜 수 조회 시 사용 (SELECT COUNT(*) FROM user_favorites WHERE beach_id = ?)
CREATE INDEX IF NOT EXISTS idx_user_favorites_beach_id
    ON user_favorites(beach_id);

-- 3) 기존 beaches.is_favorite 컬럼 제거
-- 더 이상 전역 플래그가 아닌 사용자별 찜 목록으로 관리
ALTER TABLE beaches DROP COLUMN IF EXISTS is_favorite;

-- 4) 주석: 마이그레이션 완료
COMMENT ON TABLE user_favorites IS '사용자별 찜 목록 (개인화)';