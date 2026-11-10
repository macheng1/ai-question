ALTER TABLE candidate_learning_session MODIFY task_id BIGINT NULL,
 ADD COLUMN training_id BIGINT NULL,
 ADD CONSTRAINT fk_learning_training FOREIGN KEY(training_id) REFERENCES training(id) ON DELETE CASCADE,
 ADD UNIQUE KEY candidate_training(training_id,candidate_name,phone);
