ALTER TABLE task_wait_subscriptions
    ADD COLUMN IF NOT EXISTS promoted_run_code VARCHAR(64);

CREATE TABLE IF NOT EXISTS task_wait_webhook_nonces (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(160) NOT NULL,
    nonce VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_wait_webhook_nonce UNIQUE(event_key, nonce)
);

CREATE INDEX IF NOT EXISTS idx_task_wait_webhook_nonce_expiry
    ON task_wait_webhook_nonces(expires_at);

COMMENT ON COLUMN task_wait_subscriptions.promoted_run_code IS '同步执行因持久等待自动提升生成的异步 Run Code';
COMMENT ON TABLE task_wait_webhook_nonces IS 'Webhook 唤醒请求 nonce 防重放记录';
COMMENT ON COLUMN task_wait_webhook_nonces.event_key IS '等待订阅事件键';
COMMENT ON COLUMN task_wait_webhook_nonces.nonce IS '调用方提供的单次随机值';
COMMENT ON COLUMN task_wait_webhook_nonces.expires_at IS 'nonce 失效时间，过期后可清理';
