-- Migration V16: Completely drop master_courses table and eliminate legacy master_course_id columns

-- 1. Drop foreign keys and legacy columns referencing master_courses
ALTER TABLE IF EXISTS programme_batch_courses DROP COLUMN IF EXISTS master_course_id CASCADE;
ALTER TABLE IF EXISTS approval_requests DROP COLUMN IF EXISTS master_course_id CASCADE;

-- 2. Drop master_courses table entirely
DROP TABLE IF EXISTS master_courses CASCADE;
