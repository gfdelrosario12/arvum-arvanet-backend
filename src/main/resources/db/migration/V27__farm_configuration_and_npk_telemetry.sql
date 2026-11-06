-- Migration V27: Canonical Farm Configuration, Session Working State, and NPK Soil Telemetry
-- ArvaCore Smart Irrigation Ecosystem (2026)
-- Target: 1.1-Hectare Rice Farm, Barangay Silang, Lopez, Quezon (Pitaks 1-6)

-- 1. Create canonical farm_configurations table
CREATE TABLE IF NOT EXISTS farm_configurations (
    id BIGSERIAL PRIMARY KEY,
    field_id BIGINT NOT NULL REFERENCES fields(id) ON DELETE CASCADE,
    farm_name VARCHAR(255) NOT NULL,
    geographical_location VARCHAR(255) NOT NULL,
    total_area_hectares DOUBLE PRECISION NOT NULL DEFAULT 1.1,
    pitak_count INTEGER NOT NULL DEFAULT 6,
    soil_type VARCHAR(100) NOT NULL DEFAULT 'Calumpang Clay (Macolod Series)',
    soil_characteristics TEXT,
    water_sources_json TEXT,       -- Available water sources: natural creek flow, rainfall, deep well, irrigation pump
    distribution_channels_json TEXT, -- Canal arrangement, gate linkages, drainage layout
    equipment_inventory_json TEXT, -- Tractors, moldboard plows, disc plows, rotavators, harrows, wooden leveling planks
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_farm_config_field UNIQUE (field_id)
);

CREATE INDEX IF NOT EXISTS idx_farm_config_field ON farm_configurations(field_id);

-- Seed default farm configuration for Field 1
INSERT INTO farm_configurations (
    field_id, farm_name, geographical_location, total_area_hectares, pitak_count,
    soil_type, soil_characteristics, water_sources_json, distribution_channels_json,
    equipment_inventory_json, is_active, created_at, updated_at
)
VALUES (
    1,
    'Silang Terraced Rice Paddy Field',
    'Barangay Silang, Lopez, Quezon, Philippines (13.8821° N, 121.9906° E)',
    1.1,
    6,
    'Calumpang Clay (Macolod Clay Loam)',
    'Heavy clay with high moisture retention, distinct plow pan at 15-20 cm depth, slow percolation rate (< 2 mm/day once puddled). Ideal for Alternate Wetting and Drying (AWD).',
    '["NIA Inflow Lateral Canal", "Deep Well Submersible Pump (400W)", "Rainfall Catchment", "Natural Runoff"]',
    '{"gates": ["Gate 1 (Inflow Canal, Pitak 1-2)", "Gate 2 (Mid Lateral, Pitak 3-4)", "Gate 3 (Distal Distribution, Pitak 5-6)"], "drainage": "Tail Drain Flume at Pitak 6"}',
    '["Hand Tractor (Kuliglig)", "Moldboard Plow", "Comb Harrow (Suyod)", "Wooden Leveling Plank (Haba)", "Rotavator Attachment"]',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (field_id) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP;

-- 2. Extend chatbot_sessions with session_working_config_json to isolate temporary vs permanent adjustments
ALTER TABLE chatbot_sessions ADD COLUMN IF NOT EXISTS session_working_config_json TEXT;
ALTER TABLE chatbot_sessions ADD COLUMN IF NOT EXISTS config_confirmed_by_farmer BOOLEAN DEFAULT FALSE;

-- 3. Extend telemetry_readings with NPK nutrient measurements and index them
-- In telemetry_readings, values are stored per sensor_type or extended columns
ALTER TABLE telemetry_readings ADD COLUMN IF NOT EXISTS nitrogen_mg_kg DOUBLE PRECISION;
ALTER TABLE telemetry_readings ADD COLUMN IF NOT EXISTS phosphorus_mg_kg DOUBLE PRECISION;
ALTER TABLE telemetry_readings ADD COLUMN IF NOT EXISTS potassium_mg_kg DOUBLE PRECISION;

-- 4. Extend planting_tasks with tillage_stage for discrete 8-stage progress tracking
ALTER TABLE planting_tasks ADD COLUMN IF NOT EXISTS tillage_stage VARCHAR(50);
-- Valid stages: PRIMARY_PLOWING, DECOMPOSITION_SUBMERGENCE, FIRST_HARROWING, SECOND_HARROWING, THIRD_HARROWING, LAND_LEVELING, FINAL_READINESS, CROP_ESTABLISHMENT

CREATE INDEX IF NOT EXISTS idx_planting_tasks_tillage_stage ON planting_tasks(tillage_stage);

