package db.migration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** 仅使用外部创建的临时库，禁止将地址设置成业务库。 */
@EnabledIfEnvironmentVariable(named = "AIQ_RESET_TEST_URL", matches = ".*aiq_v61_verify_.*")
class ResetBusinessDataMigrationTest {
  @Test void rollsBackFailuresAndFlywayRunsOnlyOnce() throws Exception {
    String url = System.getenv("AIQ_RESET_TEST_URL");
    String password = System.getenv("AIQ_RESET_TEST_PASSWORD");
    try (var connection = DriverManager.getConnection(url, "root", password);
         var sql = connection.createStatement()) {
      sql.executeUpdate("INSERT INTO site_news(title,content,published_at,created_at,updated_at) VALUES('测试','测试',NOW(),NOW(),NOW())");
      sql.execute("RENAME TABLE training_video_upload TO training_video_upload_failure_test");
      var context = mock(Context.class);
      when(context.getConnection()).thenReturn(connection);
      assertThrows(SQLException.class, () -> new V61__reset_business_data_and_admin().migrate(context));
      assertEquals(1, count(sql, "site_news"));
      assertEquals(1, count(sql, "app_user"));
      assertTrue(connection.getAutoCommit());
      try (var rows = sql.executeQuery("SELECT @@FOREIGN_KEY_CHECKS")) { rows.next(); assertEquals(1, rows.getInt(1)); }
      sql.execute("RENAME TABLE training_video_upload_failure_test TO training_video_upload");
      var flyway = Flyway.configure().dataSource(url, "root", password).locations("classpath:db/migration").load();
      assertEquals(1, flyway.migrate().migrationsExecuted);
      assertEquals("61", flyway.info().current().getVersion().getVersion());
      assertEquals(0, count(sql, "site_news"));
      assertEquals(1, count(sql, "app_user"));
      try (var rows = sql.executeQuery("SELECT phone,password_hash FROM app_user")) {
        rows.next(); assertEquals("13800000000", rows.getString(1));
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("12345678", rows.getString(2)));
      }
      sql.executeUpdate("INSERT INTO site_news(title,content,published_at,created_at,updated_at) VALUES('迁移后新增','测试',NOW(),NOW(),NOW())");
      assertEquals(0, flyway.migrate().migrationsExecuted);
      assertEquals(1, count(sql, "site_news"));
    }
  }
  private int count(Statement sql, String table) throws SQLException {
    try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + table)) { rows.next(); return rows.getInt(1); }
  }
}
