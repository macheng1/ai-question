ALTER TABLE candidate_video_progress
 ADD COLUMN baseline_position DOUBLE NOT NULL DEFAULT 0,
 ADD COLUMN baseline_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
UPDATE candidate_video_progress SET baseline_position=position_seconds,baseline_at=CURRENT_TIMESTAMP(3);
