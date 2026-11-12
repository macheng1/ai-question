package com.aiquestion.repository;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
/** Batch dashboard reads retain stable employee identities and latest-attempt semantics. */
@Mapper
public interface DashboardRepository {
  List<Map<String,Object>> trainings(@Param("owner") long owner, @Param("filters") Map<String,Object> filters);
  List<Map<String,Object>> rows(@Param("owner") long owner, @Param("filters") Map<String,Object> filters);
  List<Map<String,Object>> trainingOptions(@Param("owner") long owner);
  List<Map<String,Object>> departmentOptions(@Param("owner") long owner);
  default Map<String,Object> options(long owner) {
    return Map.of("trainings", trainingOptions(owner), "departments", departmentOptions(owner));
  }
}
