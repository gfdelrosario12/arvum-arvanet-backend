-- Migration V26: AI Chat Multi-Session History, Implementation Plan Lifecycle, and Task Dependencies

-- 1. Extend chatbot_sessions with title, archived status, and last activity tracking
ALTER TABLE chatbot_sessions ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE chatbot_sessions ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chatbot_sessions ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chatbot_sessions_archived ON chatbot_sessions(is_archived);
CREATE INDEX IF NOT EXISTS idx_chatbot_sessions_last_activity ON chatbot_sessions(last_activity_at DESC);

-- 2. Extend planting_plans with formal lifecycle status, description, objectives, progress, and outcomes
ALTER TABLE planting_plans ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE planting_plans ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE planting_plans ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED'; -- NOT_STARTED, IN_PROGRESS, CONTINUED, COMPLETED, CANCELLED, FAILED
ALTER TABLE planting_plans ADD COLUMN IF NOT EXISTS objectives_json TEXT;
ALTER TABLE planting_plans ADD COLUMN IF NOT EXISTS progress_percent INTEGER NOT NULL DEFAULT 0;
ALTER TABLE planting_plans ADD COLUMN IF NOT EXISTS status_reason TEXT;
ALTER TABLE planting_plans ADD COLUMN IF NOT EXISTS completion_evidence TEXT;

CREATE INDEX IF NOT EXISTS idx_planting_plans_lifecycle ON planting_plans(lifecycle_status);

-- 3. Create plan_conversation_links table to support N:M relationships between conversations and plans
CREATE TABLE IF NOT EXISTS plan_conversation_links (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES planting_plans(id) ON DELETE CASCADE,
    session_id VARCHAR(64) NOT NULL REFERENCES chatbot_sessions(session_id) ON DELETE CASCADE,
    link_type VARCHAR(50) NOT NULL DEFAULT 'ASSOCIATED', -- CREATED, REFERENCED, RESUMED
    linked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_plan_conversation_link UNIQUE (plan_id, session_id)
);

CREATE INDEX IF NOT EXISTS idx_plan_conv_links_plan ON plan_conversation_links(plan_id);
CREATE INDEX IF NOT EXISTS idx_plan_conv_links_session ON plan_conversation_links(session_id);

-- 4. Create plan_status_history table for auditing lifecycle transitions
CREATE TABLE IF NOT EXISTS plan_status_history (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES planting_plans(id) ON DELETE CASCADE,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_plan_status_history_plan ON plan_status_history(plan_id);

-- 5. Create planting_task_dependencies table for managing task dependencies and blocking state
CREATE TABLE IF NOT EXISTS planting_task_dependencies (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES planting_tasks(id) ON DELETE CASCADE,
    depends_on_task_id BIGINT NOT NULL REFERENCES planting_tasks(id) ON DELETE CASCADE,
    dependency_type VARCHAR(50) NOT NULL DEFAULT 'REQUIRED', -- REQUIRED, CONDITIONAL
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_task_dependency_pair UNIQUE (task_id, depends_on_task_id),
    CONSTRAINT chk_no_self_dependency CHECK (task_id <> depends_on_task_id)
);

CREATE INDEX IF NOT EXISTS idx_task_deps_task ON planting_task_dependencies(task_id);
CREATE INDEX IF NOT EXISTS idx_task_deps_depends_on ON planting_task_dependencies(depends_on_task_id);

-- Populate existing plan_conversation_links for plans that already have a session_id
INSERT INTO plan_conversation_links (plan_id, session_id, link_type, linked_at)
SELECT id, session_id, 'CREATED', created_at
FROM planting_plans
WHERE session_id IS NOT NULL AND session_id <> ''
ON CONFLICT (plan_id, session_id) DO NOTHING;

