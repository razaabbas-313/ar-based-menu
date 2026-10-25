-- Add indexes on analytics_events for efficient querying by restaurant/dish over time
CREATE INDEX IF NOT EXISTS idx_analytics_events_restaurant_created ON analytics_events(restaurant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_analytics_events_dish_created ON analytics_events(dish_id, created_at);

-- Daily aggregates table for fast and cheap analytics queries
CREATE TABLE IF NOT EXISTS analytics_daily (
    restaurant_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL DEFAULT 0,
    event_type VARCHAR(30) NOT NULL,
    day DATE NOT NULL,
    event_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (restaurant_id, dish_id, event_type, day)
);

CREATE INDEX IF NOT EXISTS idx_analytics_daily_restaurant_day ON analytics_daily(restaurant_id, day);
