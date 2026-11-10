package com.aiquestion.service;

import com.aiquestion.common.*;
import com.aiquestion.security.ManagementAccess;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {
  private final JdbcTemplate jdbc;

  private final org.springframework.security.crypto.password.PasswordEncoder encoder;

  public OrganizationService(JdbcTemplate jdbc, org.springframework.security.crypto.password.PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.encoder = encoder;
  }

  public List<Map<String, Object>> departments() {
    return jdbc.queryForList(
        "SELECT d.id,d.name,d.status,(SELECT COUNT(*) FROM organization_employee e WHERE"
            + " e.department_id=d.id) employeeCount FROM"
            + " organization_department d WHERE d.created_by=? ORDER BY d.id",
        ManagementAccess.userId());
  }

  public List<Map<String, Object>> employees() {
    return jdbc.queryForList(
        "SELECT e.id,e.name,e.phone,e.email,e.employee_no employeeNo,e.data_source dataSource,e.job_title jobTitle,e.department_id departmentId,d.name"
            + " departmentName,e.status FROM organization_employee e JOIN organization_department d"
            + " ON d.id=e.department_id WHERE e.created_by=? ORDER BY e.id",
        ManagementAccess.userId());
  }

  static void validateStatus(String status) {
    if (!Set.of("ACTIVE", "INACTIVE").contains(status == null ? "" : status))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "状态不合法");
  }

  private static String name(String name) {
    if (name == null || name.isBlank() || name.trim().length() > 100)
      throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写有效名称");
    return name.trim();
  }

  public void requireDepartment(long id, boolean active) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM organization_department WHERE id=? AND created_by=?"
                + (active ? " AND status='ACTIVE'" : ""),
            Integer.class,
            id,
            ManagementAccess.userId());
    if (count == null || count == 0)
      throw new BusinessException(ErrorCode.BAD_REQUEST, "部门不存在或不可用");
  }

  @Transactional
  public Map<String, Object> saveDepartment(Long id, String name, String status) {
    validateStatus(status);
    String clean = name(name);
    long owner = ManagementAccess.userId();
    if (id == null) {
      jdbc.update(
          "INSERT INTO organization_department(created_by,name,status) VALUES(?,?,?)",
          owner,
          clean,
          status);
      id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    } else {
      requireDepartment(id, false);
      jdbc.update(
          "UPDATE organization_department SET name=?,status=? WHERE id=? AND created_by=?",
          clean,
          status,
          id,
          owner);
    }
    final long saved = id;
    return departments().stream()
        .filter(r -> ((Number) r.get("id")).longValue() == saved)
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public Map<String, Object> saveEmployee(
      Long id, String name, String phone, String email, String jobTitle, Long departmentId, String status) {
    validateStatus(status);
    CandidateLearningService.validate(name, phone);
    String cleanEmail = email == null || email.isBlank() ? null : email.trim();
    if (cleanEmail != null && (cleanEmail.length() > 254
        || !cleanEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写有效邮箱");
    if (departmentId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择部门");
    long owner = ManagementAccess.userId();
    boolean requireActive = true;
    if (id != null) {
      var current =
          jdbc.queryForList(
              "SELECT department_id,status FROM organization_employee WHERE id=? AND created_by=?",
              id,
              owner);
      if (current.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
      requireActive =
          ((Number) current.get(0).get("department_id")).longValue() != departmentId
              || ("ACTIVE".equals(status) && !"ACTIVE".equals(current.get(0).get("status")));
    }
    requireDepartment(departmentId, requireActive);
    if (jobTitle != null && jobTitle.length() > 100)
      throw new BusinessException(ErrorCode.BAD_REQUEST, "岗位名称过长");
    if (id != null
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM organization_employee WHERE id=? AND created_by=?",
                Integer.class,
                id,
                owner)
            == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM organization_employee WHERE phone=? AND id<>?",
            Integer.class,
            phone.trim(),
            id == null ? 0 : id)
        > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号已存在");
    try {
      if (id == null) {
        jdbc.update(
            "INSERT INTO"
                + " organization_employee(created_by,department_id,name,phone,email,password_hash,job_title,status)"
                + " VALUES(?,?,?,?,?,?,?,?)",
            owner,
            departmentId,
            name.trim(),
            phone.trim(),
            cleanEmail,
            encoder.encode("12345678"),
            jobTitle,
            status);
        id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
      } else
        jdbc.update(
            "UPDATE organization_employee SET department_id=?,name=?,phone=?,email=?,job_title=?,status=?"
                + " WHERE id=? AND created_by=?",
            departmentId,
            name.trim(),
            phone.trim(),
            cleanEmail,
            jobTitle,
            status,
            id,
            owner);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号已存在");
    }
    final long saved = id;
    return employees().stream()
        .filter(r -> ((Number) r.get("id")).longValue() == saved)
        .findFirst()
        .orElseThrow();
  }
  public void resetPassword(long id) {
    int changed = jdbc.update("UPDATE organization_employee SET password_hash=? WHERE id=? AND created_by=?",
        encoder.encode("12345678"), id, ManagementAccess.userId());
    if (changed != 1) throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
  }

}
