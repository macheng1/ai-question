ALTER TABLE training_material
 ADD COLUMN preview_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 ADD COLUMN preview_error VARCHAR(255) NULL,
 ADD INDEX idx_material_preview_status (preview_status,id);
ALTER TABLE training_material_page
 ADD COLUMN preview_key VARCHAR(512) NULL,
 ADD COLUMN preview_ready BOOLEAN NOT NULL DEFAULT FALSE;
