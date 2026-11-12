package com.aiquestion.repository;

import java.util.*;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for RequiredTrainingAssignmentService. SQL is defined in the matching mapper XML. */
@Mapper
public interface RequiredTrainingAssignmentRepository {
  List<Long> findActiveDepartmentEmployeeIds(@Param("departmentId") Long departmentId, @Param("ownerId") Long ownerId);
  List<Map<String, Object>> findActiveEmployee(@Param("employeeId") Long employeeId, @Param("ownerId") Long ownerId);
  List<Map<String, Object>> findMatchingTrainings(@Param("ownerId") Long ownerId, @Param("departmentId") Long departmentId);
  List<Map<String, Object>> lockMatchingTraining(@Param("trainingId") Long trainingId, @Param("ownerId") Long ownerId, @Param("departmentId") Long departmentId);
  int insertAudienceIfAbsent(@Param("trainingId") Long trainingId, @Param("employeeId") Long employeeId, @Param("candidateName") String candidateName, @Param("phone") String phone, @Param("departmentId") Long departmentId, @Param("departmentName") String departmentName, @Param("existingTrainingId") Long existingTrainingId, @Param("existingEmployeeId") Long existingEmployeeId);
}
