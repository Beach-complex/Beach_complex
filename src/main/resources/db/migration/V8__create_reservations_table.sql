-- Why: 사용자-해변-시간 중복 예약을 차단하려고 유니크 제약을 둔다.
-- Policy: status는 CONFIRMED/REJECTED만 허용하고 상위 엔티티 삭제 시 예약은 연쇄 삭제한다.
-- Contract(Input): user_id, beach_id, reserved_at, status는 NULL 불가.
-- Contract(Output): 중복 또는 status 위반 삽입은 실패한다.
CREATE TABLE IF NOT EXISTS reservations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    beach_id UUID NOT NULL REFERENCES beaches(id) ON DELETE CASCADE,
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_id VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_reservation_status
        CHECK (status IN ('CONFIRMED', 'REJECTED')),
    CONSTRAINT uk_reservation_user_beach_time
        UNIQUE (user_id, beach_id, reserved_at)
);

CREATE INDEX IF NOT EXISTS idx_reservations_user_id
    ON reservations(user_id);

CREATE INDEX IF NOT EXISTS idx_reservations_beach_id
    ON reservations(beach_id);

CREATE INDEX IF NOT EXISTS idx_reservations_reserved_at
    ON reservations(reserved_at);
