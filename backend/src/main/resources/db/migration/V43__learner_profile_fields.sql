-- 学员档案：工号沿用 V37 已有字段，内部/外部与数据来源分开维护。
ALTER TABLE organization_employee
  ADD COLUMN learner_type VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
  ADD COLUMN gender VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
  ADD COLUMN remark VARCHAR(1000) NULL;
