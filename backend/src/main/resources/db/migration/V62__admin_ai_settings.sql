CREATE TABLE admin_ai_settings (
    owner_id BIGINT NOT NULL PRIMARY KEY,
    model_id VARCHAR(128) NOT NULL,
    encrypted_api_key VARBINARY(4096) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
