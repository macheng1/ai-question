CREATE TABLE training_video_upload (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    byte_size BIGINT NOT NULL,
    object_key VARCHAR(128) NOT NULL UNIQUE,
    phase VARCHAR(12) NOT NULL,
    expires_at BIGINT NOT NULL,
    INDEX idx_video_upload_expiry (expires_at)
);
