package com.aiquestion.config;

import java.util.Arrays;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅兼容 dev 反合带入的两条低版本初始化迁移，不全局允许乱序升级。 */
@Configuration
public class SeedMigrationConfiguration {
  private static final Set<String> BACKFILLED_VERSIONS = Set.of("27.1", "27.2");

  @Bean
  public FlywayMigrationStrategy seedMigrationStrategy() {
    return flyway -> {
      var ignored = Arrays.stream(flyway.info().all())
          .filter(migration -> migration.getState() == MigrationState.IGNORED)
          .toList();
      if (ignored.isEmpty()) {
        flyway.migrate();
        return;
      }
      boolean knownSeedsOnly = ignored.stream().allMatch(migration ->
          migration.getVersion() != null
              && BACKFILLED_VERSIONS.contains(migration.getVersion().getVersion()));
      if (!knownSeedsOnly) {
        throw new IllegalStateException("发现未执行的历史迁移，请核对版本；只允许自动补执行 27.1、27.2");
      }
      Flyway.configure().configuration(flyway.getConfiguration()).outOfOrder(true).load().migrate();
    };
  }
}
