package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** V61：一次性清空业务数据及账号，并重建默认管理员。保留 Flyway 历史。 */
public class V61__reset_business_data_and_admin extends BaseJavaMigration {
  @Override
  public boolean canExecuteInTransaction() {
    // MySQL 迁移由本类管理事务，以确保异常时回滚并恢复连接状态。
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    String mode = context.getConfiguration().getPlaceholders().getOrDefault("v61DataMode", "reset");
    if ("preserve".equals(mode)) {
      org.slf4j.LoggerFactory.getLogger(getClass()).info("V61 保留现有业务数据和账号，继续后续迁移");
      return;
    }
    if (!"reset".equals(mode)) {
      throw new IllegalArgumentException("V61_DATA_MODE 仅支持 preserve 或 reset，已停止迁移");
    }
    var connection = context.getConnection();
    boolean autoCommit = connection.getAutoCommit();
    int foreignKeyChecks;
    try (var statement = connection.createStatement();
         var rows = statement.executeQuery("SELECT @@FOREIGN_KEY_CHECKS")) {
      rows.next();
      foreignKeyChecks = rows.getInt(1);
    }
    connection.setAutoCommit(false);
    try (var statement = connection.createStatement()) {
      statement.execute("SET FOREIGN_KEY_CHECKS = 0");
      for (String table : new String[] {
          "app_user",
          "assessment_answer",
          "assessment_attempt",
          "assessment_attempt_question",
          "assessment_participant",
          "assessment_roster",
          "assessment_scope_target",
          "assessment_task",
          "candidate_learning_session",
          "candidate_material_progress",
          "candidate_video_progress",
          "file_object",
          "kaoshi_record",
          "learner_paper_binding",
          "learning_clock",
          "operation_log",
          "organization_department",
          "organization_employee",
          "organization_external_identity",
          "public_self_test",
          "question_bank",
          "question_bank_category",
          "question_item",
          "question_option",
          "site_news",
          "test_paper",
          "test_paper_question",
          "test_paper_random_bank",
          "training",
          "training_allocation_lock",
          "training_audience",
          "training_material",
          "training_material_page",
          "training_reminder_delivery",
          "training_scope_target",
          "training_survey_response",
          "training_video",
          "training_video_progress",
          "user_question_bank_favorite",
          "assessment_candidate_guard",
          "training_material_upload",
          "training_video_upload"
      }) {
        statement.executeUpdate("DELETE FROM `" + table + "`");
      }
      statement.executeUpdate("""
INSERT INTO app_user
    (id, name, unit_name, phone, password_hash, role_code, status, created_at, updated_at)
  VALUES
    (1, '默认企业管理员', '默认演示企业', '13800000000',
     '$2a$10$cQTudbrttVss2dmHaAK.beFCFLjHcalef6osNDdX2XDThuotMeh5e',
     'CANDIDATE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));
          """);
      connection.commit();
    } catch (Exception error) {
      try { connection.rollback(); } catch (Exception rollbackError) { error.addSuppressed(rollbackError); }
      throw error;
    } finally {
      try (var statement = connection.createStatement()) {
        statement.execute("SET FOREIGN_KEY_CHECKS = " + foreignKeyChecks);
      } finally {
        connection.setAutoCommit(autoCommit);
      }
    }
  }
}
