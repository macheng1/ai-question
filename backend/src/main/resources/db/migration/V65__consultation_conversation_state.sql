CREATE TABLE training_consultation_state (
    owner_id BIGINT NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (owner_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
