-- =====================================================================
-- V12: Atomic Per-Restaurant Dish Sequence (next_dish_seq) (H2 compatible)
-- =====================================================================

ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS next_dish_seq BIGINT NOT NULL DEFAULT 1;

-- Backfill next_dish_seq from max existing numeric suffix of dish_code
UPDATE restaurants r
SET next_dish_seq = COALESCE(
    (
        SELECT MAX(CAST(SUBSTRING(m.dish_code, 5) AS BIGINT)) + 1
        FROM media_details m
        WHERE m.restaurant_id = r.id
          AND m.dish_code LIKE 'DSH-%'
    ),
    1
);
