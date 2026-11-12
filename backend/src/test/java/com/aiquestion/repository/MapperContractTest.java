package com.aiquestion.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Architectural contract: runtime persistence is named MyBatis statements, never arbitrary SQL. */
class MapperContractTest {
  @Test
  void allXmlStatementsAndRepositoryMethodsAreBound() throws Exception {
    var configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml");
    assertTrue(resources.length > 0);
    for (var resource : resources) {
      try (var stream = resource.getInputStream()) {
        new XMLMapperBuilder(stream, configuration, resource.toString(), configuration.getSqlFragments()).parse();
      }
    }
    configuration.getMappedStatementNames(); // Force deferred references/result maps to resolve.
    for (var mapper : configuration.getMapperRegistry().getMappers()) {
      for (var method : mapper.getMethods()) {
        if (!Modifier.isAbstract(method.getModifiers())) continue;
        assertTrue(configuration.hasStatement(mapper.getName() + "." + method.getName()),
            () -> "Missing XML statement: " + mapper.getSimpleName() + "." + method.getName());
      }
    }
  }

  @Test
  void runtimeDoesNotExecuteSqlOutsideMapperXml() throws Exception {
    var violations = new ArrayList<String>();
    try (var paths = Files.walk(Path.of("src/main/java/com/aiquestion"))) {
      for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        var source = Files.readString(path);
        if (source.contains("JdbcTemplate") || source.contains("NamedParameterJdbcTemplate")
            || source.matches("(?s).*@(Select|Insert|Update|Delete|SelectProvider|InsertProvider|UpdateProvider|DeleteProvider)\\s*\\(.*")
            || source.contains("org.apache.ibatis.annotations.Select(")) violations.add(path.toString());
      }
    }
    assertEquals(java.util.List.of(), violations, "Runtime SQL must live in mapper XML (Flyway migrations are excluded)");
  }
}
