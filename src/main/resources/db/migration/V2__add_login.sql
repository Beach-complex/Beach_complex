-- users 테이블 생성
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR UNIQUE NOT NULL,
    password VARCHAR NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE,
    timezone VARCHAR,
    auth_provider VARCHAR(20),
    CONSTRAINT chk_role CHECK (role IN ('USER', 'ADMIN'))
    );

CREATE INDEX idx_users_email ON users(email);  -- 로그인 쿼리 최적화
CREATE INDEX idx_users_role ON users(role);    -- 권한별 조회 최적화

-- refresh_tokens 테이블 생성
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    revoked BOOLEAN NOT NULL DEFAULT FALSE
    );

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);

-- 기본 관리자 계정 추가 (비밀번호: Admin123!)
-- 실제 운영 환경에서는 초기 설정 후 비밀번호를 반드시 변경해야 함
INSERT INTO users (email, password, name, role, auth_provider) VALUES
    ('admin@beachcomplex.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'System Admin', 'ADMIN', 'EMAIL');


