-- 用户收藏公开题库。
CREATE TABLE user_question_bank_favorite (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  bank_id BIGINT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_favorite_bank FOREIGN KEY (bank_id) REFERENCES question_bank(id) ON DELETE CASCADE,
  UNIQUE KEY uk_favorite_user_bank (user_id, bank_id)
);

CREATE INDEX idx_favorite_user ON user_question_bank_favorite(user_id);
