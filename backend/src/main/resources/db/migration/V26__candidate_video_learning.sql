CREATE TABLE candidate_learning_session (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 task_id BIGINT NOT NULL,
 candidate_name VARCHAR(100) NOT NULL,
 phone VARCHAR(50) NOT NULL,
 token CHAR(36) NOT NULL UNIQUE,
 UNIQUE KEY candidate_task(task_id,candidate_name,phone),
 FOREIGN KEY(task_id) REFERENCES assessment_task(id) ON DELETE CASCADE
);
CREATE TABLE candidate_video_progress (
 session_id BIGINT NOT NULL,
 video_id BIGINT NOT NULL,
 position_seconds DOUBLE NOT NULL DEFAULT 0,
 completed BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(session_id,video_id),
 FOREIGN KEY(session_id) REFERENCES candidate_learning_session(id) ON DELETE CASCADE,
 FOREIGN KEY(video_id) REFERENCES training_video(id) ON DELETE CASCADE
);
