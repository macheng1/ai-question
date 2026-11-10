CREATE TABLE organization_department (
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 id BIGINT PRIMARY KEY AUTO_INCREMENT, created_by BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', INDEX idx_department_owner(created_by)
);
CREATE TABLE organization_employee (
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 id BIGINT PRIMARY KEY AUTO_INCREMENT, created_by BIGINT NOT NULL, department_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL, phone VARCHAR(50) NOT NULL, job_title VARCHAR(100), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
 UNIQUE KEY employee_owner_phone(created_by,phone), INDEX idx_employee_department(department_id)
);
CREATE TABLE assessment_scope_target (
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 task_id BIGINT NOT NULL, target_id BIGINT NOT NULL, PRIMARY KEY(task_id,target_id),
 FOREIGN KEY(task_id) REFERENCES assessment_task(id) ON DELETE CASCADE
);
CREATE TABLE assessment_roster (
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 task_id BIGINT NOT NULL, employee_id BIGINT NOT NULL, department_id BIGINT NOT NULL,
 department_name VARCHAR(100) NOT NULL, candidate_name VARCHAR(100) NOT NULL, phone VARCHAR(50) NOT NULL,
 PRIMARY KEY(task_id,employee_id), UNIQUE KEY roster_phone(task_id,phone),
 FOREIGN KEY(task_id) REFERENCES assessment_task(id) ON DELETE CASCADE
);
