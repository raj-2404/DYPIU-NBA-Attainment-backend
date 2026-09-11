-- ============================================================
-- V20__add_performance_composite_indexes.sql
-- Add composite performance indexes for approval requests, active courses, and marks
-- ============================================================

-- Fast lookup for allocation & dashboard approvals
CREATE INDEX IF NOT EXISTS idx_approval_requests_type_status_prog 
    ON approval_requests(type, status, master_programme_id);

CREATE INDEX IF NOT EXISTS idx_approval_requests_type_res 
    ON approval_requests(type, resource_id);

CREATE INDEX IF NOT EXISTS idx_approval_requests_batch_course_status 
    ON approval_requests(programme_batch_course_id, status);

-- Fast active course lookup
CREATE INDEX IF NOT EXISTS idx_programme_batch_courses_batch_active 
    ON programme_batch_courses(programme_batch_id) 
    WHERE deleted_at IS NULL;

-- Fast student CO mark lookup
CREATE INDEX IF NOT EXISTS idx_student_co_marks_offering_co 
    ON student_co_marks(programme_batch_course_id, co_code);
