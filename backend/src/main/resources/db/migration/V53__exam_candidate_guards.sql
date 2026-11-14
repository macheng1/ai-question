-- Per-candidate transaction guard, including first entry before a participant exists.
-- Preserve historical participant rows rather than merging/deleting duplicates.
CREATE TABLE assessment_candidate_guard (
 task_id BIGINT NOT NULL,
 candidate_name VARCHAR(80) NOT NULL,
 phone VARCHAR(20) NOT NULL,
 PRIMARY KEY(task_id,candidate_name,phone),
 CONSTRAINT fk_exam_guard_task FOREIGN KEY(task_id) REFERENCES assessment_task(id) ON DELETE CASCADE
);
