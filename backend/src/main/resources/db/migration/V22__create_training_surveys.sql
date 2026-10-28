CREATE TABLE training (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(2000) NOT NULL DEFAULT '',
  survey_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  questions_json JSON NOT NULL,
  version INT NOT NULL DEFAULT 1,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_training_owner (created_by)
);
CREATE TABLE training_survey_response (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  training_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  answers_json JSON NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_training_response (training_id, user_id),
  CONSTRAINT fk_training_response FOREIGN KEY (training_id) REFERENCES training(id)
);
