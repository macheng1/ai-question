package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for AuthService. SQL is defined in the matching mapper XML. */
@Mapper
public interface AuthRepository {
  List<Integer> countPhone(@Param("phone") String phone);
  List<Integer> countName(@Param("name") String name);
  int insertAdministrator(@Param("name") String name, @Param("unitName") String unitName, @Param("province") String province, @Param("city") String city, @Param("district") String district, @Param("address") String address, @Param("phone") String phone, @Param("passwordHash") String passwordHash, @Param("roleCode") String roleCode, @Param("status") String status, @Param("createdAt") java.time.LocalDateTime createdAt, @Param("updatedAt") java.time.LocalDateTime updatedAt);
  List<Long> findAdministratorIdByPhone(@Param("phone") String phone);
  List<Map<String, Object>> findActiveAdministratorProfile(@Param("userId") String userId);
  List<Integer> countConflictingNames(@Param("name") String name, @Param("userId") String userId);
  int updateAdministratorProfile(@Param("name") String name, @Param("unitName") String unitName, @Param("address") String address, @Param("updatedAt") java.time.LocalDateTime updatedAt, @Param("userId") String userId);
  List<String> findActivePasswordHash(@Param("userId") String userId);
  int updateAdministratorPassword(@Param("passwordHash") String passwordHash, @Param("updatedAt") java.time.LocalDateTime updatedAt, @Param("userId") String userId);
}
