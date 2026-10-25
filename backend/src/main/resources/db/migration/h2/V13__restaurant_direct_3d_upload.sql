-- =====================================================================
-- V13: Permission control for direct 3D model uploads by restaurant owner
-- =====================================================================

ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS can_upload_3d BOOLEAN NOT NULL DEFAULT FALSE;
