-- 初始化默认管理员（项目中管理员账号存储在 app_user，暂无单独的 SUPER_ADMIN 角色）。
-- 先选择目标数据库；表结构须已创建。账号：13800000000，初始密码：12345678。
-- 同手机号账号已存在时不修改账号信息或密码；首次登录后请修改密码。
SET NAMES utf8mb4;
INSERT INTO app_user (name, unit_name, phone, password_hash, role_code, status, created_at, updated_at)
SELECT '默认企业管理员', '默认演示企业', '13800000000',
       '$2a$10$cQTudbrttVss2dmHaAK.beFCFLjHcalef6osNDdX2XDThuotMeh5e',
       'CANDIDATE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE phone = '13800000000');
