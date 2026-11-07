-- Migration V28: Chatbot Session Metadata (Status, Summary, Active Workflow) and Message Idempotency
-- ArvaCore 2026

ALTER TABLE chatbot_sessions ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE chatbot_sessions ADD COLUMN IF NOT EXISTS summary TEXT;
ALTER TABLE chatbot_sessions ADD COLUMN IF NOT EXISTS workflow_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_chatbot_sessions_status ON chatbot_sessions(status);

ALTER TABLE chatbot_messages ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_chatbot_messages_client_id ON chatbot_messages(client_message_id);
