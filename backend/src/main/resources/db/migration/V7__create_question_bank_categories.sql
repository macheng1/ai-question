CREATE TABLE question_bank_category (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  parent_id BIGINT NULL,
  description VARCHAR(255),
  sort_no INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  CONSTRAINT uk_question_bank_category_name_parent UNIQUE (name, parent_id),
  CONSTRAINT fk_question_bank_category_parent FOREIGN KEY (parent_id) REFERENCES question_bank_category(id) ON DELETE CASCADE
);

CREATE INDEX idx_question_bank_category_parent ON question_bank_category(parent_id);
