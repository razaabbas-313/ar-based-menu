-- =====================================================================
-- V7: Auto-generated Unmodifiable Dish ID (dish_code)
-- =====================================================================

ALTER TABLE media_details ADD COLUMN IF NOT EXISTS dish_code VARCHAR(32);

-- Backfill dish_code for existing dishes as DSH-0001, DSH-0002, etc.
UPDATE media_details 
SET dish_code = 'DSH-' || LPAD(id::text, 4, '0') 
WHERE dish_code IS NULL;

ALTER TABLE media_details ALTER COLUMN dish_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_media_restaurant_dish_code ON media_details(restaurant_id, dish_code);
