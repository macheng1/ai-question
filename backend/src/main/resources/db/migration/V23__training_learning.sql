ALTER TABLE training ADD COLUMN assessment_id BIGINT NULL;
CREATE TABLE training_video (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, training_id BIGINT NOT NULL, title VARCHAR(255) NOT NULL,
 storage_path VARCHAR(100) NOT NULL, content_type VARCHAR(50) NOT NULL, sort_order INT NOT NULL DEFAULT 0,
 FOREIGN KEY (training_id) REFERENCES training(id) ON DELETE CASCADE
);
CREATE TABLE training_video_progress (
 video_id BIGINT NOT NULL, user_id BIGINT NOT NULL, position_seconds DOUBLE NOT NULL DEFAULT 0,
 completed BOOLEAN NOT NULL DEFAULT FALSE, PRIMARY KEY(video_id,user_id),
 FOREIGN KEY(video_id) REFERENCES training_video(id) ON DELETE CASCADE
);
