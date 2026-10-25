-- =====================================================================
-- Atelier 3D & Media Engine :: media_details
-- Owned by Flyway. Hibernate never mutates this schema (ddl-auto: none).
-- =====================================================================

CREATE TABLE IF NOT EXISTS media_details (
    id                    BIGSERIAL       PRIMARY KEY,

    -- File identity
    original_file_name    VARCHAR(255)    NOT NULL,
    stored_file_name      VARCHAR(120)    NOT NULL,
    s3_bucket             VARCHAR(63)     NOT NULL,
    s3_key                VARCHAR(512)    NOT NULL,
    s3_url                VARCHAR(1024)   NOT NULL,
    content_type          VARCHAR(64)     NOT NULL,
    file_extension        VARCHAR(10)     NOT NULL,
    file_size_bytes       BIGINT          NOT NULL CHECK (file_size_bytes > 0),
    original_size_bytes   BIGINT                   CHECK (original_size_bytes IS NULL OR original_size_bytes > 0),

    -- Classification & lifecycle
    media_type            VARCHAR(20)     NOT NULL
                          CHECK (media_type IN ('IMAGE_2D', 'MODEL_3D', 'DAILY_SPECIAL')),
    upload_status         VARCHAR(20)     NOT NULL DEFAULT 'PRESIGNED'
                          CHECK (upload_status IN ('PRESIGNED', 'COMPLETED', 'FAILED')),

    -- Menu metadata
    dish_title            VARCHAR(160)    NOT NULL,
    price_tag             NUMERIC(10, 2)  NOT NULL CHECK (price_tag >= 0),
    dish_category         VARCHAR(20)     NOT NULL
                          CHECK (dish_category IN ('STARTERS', 'MAINS', 'DESSERTS', 'CHEF_SPECIALS')),

    -- 3D / AR
    has_ar_support        BOOLEAN         NOT NULL DEFAULT FALSE,
    ar_placement          VARCHAR(10)     NOT NULL DEFAULT 'table'
                          CHECK (ar_placement IN ('table', 'floor')),

    -- Raster metadata (2D) / integrity
    width_px              INTEGER                  CHECK (width_px IS NULL OR width_px > 0),
    height_px             INTEGER                  CHECK (height_px IS NULL OR height_px > 0),
    etag                  VARCHAR(128),

    -- Audit
    uploaded_by_user_id   VARCHAR(64),
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at          TIMESTAMPTZ,

    CONSTRAINT uq_media_details_s3_key UNIQUE (s3_key),
    CONSTRAINT ck_media_details_completed_at
        CHECK (upload_status <> 'COMPLETED' OR completed_at IS NOT NULL)
);

-- Requested lookup indexes
CREATE INDEX IF NOT EXISTS idx_media_details_upload_status ON media_details (upload_status);
CREATE INDEX IF NOT EXISTS idx_media_details_dish_category ON media_details (dish_category);
CREATE INDEX IF NOT EXISTS idx_media_details_media_type    ON media_details (media_type);
CREATE INDEX IF NOT EXISTS idx_media_details_created_at    ON media_details (created_at DESC);

-- Hot path: the live menu query (completed items, newest first)
CREATE INDEX IF NOT EXISTS idx_media_details_menu
    ON media_details (created_at DESC)
    WHERE upload_status = 'COMPLETED';

COMMENT ON TABLE  media_details                     IS 'Dish media (2D WebP photos and 3D glTF/GLB scans) uploaded directly to S3 via presigned PUT URLs.';
COMMENT ON COLUMN media_details.original_size_bytes IS 'Size of the file the guest/staff selected before client-side compression; used to report savings.';
COMMENT ON COLUMN media_details.ar_placement        IS 'table = true-to-size tabletop placement; floor = freely scalable pedestal showpiece.';
