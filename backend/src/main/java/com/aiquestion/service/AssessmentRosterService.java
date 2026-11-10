package com.aiquestion.service;

import com.aiquestion.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AssessmentRosterService {
  private final JdbcTemplate jdbc;

  public AssessmentRosterService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Lock before any consistent read, so MySQL creates its snapshot after preceding writers commit.
   */
  public void lockTask(long task) {
    try {
      jdbc.queryForObject("SELECT id FROM assessment_task WHERE id=? FOR UPDATE", Long.class, task);
    } catch (org.springframework.dao.EmptyResultDataAccessException e) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在");
    }
  }

  public boolean hasTraining(long task) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM training WHERE assessment_id=?", Integer.class, task)
        > 0;
  }

  public List<Map<String, Object>> roster(long task) {
    var rows =
        jdbc.queryForList(
            "SELECT r.employee_id employeeId,r.department_id departmentId,r.department_name"
                + " departmentName,r.candidate_name candidateName,r.phone,EXISTS(SELECT 1 FROM"
                + " assessment_participant p JOIN assessment_attempt a ON a.participant_id=p.id"
                + " WHERE p.task_id=t.assessment_id AND p.phone=r.phone AND a.submitted_at IS NOT"
                + " NULL) submitted FROM training_audience r JOIN training t ON t.id=r.training_id"
                + " WHERE t.assessment_id=? ORDER BY r.employee_id",
            task);
    for (var row : rows) {
      Object value = row.get("submitted");
      row.put(
          "submitted",
          Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0);
    }
    return rows;
  }

  public void requireEligible(long task, String name, String phone) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=?", Integer.class, task)
        == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "考试不存在");
    TrainingAudienceService.requireTask(jdbc, task, name, phone);
  }
}
