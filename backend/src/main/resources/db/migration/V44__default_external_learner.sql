-- 新增学员默认外部，不修改已有学员类型。
ALTER TABLE organization_employee ALTER COLUMN learner_type SET DEFAULT 'EXTERNAL';
