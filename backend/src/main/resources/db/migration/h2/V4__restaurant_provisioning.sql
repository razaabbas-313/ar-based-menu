-- H2 equivalent of db/migration/postgresql/V4 (used by the 'h2' profile).

ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE restaurants ALTER COLUMN must_change_password SET DEFAULT TRUE;
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS tiktok_handle VARCHAR(100);
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS google_maps_url VARCHAR(500);

ALTER TABLE media_details ADD COLUMN IF NOT EXISTS pending_image_s3_key VARCHAR(512);
ALTER TABLE media_details ADD COLUMN IF NOT EXISTS pending_image_size_bytes BIGINT;
ALTER TABLE media_details ADD COLUMN IF NOT EXISTS pending_image_original_size_bytes BIGINT;
ALTER TABLE media_details ADD COLUMN IF NOT EXISTS pending_image_content_type VARCHAR(64);
ALTER TABLE media_details ADD COLUMN IF NOT EXISTS pending_image_created_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_media_details_pending_image ON media_details (pending_image_created_at);
