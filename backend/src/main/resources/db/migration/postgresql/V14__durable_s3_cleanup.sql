CREATE TABLE s3_cleanup_tasks (
 id VARCHAR(36) PRIMARY KEY,
 restaurant_id BIGINT NOT NULL,
 bucket VARCHAR(63) NOT NULL,
 object_key VARCHAR(512) NOT NULL,
 tenant_slug VARCHAR(60) NOT NULL,
 reason VARCHAR(80) NOT NULL,
 state VARCHAR(16) NOT NULL CHECK (state IN ('READY','LEASED','RETRY','DONE','HELD','MANUAL')),
 attempts INTEGER NOT NULL DEFAULT 0,
 available_at TIMESTAMP WITH TIME ZONE NOT NULL,
 lease_until TIMESTAMP WITH TIME ZONE,
 lease_token VARCHAR(36),
 last_outcome VARCHAR(32),
 last_error_code VARCHAR(64),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(bucket,object_key)
);
CREATE INDEX idx_cleanup_due ON s3_cleanup_tasks(state,available_at);
CREATE INDEX idx_cleanup_lease ON s3_cleanup_tasks(state,lease_until);
CREATE INDEX idx_cleanup_key ON s3_cleanup_tasks(object_key);
CREATE INDEX idx_cleanup_tenant ON s3_cleanup_tasks(restaurant_id);