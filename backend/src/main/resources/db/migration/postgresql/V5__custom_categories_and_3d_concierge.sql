-- =====================================================================
-- V5: Custom Categories, 3D White-Glove Concierge, Menu Live Switch,
--     Admin Notes, OTP Password Reset, Analytics
-- =====================================================================

-- ---------------------------------------------------------------- categories
CREATE TABLE IF NOT EXISTS categories (
    id            BIGSERIAL    PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_categories_restaurant ON categories (restaurant_id, display_order, id);

-- ---------------------------------------------------------------- restaurants updates
ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS is_menu_live        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS admin_notes         TEXT,
    ADD COLUMN IF NOT EXISTS menu_views_count    BIGINT  NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS model_views_count   BIGINT  NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ar_launches_count   BIGINT  NOT NULL DEFAULT 0;

-- Update status constraint to include PENDING_ONBOARDING
ALTER TABLE restaurants DROP CONSTRAINT IF EXISTS ck_restaurants_status;
ALTER TABLE restaurants
    ADD CONSTRAINT ck_restaurants_status
    CHECK (status IN ('ACTIVE', 'TRIAL', 'PENDING', 'PENDING_ONBOARDING', 'SUSPENDED'));

-- ---------------------------------------------------------------- media_details updates
ALTER TABLE media_details
    ADD COLUMN IF NOT EXISTS category_id          BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS model_request_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS model_request_photos TEXT,
    ADD COLUMN IF NOT EXISTS model_request_notes  TEXT,
    ADD COLUMN IF NOT EXISTS model_requested_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS model_completed_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS views_count          BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS model_views_count    BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ar_launches_count    BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_media_details_category ON media_details (category_id);
CREATE INDEX IF NOT EXISTS idx_media_details_model_req ON media_details (model_request_status);

-- ---------------------------------------------------------------- password_reset_otps
CREATE TABLE IF NOT EXISTS password_reset_otps (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(160) NOT NULL,
    otp_code   VARCHAR(10)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_otps_lookup ON password_reset_otps (email, used, expires_at);

-- ---------------------------------------------------------------- analytics_events
CREATE TABLE IF NOT EXISTS analytics_events (
    id            BIGSERIAL   PRIMARY KEY,
    restaurant_id BIGINT      NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    dish_id       BIGINT,
    event_type    VARCHAR(30) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_analytics_events_lookup ON analytics_events (restaurant_id, event_type, created_at DESC);

-- ---------------------------------------------------------------- Seed default categories for existing restaurants
INSERT INTO categories (restaurant_id, name, display_order)
SELECT r.id, c.name, c.display_order
FROM restaurants r
CROSS JOIN (
    VALUES
        ('Starters', 1),
        ('Mains', 2),
        ('Desserts', 3),
        ('Chef Specials', 4),
        ('Drinks', 5)
) AS c(name, display_order);

-- Map existing dishes to their newly seeded category
UPDATE media_details m
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.restaurant_id = m.restaurant_id
      AND (
        (m.dish_category = 'STARTERS' AND c.name = 'Starters') OR
        (m.dish_category = 'MAINS' AND c.name = 'Mains') OR
        (m.dish_category = 'DESSERTS' AND c.name = 'Desserts') OR
        (m.dish_category = 'CHEF_SPECIALS' AND c.name = 'Chef Specials') OR
        (m.dish_category = 'DRINKS' AND c.name = 'Drinks')
      )
    LIMIT 1
)
WHERE m.category_id IS NULL;
