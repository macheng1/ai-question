package com.aiquestion.repository;

import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

/** Retains JDBC single-row cardinality and primitive getter semantics during map-based migration. */
public final class RepositoryRows {
  private RepositoryRows() {}
  public static <T> T one(List<T> rows) {
    if (rows.isEmpty()) throw new EmptyResultDataAccessException(1);
    if (rows.size() != 1) throw new IncorrectResultSizeDataAccessException(1, rows.size());
    return rows.get(0);
  }
  public static Long nullableLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }
  public static String string(Map<String, Object> row, String column) {
    Object value = row.get(column);
    return value == null ? null : value.toString();
  }
  public static long longValue(Map<String, Object> row, String column) {
    Object value = row.get(column);
    return value == null ? 0 : ((Number) value).longValue();
  }
  public static int intValue(Map<String, Object> row, String column) {
    Object value = row.get(column);
    return value == null ? 0 : ((Number) value).intValue();
  }
  public static boolean booleanValue(Map<String, Object> row, String column) {
    Object value = row.get(column);
    return value instanceof Boolean b ? b : value instanceof Number n ? n.intValue() != 0
        : value != null && ("true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString()));
  }
}
