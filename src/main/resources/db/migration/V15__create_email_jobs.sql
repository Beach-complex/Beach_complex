CREATE TABLE email_jobs (
    id UUID PRIMARY KEY,
    recipient VARCHAR(320) NOT NULL,
    payload TEXT,
    token_hash VARCHAR(64) NOT NULL,
    producer_traceparent VARCHAR(55),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENDING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMPTZ,
    next_retry_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(64)
);
CREATE INDEX idx_email_jobs_due ON email_jobs (status, next_retry_at, lease_until);
CREATE TABLE email_job_attempts (
    job_id UUID NOT NULL REFERENCES email_jobs(id),
    attempt INTEGER NOT NULL CHECK (attempt BETWEEN 1 AND 4),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    outcome VARCHAR(32) NOT NULL DEFAULT 'STARTED',
    PRIMARY KEY (job_id, attempt)
);
