-- 用户角色与组织基础信息
ALTER TABLE app_user
  ADD COLUMN role_code VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE' AFTER password_hash;

CREATE INDEX idx_app_user_role ON app_user(role_code);

-- 题目主表：题干、题型、答案和解析统一存储
CREATE TABLE question_item (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  bank_id BIGINT NOT NULL,
  question_type VARCHAR(20) NOT NULL,
  stem TEXT NOT NULL,
  answer_text TEXT,
  analysis TEXT,
  difficulty VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  score DECIMAL(8,2) NOT NULL DEFAULT 0,
  sort_no INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  created_by BIGINT,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_question_item_bank FOREIGN KEY (bank_id) REFERENCES question_bank(id),
  CONSTRAINT fk_question_item_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX idx_question_item_bank ON question_item(bank_id);
CREATE INDEX idx_question_item_type ON question_item(question_type);
CREATE INDEX idx_question_item_status ON question_item(status);

-- 选择题选项表，判断题和简答题可以不使用该表
CREATE TABLE question_option (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT NOT NULL,
  option_key VARCHAR(10) NOT NULL,
  option_text TEXT NOT NULL,
  is_correct BOOLEAN NOT NULL DEFAULT FALSE,
  sort_no INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_question_option_question FOREIGN KEY (question_id) REFERENCES question_item(id) ON DELETE CASCADE,
  UNIQUE KEY uk_question_option_key (question_id, option_key)
);

-- 试卷表：固定组卷和随机组卷都通过 mode 区分
CREATE TABLE test_paper (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(500),
  mode VARCHAR(20) NOT NULL DEFAULT 'FIXED',
  total_score DECIMAL(8,2) NOT NULL DEFAULT 0,
  pass_score DECIMAL(8,2) NOT NULL DEFAULT 60,
  duration_minutes INT NOT NULL DEFAULT 30,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  created_by BIGINT,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_test_paper_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE test_paper_question (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  paper_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  score DECIMAL(8,2) NOT NULL DEFAULT 0,
  sort_no INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_paper_question_paper FOREIGN KEY (paper_id) REFERENCES test_paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_paper_question_question FOREIGN KEY (question_id) REFERENCES question_item(id),
  UNIQUE KEY uk_paper_question (paper_id, question_id)
);

CREATE INDEX idx_paper_question_paper ON test_paper_question(paper_id);

-- 考试任务：负责发布时间、人员范围、乱序和重考规则
CREATE TABLE assessment_task (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  paper_id BIGINT NOT NULL,
  start_at TIMESTAMP(3) NOT NULL,
  end_at TIMESTAMP(3) NOT NULL,
  duration_minutes INT NOT NULL DEFAULT 30,
  candidate_scope VARCHAR(20) NOT NULL DEFAULT 'ALL',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  allow_retake BOOLEAN NOT NULL DEFAULT FALSE,
  max_attempts INT NOT NULL DEFAULT 1,
  shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
  shuffle_options BOOLEAN NOT NULL DEFAULT FALSE,
  show_result_immediately BOOLEAN NOT NULL DEFAULT TRUE,
  created_by BIGINT,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_assessment_task_paper FOREIGN KEY (paper_id) REFERENCES test_paper(id),
  CONSTRAINT fk_assessment_task_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX idx_assessment_task_time ON assessment_task(start_at, end_at);
CREATE INDEX idx_assessment_task_status ON assessment_task(status);

-- 任务参与人：支持正式员工和免登录临时考生
CREATE TABLE assessment_participant (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  user_id BIGINT,
  candidate_name VARCHAR(80) NOT NULL,
  phone VARCHAR(20),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_participant_task FOREIGN KEY (task_id) REFERENCES assessment_task(id) ON DELETE CASCADE,
  CONSTRAINT fk_participant_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_participant_task ON assessment_participant(task_id);
CREATE INDEX idx_participant_phone ON assessment_participant(phone);

-- 每次答题过程独立留痕，支持重考和成绩快照
CREATE TABLE assessment_attempt (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  participant_id BIGINT NOT NULL,
  attempt_no INT NOT NULL DEFAULT 1,
  status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
  total_score DECIMAL(8,2) NOT NULL DEFAULT 0,
  score DECIMAL(8,2) NOT NULL DEFAULT 0,
  passed BOOLEAN NOT NULL DEFAULT FALSE,
  started_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  submitted_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_attempt_task FOREIGN KEY (task_id) REFERENCES assessment_task(id),
  CONSTRAINT fk_attempt_participant FOREIGN KEY (participant_id) REFERENCES assessment_participant(id),
  UNIQUE KEY uk_attempt_number (participant_id, attempt_no)
);

CREATE TABLE assessment_answer (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attempt_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  answer_text TEXT,
  correct_answer TEXT,
  is_correct BOOLEAN,
  score DECIMAL(8,2) NOT NULL DEFAULT 0,
  answered_at TIMESTAMP(3),
  CONSTRAINT fk_answer_attempt FOREIGN KEY (attempt_id) REFERENCES assessment_attempt(id) ON DELETE CASCADE,
  CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES question_item(id),
  UNIQUE KEY uk_attempt_question (attempt_id, question_id)
);

-- 统一文件元数据，实际文件可落本地或 OSS
CREATE TABLE file_object (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  original_name VARCHAR(255) NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  object_url VARCHAR(1000),
  content_type VARCHAR(120),
  file_size BIGINT NOT NULL DEFAULT 0,
  storage_type VARCHAR(20) NOT NULL DEFAULT 'OSS',
  uploaded_by BIGINT,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_file_object_uploader FOREIGN KEY (uploaded_by) REFERENCES app_user(id)
);

CREATE INDEX idx_file_object_uploader ON file_object(uploaded_by);

-- 管理操作留痕，便于审计和问题追踪
CREATE TABLE operation_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT,
  module_name VARCHAR(60) NOT NULL,
  operation_type VARCHAR(30) NOT NULL,
  request_uri VARCHAR(500),
  request_method VARCHAR(10),
  trace_id VARCHAR(80),
  result_code VARCHAR(40),
  duration_ms BIGINT,
  client_ip VARCHAR(64),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_operation_log_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_operation_log_created_at ON operation_log(created_at);
CREATE INDEX idx_operation_log_trace_id ON operation_log(trace_id);
