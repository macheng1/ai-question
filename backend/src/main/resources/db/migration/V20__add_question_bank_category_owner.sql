ALTER TABLE question_bank_category ADD COLUMN created_by BIGINT NULL AFTER sort_no;
CREATE INDEX idx_question_bank_category_created_by ON question_bank_category(created_by);
ALTER TABLE question_bank_category ADD CONSTRAINT fk_question_bank_category_created_by
    FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL;
