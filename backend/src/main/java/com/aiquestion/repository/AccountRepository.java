package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for AccountService. SQL is defined in the matching mapper XML. */
@Mapper
public interface AccountRepository {
  List<Map<String, Object>> findActiveAdministratorsByPhone(@Param("phone") String phone);
  List<Integer> countActiveAdministrator(@Param("accountId") Long accountId);
  List<Map<String, Object>> findActiveEmployeeProfile(@Param("employeeId") Long employeeId);
}
