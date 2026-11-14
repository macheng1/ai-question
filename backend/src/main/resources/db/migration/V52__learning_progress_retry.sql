ALTER TABLE candidate_material_progress
 ADD COLUMN consumed_heartbeat_token VARCHAR(36) NULL,
 ADD COLUMN consumed_seconds DOUBLE NULL;
ALTER TABLE candidate_video_progress
 ADD COLUMN last_request_id VARCHAR(80) NULL;
