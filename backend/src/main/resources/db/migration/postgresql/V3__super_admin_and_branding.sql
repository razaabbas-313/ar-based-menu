-- =====================================================================
-- V3: platform super admin, restaurant branding/status/usage counters,
--     dual-track dishes (photo + optional 3D model), availability,
--     descriptions, dietary tags and the DRINKS category.
-- =====================================================================

-- ---------------------------------------------------------------- restaurants
ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS logo_url           TEXT,
    ADD COLUMN IF NOT EXISTS logo_s3_key        VARCHAR(512),
    ADD COLUMN IF NOT EXISTS logo_size_bytes    BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS banner_url         TEXT,
    ADD COLUMN IF NOT EXISTS banner_s3_key      VARCHAR(512),
    ADD COLUMN IF NOT EXISTS banner_size_bytes  BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tagline            VARCHAR(160),
    ADD COLUMN IF NOT EXISTS bio                TEXT,
    ADD COLUMN IF NOT EXISTS opening_hours      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS phone_number       VARCHAR(50),
    ADD COLUMN IF NOT EXISTS whatsapp_number    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS instagram_handle   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS facebook_url       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS website_url        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS theme_accent       VARCHAR(30)  NOT NULL DEFAULT 'amber',
    ADD COLUMN IF NOT EXISTS menu_layout        VARCHAR(20)  NOT NULL DEFAULT 'cards',
    ADD COLUMN IF NOT EXISTS currency_code      VARCHAR(3)   NOT NULL DEFAULT 'USD',
    ADD COLUMN IF NOT EXISTS currency_symbol    VARCHAR(10)  NOT NULL DEFAULT '$',
    ADD COLUMN IF NOT EXISTS storage_used_bytes BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS dish_count         INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS model_3d_count     INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at         TIMESTAMPTZ;

ALTER TABLE restaurants
    ADD CONSTRAINT ck_restaurants_status       CHECK (status IN ('ACTIVE', 'TRIAL', 'PENDING', 'SUSPENDED')),
    ADD CONSTRAINT ck_restaurants_theme_accent CHECK (theme_accent IN ('amber', 'emerald', 'gold', 'zinc')),
    ADD CONSTRAINT ck_restaurants_menu_layout  CHECK (menu_layout IN ('cards', 'compact')),
    ADD CONSTRAINT ck_restaurants_counters     CHECK (storage_used_bytes >= 0 AND dish_count >= 0 AND model_3d_count >= 0);

CREATE INDEX IF NOT EXISTS idx_restaurants_status  ON restaurants (status);
CREATE INDEX IF NOT EXISTS idx_restaurants_deleted ON restaurants (deleted_at);

-- ---------------------------------------------------------------- media_details
ALTER TABLE media_details
    ADD COLUMN IF NOT EXISTS model_s3_key               VARCHAR(500),
    ADD COLUMN IF NOT EXISTS model_s3_url               TEXT,
    ADD COLUMN IF NOT EXISTS model_file_size_bytes      BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pending_model_s3_key       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS pending_model_size_bytes   BIGINT,
    ADD COLUMN IF NOT EXISTS pending_model_content_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pending_model_created_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS is_available               BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS description                TEXT,
    ADD COLUMN IF NOT EXISTS dietary_tags               VARCHAR(255);

ALTER TABLE media_details
    ADD CONSTRAINT uq_media_details_model_s3_key UNIQUE (model_s3_key),
    ADD CONSTRAINT ck_media_details_model_size CHECK (model_file_size_bytes >= 0);

-- New category: DRINKS (replace the V1 inline check).
ALTER TABLE media_details DROP CONSTRAINT IF EXISTS media_details_dish_category_check;
ALTER TABLE media_details
    ADD CONSTRAINT ck_media_details_dish_category
    CHECK (dish_category IN ('STARTERS', 'MAINS', 'DESSERTS', 'CHEF_SPECIALS', 'DRINKS'));

-- v2 rows whose only file is a .glb: expose that file through the model columns too.
UPDATE media_details
   SET model_s3_key          = s3_key,
       model_s3_url          = s3_url,
       model_file_size_bytes = file_size_bytes
 WHERE content_type LIKE 'model/%'
   AND model_s3_key IS NULL;

-- Guest menu hot path.
CREATE INDEX IF NOT EXISTS idx_media_details_public_menu
    ON media_details (restaurant_id, is_available, created_at DESC);

-- Back-fill usage counters from existing data.
UPDATE restaurants r
   SET dish_count = (SELECT COUNT(*) FROM media_details m
                      WHERE m.restaurant_id = r.id AND m.upload_status = 'COMPLETED'),
       model_3d_count = (SELECT COUNT(*) FROM media_details m
                          WHERE m.restaurant_id = r.id AND m.upload_status = 'COMPLETED'
                            AND m.model_s3_key IS NOT NULL),
       storage_used_bytes = (SELECT COALESCE(SUM(
                                    CASE WHEN m.content_type LIKE 'model/%' THEN 0 ELSE m.file_size_bytes END
                                    + m.model_file_size_bytes), 0)
                               FROM media_details m
                              WHERE m.restaurant_id = r.id AND m.upload_status = 'COMPLETED');

-- ---------------------------------------------------------------- platform users
CREATE TABLE IF NOT EXISTS platform_users (
    id                   BIGSERIAL    PRIMARY KEY,
    email                VARCHAR(160) NOT NULL,
    display_name         VARCHAR(120) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    role                 VARCHAR(30)  NOT NULL DEFAULT 'ROLE_SUPER_ADMIN',
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at        TIMESTAMPTZ,
    CONSTRAINT uq_platform_users_email UNIQUE (email),
    CONSTRAINT ck_platform_users_role CHECK (role IN ('ROLE_SUPER_ADMIN'))
);

-- Seeded super admin. '!' is not a valid BCrypt hash, so nobody can sign in with it:
-- on first start the API sets the password from ATELIER_SUPERADMIN_PASSWORD (or generates
-- one and prints it once in the log) and forces a change at first sign-in.
INSERT INTO platform_users (email, display_name, password_hash, role, must_change_password)
VALUES ('admin@atelier.local', 'Platform Owner', '!', 'ROLE_SUPER_ADMIN', TRUE)
ON CONFLICT (email) DO NOTHING;

-- ---------------------------------------------------------------- admin audit trail
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id            BIGSERIAL    PRIMARY KEY,
    admin_email   VARCHAR(160) NOT NULL,
    action        VARCHAR(40)  NOT NULL,
    restaurant_id BIGINT,
    detail        VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_created ON admin_audit_log (created_at DESC);

COMMENT ON COLUMN restaurants.storage_used_bytes IS 'Bytes of COMPLETED dish photos + 3D models + logo/banner. Maintained atomically by the API.';
COMMENT ON COLUMN media_details.model_s3_key     IS 'Optional .glb attached to the dish; the primary s3_key is the 2D photo.';
