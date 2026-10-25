-- =====================================================================
-- V2: multi-tenancy. Every media row belongs to exactly one restaurant.
-- =====================================================================

CREATE TABLE IF NOT EXISTS restaurants (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    slug          VARCHAR(60)  NOT NULL,
    owner_name    VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_restaurants_slug  UNIQUE (slug),
    CONSTRAINT uq_restaurants_email UNIQUE (email),
    CONSTRAINT ck_restaurants_slug_format CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,58}[a-z0-9]$')
);

-- Explicit slug index for guest menu lookups (/r/{slug}).
CREATE INDEX IF NOT EXISTS idx_restaurants_slug ON restaurants (slug);

ALTER TABLE media_details ADD COLUMN IF NOT EXISTS restaurant_id BIGINT;

-- Optimistic locking: protects confirm vs. stale-upload reaper races.
ALTER TABLE media_details ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Databases that already hold pre-tenant media: park those rows under a placeholder restaurant
-- that cannot sign in ('!' is not a valid BCrypt hash), so restaurant_id can be NOT NULL.
-- Re-assign them later with:
--   UPDATE media_details SET restaurant_id = (SELECT id FROM restaurants WHERE slug = 'your-slug')
--    WHERE restaurant_id = (SELECT id FROM restaurants WHERE slug = 'legacy-unassigned');
INSERT INTO restaurants (name, slug, owner_name, email, password_hash)
SELECT 'Legacy (unassigned)', 'legacy-unassigned', 'Unassigned', 'legacy-unassigned@atelier.invalid', '!'
WHERE EXISTS (SELECT 1 FROM media_details WHERE restaurant_id IS NULL);

UPDATE media_details
   SET restaurant_id = (SELECT id FROM restaurants WHERE slug = 'legacy-unassigned')
 WHERE restaurant_id IS NULL;

ALTER TABLE media_details ALTER COLUMN restaurant_id SET NOT NULL;

ALTER TABLE media_details
    ADD CONSTRAINT fk_media_details_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_media_details_restaurant_status ON media_details (restaurant_id, upload_status);

-- Hot-path menu index, now partitioned by restaurant.
DROP INDEX IF EXISTS idx_media_details_menu;
CREATE INDEX idx_media_details_menu
    ON media_details (restaurant_id, created_at DESC)
    WHERE upload_status = 'COMPLETED';

COMMENT ON TABLE  restaurants               IS 'Tenants. One owner account per restaurant; slug is the public /r/{slug} handle and S3 namespace.';
COMMENT ON COLUMN media_details.restaurant_id IS 'Owning restaurant (tenant). All staff and guest queries filter on it.';
