-- Add assigned_roles column to users table for IQAC multi-role assignment
ALTER TABLE users ADD COLUMN IF NOT EXISTS assigned_roles TEXT;
