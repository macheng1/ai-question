-- 默认企业演示初始化，由 Flyway 管理执行记录。
-- 企业：默认演示企业；管理员：默认企业管理员；手机号：13800000000；初始密码：12345678。
-- 与正常企业注册保持一致，以 app_user.id 作为企业数据归属标识。
-- 同手机号或同名账号已存在时跳过，不重置密码、不提升权限、不覆盖已有资料。
INSERT INTO app_user
  (name, unit_name, phone, password_hash, role_code, status, created_at, updated_at)
SELECT
  '默认企业管理员', '默认演示企业', '13800000000',
  '$2a$10$cQTudbrttVss2dmHaAK.beFCFLjHcalef6osNDdX2XDThuotMeh5e', 'CANDIDATE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (
  SELECT 1 FROM app_user WHERE phone = '13800000000' OR name = '默认企业管理员'
);
