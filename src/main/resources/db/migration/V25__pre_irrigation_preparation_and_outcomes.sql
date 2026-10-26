-- Migration V25: Persistent Pre-Irrigation Preparation Schedules, Task Lifecycles, and Irrigation Event Associations

-- 1. Extend planting_tasks with lifecycle states, deadlines, priority, and outcomes
ALTER TABLE planting_tasks 
    ADD COLUMN IF NOT EXISTS deadline TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS priority VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS evidence_source VARCHAR(255),
    ADD COLUMN IF NOT EXISTS completion_evidence TEXT,
    ADD COLUMN IF NOT EXISTS associated_irrigation_command_id VARCHAR(64);

-- 2. Create pre_irrigation_plans table for scheduling upcoming irrigation preparations
CREATE TABLE IF NOT EXISTS pre_irrigation_plans (
    id BIGSERIAL PRIMARY KEY,
    plan_code VARCHAR(64) NOT NULL UNIQUE,
    field_id BIGINT REFERENCES fields(id) ON DELETE CASCADE NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    crop_variety VARCHAR(100) NOT NULL,
    crop_stage VARCHAR(100) NOT NULL,
    irrigation_objective VARCHAR(255) NOT NULL,
    proposed_window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    proposed_window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    overall_status VARCHAR(50) NOT NULL DEFAULT 'PROPOSED', -- PROPOSED, SCHEDULED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED, ABANDONED, OVERDUE, BLOCKED, RESCHEDULED
    prerequisites_met BOOLEAN NOT NULL DEFAULT FALSE,
    readiness_score INTEGER NOT NULL DEFAULT 0,
    associated_irrigation_id VARCHAR(64),
    risk_factors_json TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pre_irrigation_plans_field ON pre_irrigation_plans(field_id);
CREATE INDEX IF NOT EXISTS idx_pre_irrigation_plans_user ON pre_irrigation_plans(user_id);
CREATE INDEX IF NOT EXISTS idx_pre_irrigation_plans_status ON pre_irrigation_plans(overall_status);

-- 3. Link planting_tasks to pre_irrigation_plans (nullable for backwards compatibility with planting_plans)
ALTER TABLE planting_tasks
    ADD COLUMN IF NOT EXISTS pre_irrigation_plan_id BIGINT REFERENCES pre_irrigation_plans(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_planting_tasks_prep_plan ON planting_tasks(pre_irrigation_plan_id);

-- 4. Create post_irrigation_outcomes table for learning from completed, rejected, and abandoned preparations
CREATE TABLE IF NOT EXISTS post_irrigation_outcomes (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT REFERENCES pre_irrigation_plans(id) ON DELETE SET NULL,
    irrigation_command_id VARCHAR(64) NOT NULL,
    field_id BIGINT REFERENCES fields(id) ON DELETE CASCADE NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    controller_status VARCHAR(50) NOT NULL,
    water_level_delta_cm DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    soil_moisture_delta_pct DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    duration_minutes INTEGER NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    farmer_feedback TEXT,
    notes TEXT,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_post_irrigation_outcomes_field ON post_irrigation_outcomes(field_id);
CREATE INDEX IF NOT EXISTS idx_post_irrigation_outcomes_cmd ON post_irrigation_outcomes(irrigation_command_id);

