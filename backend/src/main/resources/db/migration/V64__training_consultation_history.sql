CREATE TABLE training_consultation_turn (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    training_id BIGINT NULL,
    department_id BIGINT NULL,
    question VARCHAR(1000) NOT NULL,
    answer TEXT NOT NULL,
    topic VARCHAR(32) NOT NULL,
    tab_name VARCHAR(16) NULL,
    status_name VARCHAR(32) NULL,
    total INT NOT NULL,
    INDEX ix_consultation_owner_thread (owner_id, conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
