package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Adds employee contact details and initializes credentials without changing management accounts. */
public class V36__employee_email_and_password extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    var connection = context.getConnection();
    try (var statement = connection.createStatement()) {
      statement.execute("ALTER TABLE organization_employee ADD COLUMN email VARCHAR(254) NULL, ADD COLUMN password_hash VARCHAR(255) NULL");
    }
    var encoder = new BCryptPasswordEncoder();
    try (var select = connection.prepareStatement("SELECT id FROM organization_employee WHERE password_hash IS NULL");
        var rows = select.executeQuery();
        var update = connection.prepareStatement("UPDATE organization_employee SET password_hash=? WHERE id=? AND password_hash IS NULL")) {
      while (rows.next()) {
        update.setString(1, encoder.encode("12345678"));
        update.setLong(2, rows.getLong(1));
        update.executeUpdate();
      }
    }
    try (var statement = connection.createStatement()) {
      statement.execute("ALTER TABLE organization_employee MODIFY COLUMN password_hash VARCHAR(255) NOT NULL");
    }
  }
}
