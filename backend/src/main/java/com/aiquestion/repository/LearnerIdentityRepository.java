package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for LearnerIdentityService. SQL is defined in the matching mapper XML. */
@Mapper
public interface LearnerIdentityRepository {
  List<Long> findEntryOwner(@Param("entryId") Long entryId, @Param("training") boolean training);
  List<Long> findActiveEmployee(@Param("enterpriseId") Long enterpriseId, @Param("name") String name, @Param("phone") String phone);
  List<Integer> countOwnedAttempt(@Param("attemptId") Long attemptId, @Param("employeeId") Long employeeId, @Param("enterpriseId") Long enterpriseId);
  List<Long> findSessionEmployee(@Param("token") String token);
  List<Integer> countEmployeeSession(@Param("token") String token, @Param("employeeId") Long employeeId);
}
