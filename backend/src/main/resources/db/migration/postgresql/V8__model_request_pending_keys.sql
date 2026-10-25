-- =====================================================================
-- V8: Model request pending keys table & race-safe active request index
-- =====================================================================

CREATE TABLE IF NOT EXISTS model_request_pending_keys (
    id                   BIGSERIAL    PRIMARY KEY,
    restaurant_id        BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    dish_id              BIGINT       NOT NULL REFERENCES media_details(id) ON DELETE CASCADE,
    s3_key               VARCHAR(512) NOT NULL,
    slot                 VARCHAR(32)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pending_keys_dish ON model_request_pending_keys (dish_id);
CREATE INDEX IF NOT EXISTS idx_pending_keys_rest_dish ON model_request_pending_keys (restaurant_id, dish_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_model_request_active_dish ON model_requests (dish_id) WHERE status IN ('PENDING', 'IN_PRODUCTION');
