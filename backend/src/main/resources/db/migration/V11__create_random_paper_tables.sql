-- 随机组卷配置：一张试卷可从多个题库按题型抽取不同数量的题目
CREATE TABLE test_paper_random_bank (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  paper_id BIGINT NOT NULL,
  bank_id BIGINT NOT NULL,
  single_count INT NOT NULL DEFAULT 0,
  single_score DECIMAL(8,2) NOT NULL DEFAULT 0,
  multiple_count INT NOT NULL DEFAULT 0,
  multiple_score DECIMAL(8,2) NOT NULL DEFAULT 0,
  true_false_count INT NOT NULL DEFAULT 0,
  true_false_score DECIMAL(8,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_random_bank_paper FOREIGN KEY (paper_id) REFERENCES test_paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_random_bank_bank FOREIGN KEY (bank_id) REFERENCES question_bank(id),
  UNIQUE KEY uk_random_paper_bank (paper_id, bank_id)
);

-- 每次考试固定一份抽题快照，保证中途退出恢复时题目不变
CREATE TABLE assessment_attempt_question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attempt_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  score DECIMAL(8,2) NOT NULL DEFAULT 0,
  sort_no INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_attempt_question_attempt FOREIGN KEY (attempt_id) REFERENCES assessment_attempt(id) ON DELETE CASCADE,
  CONSTRAINT fk_attempt_question_question FOREIGN KEY (question_id) REFERENCES question_item(id),
  UNIQUE KEY uk_attempt_question_snapshot (attempt_id, question_id)
);

CREATE INDEX idx_attempt_question_attempt ON assessment_attempt_question(attempt_id);
