ALTER TABLE candidate_video_progress ADD COLUMN studied_seconds DOUBLE NULL,
 ADD COLUMN last_play_position DOUBLE NULL,
 ADD COLUMN first_studied_at TIMESTAMP(3) NULL,
 ADD COLUMN completed_at TIMESTAMP(3) NULL;
