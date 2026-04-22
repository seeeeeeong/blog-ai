CREATE TABLE IF NOT EXISTS chat_rate_limit (
    scope    VARCHAR(20)  NOT NULL,
    key      VARCHAR(100) NOT NULL,
    count    INT          NOT NULL DEFAULT 0,
    reset_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (scope, key)
);

CREATE INDEX IF NOT EXISTS idx_chat_rate_limit_reset_at ON chat_rate_limit(reset_at);
