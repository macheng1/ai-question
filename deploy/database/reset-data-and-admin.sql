-- 清空业务数据并重新初始化默认管理员（MySQL 8，结构须已升级到 V60）。
-- 执行前停止应用和后台任务，选择正确的目标数据库；不要使用 mysql --force。
-- 删除全部业务数据和原有账号；默认管理员密码也将重置为 12345678。
-- 保留表结构和 flyway_schema_history；不删除 OSS 文件及服务器文件。
-- 需要 CREATE ROUTINE / EXECUTE / ALTER ROUTINE 权限。
SET NAMES utf8mb4;

DELIMITER $$
CREATE PROCEDURE reset_business_data_and_admin_v60()
SQL SECURITY INVOKER
BEGIN
  DECLARE previous_fk_checks INT DEFAULT 1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    SET FOREIGN_KEY_CHECKS = previous_fk_checks;
    RESIGNAL;
  END;

  SET previous_fk_checks = @@FOREIGN_KEY_CHECKS;
  START TRANSACTION;
  SET FOREIGN_KEY_CHECKS = 0;

  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='training_consultation_turn') THEN
    DELETE FROM `training_consultation_turn`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='training_consultation_state') THEN
    DELETE FROM `training_consultation_state`;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='admin_ai_settings') THEN
    DELETE FROM `admin_ai_settings`;
  END IF;

  DELETE FROM `app_user`;
  DELETE FROM `assessment_answer`;
  DELETE FROM `assessment_attempt`;
  DELETE FROM `assessment_attempt_question`;
  DELETE FROM `assessment_participant`;
  DELETE FROM `assessment_roster`;
  DELETE FROM `assessment_scope_target`;
  DELETE FROM `assessment_task`;
  DELETE FROM `candidate_learning_session`;
  DELETE FROM `candidate_material_progress`;
  DELETE FROM `candidate_video_progress`;
  DELETE FROM `file_object`;
  DELETE FROM `kaoshi_record`;
  DELETE FROM `learner_paper_binding`;
  DELETE FROM `learning_clock`;
  DELETE FROM `operation_log`;
  DELETE FROM `organization_department`;
  DELETE FROM `organization_employee`;
  DELETE FROM `organization_external_identity`;
  DELETE FROM `public_self_test`;
  DELETE FROM `question_bank`;
  DELETE FROM `question_bank_category`;
  DELETE FROM `question_item`;
  DELETE FROM `question_option`;
  DELETE FROM `site_news`;
  DELETE FROM `test_paper`;
  DELETE FROM `test_paper_question`;
  DELETE FROM `test_paper_random_bank`;
  DELETE FROM `training`;
  DELETE FROM `training_allocation_lock`;
  DELETE FROM `training_audience`;
  DELETE FROM `training_material`;
  DELETE FROM `training_material_page`;
  DELETE FROM `training_reminder_delivery`;
  DELETE FROM `training_scope_target`;
  DELETE FROM `training_survey_response`;
  DELETE FROM `training_video`;
  DELETE FROM `training_video_progress`;
  DELETE FROM `user_question_bank_favorite`;
  DELETE FROM `assessment_candidate_guard`;
  DELETE FROM `training_material_upload`;
  DELETE FROM `training_video_upload`;

  INSERT INTO app_user
    (id, name, unit_name, phone, password_hash, role_code, status, created_at, updated_at)
  VALUES
    (1, '默认企业管理员', '默认演示企业', '13800000000',
     '$2a$10$cQTudbrttVss2dmHaAK.beFCFLjHcalef6osNDdX2XDThuotMeh5e',
     'CANDIDATE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

  SET FOREIGN_KEY_CHECKS = previous_fk_checks;
  COMMIT;
END$$
DELIMITER ;

CALL reset_business_data_and_admin_v60();
DROP PROCEDURE reset_business_data_and_admin_v60;

SELECT id, name, phone, status FROM app_user;
-- 默认账号：13800000000；密码：12345678。首次登录后修改。
-- 执行失败会回滚删除和管理员初始化；排查后需先删除遗留的同名过程，再重新执行本文件。
