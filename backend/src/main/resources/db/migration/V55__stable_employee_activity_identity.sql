-- A historical assignment snapshot can identify an unbound row only if it already
-- existed at the time of entry. Unknown dates/owners stay unclaimed. Never match a
-- historical phone to today's employee phone or replace an existing employee ID.
UPDATE candidate_learning_session s
JOIN training t ON t.id=s.training_id OR (s.training_id IS NULL AND t.assessment_id=s.task_id)
JOIN training_audience a ON a.training_id=t.id AND a.candidate_name=s.candidate_name AND a.phone=s.phone
JOIN organization_employee e ON e.id=a.employee_id AND e.created_by=t.created_by
SET s.employee_id=a.employee_id
WHERE s.employee_id IS NULL AND a.assigned_at<=s.started_at AND e.created_at<=s.started_at;

UPDATE assessment_participant p
JOIN assessment_task task ON task.id=p.task_id
JOIN training t ON t.assessment_id=task.id AND t.created_by=task.created_by
JOIN training_audience a ON a.training_id=t.id AND a.candidate_name=p.candidate_name AND a.phone=p.phone
JOIN organization_employee e ON e.id=a.employee_id AND e.created_by=t.created_by
SET p.employee_id=a.employee_id
WHERE p.employee_id IS NULL AND a.assigned_at<=p.created_at AND e.created_at<=p.created_at;

UPDATE assessment_participant p
JOIN assessment_task task ON task.id=p.task_id
JOIN assessment_roster r ON r.task_id=task.id AND r.candidate_name=p.candidate_name AND r.phone=p.phone
JOIN organization_employee e ON e.id=r.employee_id AND e.created_by=task.created_by
SET p.employee_id=r.employee_id
WHERE p.employee_id IS NULL AND r.created_at<=p.created_at AND e.created_at<=p.created_at
AND NOT EXISTS(SELECT 1 FROM training t WHERE t.assessment_id=task.id);

-- Old versions could open a second session for the same employee after a profile
-- edit. Keep the newest entry token (used by learner records), all earned study
-- time, the furthest playback position and the earliest start/completion dates.
CREATE TEMPORARY TABLE v55_sessions (
 id BIGINT PRIMARY KEY, keep_id BIGINT NOT NULL, INDEX(keep_id)
);
INSERT INTO v55_sessions
SELECT s.id,d.keep_id FROM candidate_learning_session s
JOIN (SELECT task_id,training_id,employee_id,MAX(id) keep_id
      FROM candidate_learning_session WHERE employee_id IS NOT NULL
      GROUP BY task_id,training_id,employee_id HAVING COUNT(*)>1) d
ON d.employee_id=s.employee_id AND d.task_id<=>s.task_id AND d.training_id<=>s.training_id;

CREATE TEMPORARY TABLE v55_dates AS
SELECT d.keep_id,MIN(CAST(s.started_at AS DATETIME(3))) started_at,MIN(CAST(s.completed_at AS DATETIME(3))) completed_at
FROM v55_sessions d JOIN candidate_learning_session s ON s.id=d.id GROUP BY d.keep_id;

CREATE TEMPORARY TABLE v55_videos AS
SELECT d.keep_id,p.video_id,MAX(p.position_seconds) position_seconds,SUM(p.studied_seconds) studied_seconds,
 MAX(p.completed) completed,MIN(CAST(p.first_studied_at AS DATETIME(3))) first_studied_at,MIN(CAST(p.completed_at AS DATETIME(3))) completed_at,
 MAX(CAST(p.updated_at AS DATETIME(3))) updated_at
FROM v55_sessions d JOIN candidate_video_progress p ON p.session_id=d.id GROUP BY d.keep_id,p.video_id;

CREATE TEMPORARY TABLE v55_pages AS
SELECT d.keep_id,p.material_id,p.page_number,SUM(p.studied_seconds) studied_seconds,
 MAX(p.completed) completed,MIN(CAST(p.first_seen AS DATETIME(3))) first_seen,MAX(CAST(p.last_seen AS DATETIME(3))) last_seen,MIN(CAST(p.completed_at AS DATETIME(3))) completed_at
FROM v55_sessions d JOIN candidate_material_progress p ON p.session_id=d.id GROUP BY d.keep_id,p.material_id,p.page_number;

START TRANSACTION;
UPDATE candidate_learning_session s JOIN v55_dates d ON s.id=d.keep_id
SET s.started_at=d.started_at,s.completed_at=d.completed_at;
DELETE p FROM candidate_video_progress p JOIN v55_sessions d ON d.id=p.session_id;
INSERT INTO candidate_video_progress(session_id,video_id,position_seconds,studied_seconds,completed,
 first_studied_at,completed_at,updated_at,baseline_position,last_play_position)
SELECT keep_id,video_id,position_seconds,studied_seconds,completed,first_studied_at,completed_at,
 updated_at,position_seconds,position_seconds FROM v55_videos;
DELETE p FROM candidate_material_progress p JOIN v55_sessions d ON d.id=p.session_id;
INSERT INTO candidate_material_progress(session_id,material_id,page_number,studied_seconds,completed,first_seen,last_seen,completed_at)
SELECT p.keep_id,p.material_id,p.page_number,p.studied_seconds,
 (p.completed OR p.studied_seconds>=m.required_seconds),p.first_seen,p.last_seen,p.completed_at
FROM v55_pages p JOIN training_material_page m ON m.material_id=p.material_id AND m.page_number=p.page_number;
DELETE s FROM candidate_learning_session s JOIN v55_sessions d ON s.id=d.id WHERE s.id<>d.keep_id;
COMMIT;
DROP TEMPORARY TABLE v55_sessions,v55_dates,v55_videos,v55_pages;

ALTER TABLE candidate_learning_session
 ADD UNIQUE KEY uk_learning_task_employee(task_id,employee_id),
 ADD UNIQUE KEY uk_learning_training_employee(training_id,employee_id),
 DROP INDEX candidate_task, DROP INDEX candidate_training;
ALTER TABLE training_audience DROP INDEX audience_phone;
ALTER TABLE assessment_roster DROP INDEX roster_phone;

-- Both tables contain transient concurrency state. Existing progress/attempts
-- are stored separately. First entry creates the employee's new mutex/clock.
DELETE FROM assessment_candidate_guard;
ALTER TABLE assessment_candidate_guard
 ADD COLUMN employee_id BIGINT NOT NULL, DROP PRIMARY KEY, ADD PRIMARY KEY(task_id,employee_id),
 DROP COLUMN candidate_name, DROP COLUMN phone;
DELETE FROM learning_clock;
ALTER TABLE learning_clock
 ADD COLUMN employee_id BIGINT NOT NULL, DROP PRIMARY KEY, ADD PRIMARY KEY(employee_id), DROP COLUMN phone;
