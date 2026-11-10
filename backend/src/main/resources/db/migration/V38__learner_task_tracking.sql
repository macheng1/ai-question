ALTER TABLE training_audience ADD COLUMN assigned_at TIMESTAMP(3) NULL DEFAULT NULL;
ALTER TABLE candidate_learning_session
 ADD COLUMN employee_id BIGINT NULL,
 ADD COLUMN started_at TIMESTAMP(3) NULL DEFAULT CURRENT_TIMESTAMP(3),
 ADD COLUMN completed_at TIMESTAMP(3) NULL,
 ADD INDEX idx_learning_employee(employee_id,training_id,task_id);
ALTER TABLE assessment_participant ADD COLUMN employee_id BIGINT NULL,
 ADD INDEX idx_participant_employee(employee_id,task_id);

UPDATE candidate_learning_session s
 JOIN training t ON (t.id=s.training_id OR t.assessment_id=s.task_id)
 JOIN training_audience a ON a.training_id=t.id AND a.candidate_name=s.candidate_name AND a.phone=s.phone
 SET s.employee_id=a.employee_id;
UPDATE assessment_participant p JOIN training t ON t.assessment_id=p.task_id
 JOIN training_audience a ON a.training_id=t.id AND a.candidate_name=p.candidate_name AND a.phone=p.phone
 SET p.employee_id=a.employee_id;
-- Legacy sessions do not have a reliable start time; derive it only from recorded activity.
UPDATE candidate_learning_session s SET started_at=COALESCE(
 (SELECT MIN(v.first_studied_at) FROM candidate_video_progress v WHERE v.session_id=s.id),
 (SELECT MIN(p.first_seen) FROM candidate_material_progress p WHERE p.session_id=s.id),NULL);

UPDATE assessment_participant p JOIN assessment_task t ON t.id=p.task_id
 JOIN organization_employee e ON e.created_by=t.created_by AND e.name=p.candidate_name AND e.phone=p.phone
 SET p.employee_id=e.id WHERE p.employee_id IS NULL;
