ALTER TABLE test_paper_random_bank
  ADD COLUMN short_answer_count INT NOT NULL DEFAULT 0,
  ADD COLUMN short_answer_score DECIMAL(8,2) NOT NULL DEFAULT 0;

ALTER TABLE assessment_attempt_question
  ADD COLUMN snapshot_type VARCHAR(32),
  ADD COLUMN snapshot_stem TEXT,
  ADD COLUMN snapshot_answer TEXT;

ALTER TABLE assessment_attempt
  ADD COLUMN reviewed_by BIGINT,
  ADD COLUMN reviewed_at TIMESTAMP(3) NULL,
  ADD INDEX idx_attempt_review_queue (task_id, status, submitted_at);
