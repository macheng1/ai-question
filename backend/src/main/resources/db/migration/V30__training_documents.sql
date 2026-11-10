CREATE TABLE training_material (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, training_id BIGINT NOT NULL,
 title VARCHAR(255) NOT NULL, material_type VARCHAR(10) NOT NULL,
 storage_key VARCHAR(255) NOT NULL, page_count INT NOT NULL,
 sort_order INT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(training_id) REFERENCES training(id) ON DELETE CASCADE
);
CREATE TABLE training_material_page (
 material_id BIGINT NOT NULL, page_number INT NOT NULL, required_seconds INT NOT NULL DEFAULT 120,
 PRIMARY KEY(material_id,page_number), FOREIGN KEY(material_id) REFERENCES training_material(id) ON DELETE CASCADE
);
CREATE TABLE candidate_material_progress (
 session_id BIGINT NOT NULL, material_id BIGINT NOT NULL, page_number INT NOT NULL,
 studied_seconds DOUBLE NOT NULL DEFAULT 0, completed BOOLEAN NOT NULL DEFAULT FALSE,
 last_seen TIMESTAMP(3) NULL, first_seen TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 completed_at TIMESTAMP(3) NULL, heartbeat_token CHAR(36) NULL,
 PRIMARY KEY(session_id,material_id,page_number),
 FOREIGN KEY(session_id) REFERENCES candidate_learning_session(id) ON DELETE CASCADE,
 FOREIGN KEY(material_id,page_number) REFERENCES training_material_page(material_id,page_number) ON DELETE CASCADE
);
