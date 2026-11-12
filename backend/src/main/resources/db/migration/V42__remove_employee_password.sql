-- 员工通过培训/考试入口确认姓名、手机号，不再保存密码。管理员密码不受影响。
ALTER TABLE organization_employee DROP COLUMN password_hash;
