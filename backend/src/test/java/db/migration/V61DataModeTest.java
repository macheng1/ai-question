package db.migration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

class V61DataModeTest {
  private Context context(String mode) {
    var context = mock(Context.class);
    when(context.getConfiguration()).thenReturn(
        Flyway.configure().placeholders(Map.of("v61DataMode", mode)));
    return context;
  }

  @Test void preserveSkipsAllDatabaseWrites() throws Exception {
    var context = context("preserve");
    new V61__reset_business_data_and_admin().migrate(context);
    verify(context, never()).getConnection();
  }

  @Test void unknownSettingStopsBeforeAccessingTheDatabase() {
    var context = context("presrve");
    assertThrows(IllegalArgumentException.class,
        () -> new V61__reset_business_data_and_admin().migrate(context));
    verify(context, never()).getConnection();
  }
}
