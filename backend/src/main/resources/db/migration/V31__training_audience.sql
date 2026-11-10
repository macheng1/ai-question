ALTER TABLE training ADD COLUMN audience_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
CREATE TABLE training_audience (
 training_id BIGINT NOT NULL, employee_id BIGINT NOT NULL, candidate_name VARCHAR(100) NOT NULL,
 phone VARCHAR(50) NOT NULL, department_id BIGINT NOT NULL, department_name VARCHAR(100) NOT NULL,
 PRIMARY KEY(training_id,employee_id), UNIQUE KEY audience_phone(training_id,phone),
 FOREIGN KEY(training_id) REFERENCES training(id) ON DELETE CASCADE
);
CREATE TABLE training_scope_target (
 training_id BIGINT NOT NULL,target_id BIGINT NOT NULL,PRIMARY KEY(training_id,target_id),
 FOREIGN KEY(training_id) REFERENCES training(id) ON DELETE CASCADE
);
