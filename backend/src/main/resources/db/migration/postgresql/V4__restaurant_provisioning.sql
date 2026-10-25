-- V4: super-admin-only provisioning, forced first-login password change, in-place photo
-- replacement, and more guest-menu contact links.

-- ---------------------------------------------------------------- restaurants
-- Accounts provisioned from now on start with a temporary password (must_change_password = TRUE).
-- Owners who already chose their own password (self-registered before v4) are not forced to reset.
ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE restaurants
    ALTER COLUMN must_change_password SET DEFAULT TRUE;

-- Status already exists since V3; kept idempotent so V4 can also run on a hand-made V2 schema.
ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tiktok_handle       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS google_maps_url     VARCHAR(500);

-- ---------------------------------------------------------------- media_details
-- A replacement photo that has been presigned but not confirmed yet. The live photo keeps serving
-- guests until the new object is verified with an S3 HEAD.
ALTER TABLE media_details
    ADD COLUMN IF NOT EXISTS pending_image_s3_key              VARCHAR(512),
    ADD COLUMN IF NOT EXISTS pending_image_size_bytes          BIGINT,
    ADD COLUMN IF NOT EXISTS pending_image_original_size_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS pending_image_content_type        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pending_image_created_at          TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_media_details_pending_image
    ON media_details (pending_image_created_at)
    WHERE pending_image_s3_key IS NOT NULL;
