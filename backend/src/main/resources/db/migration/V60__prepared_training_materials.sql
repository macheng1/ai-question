CREATE TABLE training_material_upload (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    material_type VARCHAR(10) NOT NULL,
    object_key VARCHAR(128) NOT NULL UNIQUE,
    page_count INT NOT NULL,
    expires_at BIGINT NOT NULL,
    INDEX idx_material_upload_expiry (expires_at)
);
