ALTER TABLE training_video_upload ADD COLUMN multipart_id VARCHAR(512) NULL;
CREATE INDEX idx_video_upload_owner ON training_video_upload(owner_id);
