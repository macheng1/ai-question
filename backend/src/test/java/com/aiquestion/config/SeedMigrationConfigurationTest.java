package com.aiquestion.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class SeedMigrationConfigurationTest {
  @Test
  void normalMigrationUsesOriginalConfiguration() {
    Flyway flyway = mock(Flyway.class);
    MigrationInfoService info = mock(MigrationInfoService.class);
    when(flyway.info()).thenReturn(info);
    when(info.all()).thenReturn(new MigrationInfo[0]);
    new SeedMigrationConfiguration().seedMigrationStrategy().migrate(flyway);
    verify(flyway).migrate();
  }

  @Test
  void unrelatedHistoricalMigrationIsNotSilentlyExecuted() {
    Flyway flyway = mock(Flyway.class);
    MigrationInfoService info = mock(MigrationInfoService.class);
    MigrationInfo migration = mock(MigrationInfo.class);
    when(flyway.info()).thenReturn(info);
    when(info.all()).thenReturn(new MigrationInfo[]{migration});
    when(migration.getState()).thenReturn(MigrationState.IGNORED);
    when(migration.getVersion()).thenReturn(MigrationVersion.fromVersion("26.1"));
    assertThrows(IllegalStateException.class,
        () -> new SeedMigrationConfiguration().seedMigrationStrategy().migrate(flyway));
    verify(flyway, never()).migrate();
  }
}
