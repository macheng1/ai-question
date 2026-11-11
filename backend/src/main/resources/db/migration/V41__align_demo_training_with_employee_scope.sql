-- 培训用途统一由关联考试决定，保留旧字段供兼容读取。
UPDATE training SET purpose=IF(assessment_id IS NULL,'STANDALONE','EXAM')
WHERE purpose<>IF(assessment_id IS NULL,'STANDALONE','EXAM');
ALTER TABLE training ALTER COLUMN audience_type SET DEFAULT 'USERS';

-- 只初始化默认演示企业；账号冲突时停止，不借用其他企业的员工。
CREATE TEMPORARY TABLE current_demo_owner(id BIGINT NOT NULL PRIMARY KEY);
INSERT INTO current_demo_owner VALUES ((SELECT id FROM app_user
WHERE phone='13800000000' AND name='默认企业管理员' AND unit_name='默认演示企业' AND status='ACTIVE'));
SET @owner = (SELECT id FROM current_demo_owner);
INSERT INTO organization_department(created_by,name,status)
SELECT @owner,'培训示范部','ACTIVE' WHERE NOT EXISTS (
SELECT 1 FROM organization_department WHERE created_by=@owner AND name='培训示范部');
CREATE TEMPORARY TABLE current_demo_department(id BIGINT NOT NULL PRIMARY KEY);
INSERT INTO current_demo_department VALUES ((SELECT id FROM organization_department
WHERE created_by=@owner AND name='培训示范部' AND status='ACTIVE'));
SET @department = (SELECT id FROM current_demo_department);
INSERT INTO organization_employee(created_by,department_id,name,phone,password_hash,job_title,status)
SELECT @owner,@department,'演示员工','13900000001',
'$2a$10$cQTudbrttVss2dmHaAK.beFCFLjHcalef6osNDdX2XDThuotMeh5e','培训专员','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM organization_employee WHERE phone='13900000001');
CREATE TEMPORARY TABLE current_demo_employee(id BIGINT NOT NULL PRIMARY KEY);
INSERT INTO current_demo_employee VALUES ((SELECT id FROM organization_employee
WHERE phone='13900000001' AND created_by=@owner AND department_id=@department
AND name='演示员工' AND status='ACTIVE'));
SET @employee = (SELECT id FROM current_demo_employee);

-- 只补尚未配置名单、尚未产生业务记录的原始演示培训，不覆盖管理员已配置的范围。
CREATE TEMPORARY TABLE current_demo_training(id BIGINT NOT NULL PRIMARY KEY,category VARCHAR(30) NOT NULL);
INSERT INTO current_demo_training
SELECT t.id,CASE t.name WHEN '新员工入职与职业规范培训' THEN 'ONBOARDING'
WHEN '员工信息安全与数据保护培训' THEN 'SAFETY' ELSE 'POLICY' END
FROM training t WHERE t.created_by=@owner AND t.audience_type='PUBLIC'
AND t.name IN ('新员工入职与职业规范培训','员工信息安全与数据保护培训','质量意识与异常问题闭环培训')
AND NOT EXISTS(SELECT 1 FROM training_audience a WHERE a.training_id=t.id)
AND NOT EXISTS(SELECT 1 FROM candidate_learning_session s WHERE s.training_id=t.id OR s.task_id=t.assessment_id)
AND NOT EXISTS(SELECT 1 FROM assessment_participant p WHERE p.task_id=t.assessment_id)
AND NOT EXISTS(SELECT 1 FROM training_survey_response r WHERE r.training_id=t.id);
UPDATE training t JOIN current_demo_training d ON d.id=t.id
JOIN assessment_task a ON a.id=t.assessment_id AND a.created_by=t.created_by
SET t.category=d.category,t.audience_type='USERS',t.start_at=COALESCE(t.start_at,a.start_at),
t.end_at=COALESCE(t.end_at,a.end_at);
INSERT INTO training_scope_target(training_id,target_id)
SELECT id,@employee FROM current_demo_training d WHERE NOT EXISTS(
SELECT 1 FROM training_scope_target s WHERE s.training_id=d.id AND s.target_id=@employee);
INSERT INTO training_audience(training_id,employee_id,candidate_name,phone,department_id,department_name,assigned_at)
SELECT t.id,e.id,e.name,e.phone,d.id,d.name,CURRENT_TIMESTAMP(3)
FROM current_demo_training t JOIN organization_employee e ON e.id=@employee
JOIN organization_department d ON d.id=e.department_id;
DROP TEMPORARY TABLE current_demo_training;
DROP TEMPORARY TABLE current_demo_employee;
DROP TEMPORARY TABLE current_demo_department;
DROP TEMPORARY TABLE current_demo_owner;
