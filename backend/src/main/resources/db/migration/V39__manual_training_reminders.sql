ALTER TABLE training ADD COLUMN end_at DATETIME(3) NULL COMMENT '培训结束时间，独立于考试时间';
CREATE TABLE training_reminder_delivery (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 training_id BIGINT NOT NULL,
 employee_id BIGINT NOT NULL,
 request_id VARCHAR(36) NOT NULL,
 email VARCHAR(254) NOT NULL,
 status VARCHAR(16) NOT NULL,
 message VARCHAR(255) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 sent_at DATETIME(3) NULL,
 UNIQUE KEY uk_reminder_request(training_id,employee_id,request_id),
 KEY idx_reminder_history(training_id,employee_id,sent_at)
);
