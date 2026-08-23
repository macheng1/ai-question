ALTER TABLE question_bank
  ADD COLUMN created_by BIGINT NULL AFTER published;

CREATE INDEX idx_question_bank_created_by ON question_bank(created_by);

ALTER TABLE question_bank
  ADD CONSTRAINT fk_question_bank_created_by
  FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL;
