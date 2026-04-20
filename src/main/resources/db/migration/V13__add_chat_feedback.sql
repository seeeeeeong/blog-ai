CREATE TABLE IF NOT EXISTS chat_feedback (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    message_id VARCHAR(64) NOT NULL,
    rating VARCHAR(8) NOT NULL CHECK (rating IN ('up', 'down')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chat_feedback_session
    ON chat_feedback (session_id);
