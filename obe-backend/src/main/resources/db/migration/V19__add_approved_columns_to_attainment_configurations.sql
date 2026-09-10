-- ============================================================
-- V19: Add approved configuration columns to attainment_configurations
-- Enables Programme Coordinator approved threshold & weights persistence
-- ============================================================

ALTER TABLE attainment_configurations
    ADD COLUMN IF NOT EXISTS approved_direct_weight NUMERIC(5,2) DEFAULT 80.00,
    ADD COLUMN IF NOT EXISTS approved_indirect_weight NUMERIC(5,2) DEFAULT 20.00,
    ADD COLUMN IF NOT EXISTS approved_direct_threshold NUMERIC(5,2) DEFAULT 60.00,
    ADD COLUMN IF NOT EXISTS approved_indirect_threshold NUMERIC(5,2) DEFAULT 60.00,
    ADD COLUMN IF NOT EXISTS approved_direct_levels_json TEXT,
    ADD COLUMN IF NOT EXISTS approved_indirect_levels_json TEXT,
    ADD COLUMN IF NOT EXISTS approved_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE;

-- Backfill existing rows with current values or default baselines
UPDATE attainment_configurations
SET
    approved_direct_weight = COALESCE(approved_direct_weight, direct_weight, 80.00),
    approved_indirect_weight = COALESCE(approved_indirect_weight, indirect_weight, 20.00),
    approved_direct_threshold = COALESCE(approved_direct_threshold, direct_threshold, 60.00),
    approved_indirect_threshold = COALESCE(approved_indirect_threshold, indirect_threshold, 60.00),
    approved_direct_levels_json = COALESCE(approved_direct_levels_json, direct_levels_json),
    approved_indirect_levels_json = COALESCE(approved_indirect_levels_json, indirect_levels_json),
    approved_by = COALESCE(approved_by, submitted_by, 'SYSTEM'),
    approved_at = COALESCE(approved_at, submitted_at, updated_at, created_at, CURRENT_TIMESTAMP)
WHERE approved_direct_weight IS NULL OR approved_at IS NULL;
