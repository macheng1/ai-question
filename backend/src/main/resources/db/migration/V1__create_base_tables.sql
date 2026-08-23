CREATE TABLE question_bank (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  question_count INT NOT NULL DEFAULT 0,
  category VARCHAR(40) NOT NULL,
  source VARCHAR(255),
  published BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL
);

CREATE INDEX idx_question_bank_category ON question_bank(category);
CREATE INDEX idx_question_bank_published ON question_bank(published);

CREATE TABLE app_user (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  phone VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL
);

CREATE TABLE kaoshi_record (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  kaoshi_name VARCHAR(160) NOT NULL,
  candidate_name VARCHAR(80) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  score INT NOT NULL DEFAULT 0,
  total_score INT NOT NULL DEFAULT 100,
  pass_score INT NOT NULL DEFAULT 60,
  passed BOOLEAN NOT NULL DEFAULT FALSE,
  submitted_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL
);

CREATE INDEX idx_kaoshi_record_phone ON kaoshi_record(phone);
