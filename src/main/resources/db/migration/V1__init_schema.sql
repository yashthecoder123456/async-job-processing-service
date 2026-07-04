CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority INT NOT NULL,
    max_retries INT NOT NULL,
    timeout_seconds INT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    result_payload JSONB,
    idempotency_key VARCHAR(255) UNIQUE,
    locked_by VARCHAR(128),
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_created_at ON jobs(created_at);
CREATE INDEX idx_jobs_idempotency_key ON jobs(idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE job_attempts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id),
    attempt_number INT NOT NULL,
    worker_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT
);

CREATE INDEX idx_job_attempts_job_id ON job_attempts(job_id);

CREATE TABLE dead_letter_jobs (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE REFERENCES jobs(id),
    payload JSONB NOT NULL,
    final_error TEXT,
    attempt_count INT NOT NULL,
    dead_lettered_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    destination VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    publish_after TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    locked_by VARCHAR(128),
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox_events(status, publish_after, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE system_state (
    key VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO system_state (key, value, updated_at) VALUES
    ('submissions_enabled', 'true', NOW()),
    ('dispatcher_enabled', 'true', NOW()),
    ('workers_enabled', 'true', NOW());
