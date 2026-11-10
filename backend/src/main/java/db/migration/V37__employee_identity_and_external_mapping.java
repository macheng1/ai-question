package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Refuse ambiguous employee identities instead of merging unrelated learning histories. */
public class V37__employee_identity_and_external_mapping extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (var statement = context.getConnection().createStatement()) {
      try (var duplicates = statement.executeQuery("SELECT phone FROM organization_employee GROUP BY phone HAVING COUNT(*)>1 LIMIT 1")) {
        if (duplicates.next()) throw new IllegalStateException("员工手机号存在重复，请人工处理后重新迁移");
      }
      statement.execute("ALTER TABLE organization_employee ADD UNIQUE KEY uk_employee_phone(phone), ADD COLUMN employee_no VARCHAR(100) NULL, ADD COLUMN data_source VARCHAR(30) NOT NULL DEFAULT 'MANUAL'");
      statement.execute("""
          CREATE TABLE organization_external_identity (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            enterprise_id BIGINT NOT NULL,
            source_system VARCHAR(50) NOT NULL,
            entity_type VARCHAR(20) NOT NULL,
            external_id VARCHAR(191) NOT NULL,
            local_id BIGINT NOT NULL,
            external_updated_at DATETIME(3) NULL,
            last_synced_at DATETIME(3) NULL,
            UNIQUE KEY uk_external_identity(enterprise_id,source_system,entity_type,external_id),
            UNIQUE KEY uk_external_local(enterprise_id,source_system,entity_type,local_id)
          )
          """);
      statement.execute("ALTER TABLE training_survey_response MODIFY user_id BIGINT NULL, ADD COLUMN employee_id BIGINT NULL, ADD UNIQUE KEY uk_training_employee(training_id,employee_id)");
    }
  }
}
