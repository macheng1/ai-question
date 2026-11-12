package com.aiquestion.repository;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence operations for OrganizationService. SQL is defined in the matching mapper XML. */
@Mapper
public interface OrganizationRepository {
  List<Map<String, Object>> findDepartmentsWithEmployeeCounts(@Param("ownerId") Long ownerId);
  List<Map<String, Object>> findEmployees(@Param("ownerId") Long ownerId);
  List<Integer> countOwnedDepartments(
      @Param("departmentId") Long departmentId,
      @Param("ownerId") Long ownerId,
      @Param("activeOnly") boolean activeOnly);
  List<String> lockDepartmentStatus(@Param("departmentId") Long departmentId, @Param("ownerId") Long ownerId);
  int insertDepartment(
      @Param("ownerId") Long ownerId,
      @Param("name") String name,
      @Param("status") String status,
      @Param("generated") GeneratedId generated);
  int updateDepartment(
      @Param("name") String name,
      @Param("status") String status,
      @Param("departmentId") Long departmentId,
      @Param("ownerId") Long ownerId);
  List<Map<String, Object>> findEmployeeDepartment(@Param("employeeId") Long employeeId, @Param("ownerId") Long ownerId);
  List<Integer> countOwnedEmployees(@Param("employeeId") Long employeeId, @Param("ownerId") Long ownerId);
  List<Integer> countPhoneConflicts(@Param("phone") String phone, @Param("excludedEmployeeId") Long excludedEmployeeId);
  int insertEmployee(
      @Param("ownerId") Long ownerId,
      @Param("departmentId") Long departmentId,
      @Param("name") String name,
      @Param("phone") String phone,
      @Param("email") String email,
      @Param("jobTitle") String jobTitle,
      @Param("status") String status,
      @Param("learnerType") String learnerType,
      @Param("gender") String gender,
      @Param("employeeNo") String employeeNo,
      @Param("remark") String remark,
      @Param("generated") GeneratedId generated);
  int updateEmployee(
      @Param("departmentId") Long departmentId,
      @Param("name") String name,
      @Param("phone") String phone,
      @Param("email") String email,
      @Param("jobTitle") String jobTitle,
      @Param("status") String status,
      @Param("learnerType") String learnerType,
      @Param("gender") String gender,
      @Param("employeeNo") String employeeNo,
      @Param("remark") String remark,
      @Param("employeeId") Long employeeId,
      @Param("ownerId") Long ownerId);
}
