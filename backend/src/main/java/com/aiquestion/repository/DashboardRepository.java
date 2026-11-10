package com.aiquestion.repository;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 一次批量读取名单及最新记录，避免按员工逐个请求学习和考试服务。 */
@Repository
public class DashboardRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private Map<String, Object> parameters(long owner, Map<String, Object> filters) {
    var params = new HashMap<>(filters);
    params.put("owner", owner);
    return params;
  }

  private String conditions(Map<String, Object> filters) {
    var sql = new StringBuilder(" WHERE t.created_by=:owner");
    if (filters.containsKey("start")) sql.append(" AND COALESCE(t.start_at,t.created_at)>=:start");
    if (filters.containsKey("end")) sql.append(" AND COALESCE(t.start_at,t.created_at)<:end");
    if (filters.containsKey("trainingId")) sql.append(" AND t.id=:trainingId");
    if (filters.containsKey("category")) sql.append(" AND t.category=:category");
    if (filters.containsKey("departmentId"))
      sql.append(
          " AND EXISTS(SELECT 1 FROM training_audience scope WHERE scope.training_id=t.id AND"
              + " scope.department_id=:departmentId)");
    return sql.toString();
  }

  public List<Map<String, Object>> trainings(long owner, Map<String, Object> filters) {
    return jdbc.queryForList(
        "SELECT t.id,t.name,t.assessment_id assessmentId FROM training t"
            + conditions(filters)
            + " ORDER BY t.id DESC",
        parameters(owner, filters));
  }

  public Map<String, Object> options(long owner) {
    var params = Map.of("owner", owner);
    return Map.of(
        "trainings",
        jdbc.queryForList(
            "SELECT id,name FROM training WHERE created_by=:owner ORDER BY id DESC", params),
        "departments",
        jdbc.queryForList(
            "SELECT DISTINCT a.department_id id,a.department_name name FROM training_audience a"
                + " JOIN training t ON t.id=a.training_id WHERE t.created_by=:owner ORDER BY"
                + " a.department_name",
            params));
  }

  public List<Map<String, Object>> rows(long owner, Map<String, Object> filters) {
    // 最新答卷（含未交卷）与现有成绩统计一致；学习记录通过稳定 employee_id 关联。
    String sql =
        """
SELECT t.id trainingId,t.name trainingName,t.assessment_id assessmentId,t.end_at trainingEnd,
 ta.employee_id employeeId,ta.candidate_name name,ta.phone,ta.department_id departmentId,ta.department_name departmentName,
 s.id sessionId,s.started_at startedAt,s.completed_at confirmedAt,
 (SELECT COUNT(*) FROM training_video v WHERE v.training_id=t.id)
 +(SELECT COUNT(*) FROM training_material m JOIN training_material_page p ON p.material_id=m.id WHERE m.training_id=t.id) totalUnits,
 (SELECT COUNT(*) FROM candidate_video_progress p JOIN training_video v ON v.id=p.video_id WHERE v.training_id=t.id AND p.session_id=s.id AND p.completed=1)
 +(SELECT COUNT(*) FROM candidate_material_progress p JOIN training_material m ON m.id=p.material_id WHERE m.training_id=t.id AND p.session_id=s.id AND p.completed=1) completedUnits,
 COALESCE((SELECT SUM(p.studied_seconds) FROM candidate_video_progress p JOIN training_video v ON v.id=p.video_id WHERE v.training_id=t.id AND p.session_id=s.id),0)
 +COALESCE((SELECT SUM(p.studied_seconds) FROM candidate_material_progress p JOIN training_material m ON m.id=p.material_id WHERE m.training_id=t.id AND p.session_id=s.id),0) studiedSeconds,
 (SELECT COUNT(*) FROM candidate_video_progress p JOIN training_video v ON v.id=p.video_id WHERE v.training_id=t.id AND p.session_id=s.id AND p.studied_seconds IS NULL) missingDuration,
 (SELECT MAX(p.completed_at) FROM candidate_video_progress p JOIN training_video v ON v.id=p.video_id WHERE v.training_id=t.id AND p.session_id=s.id) videoCompletedAt,
 (SELECT MAX(p.completed_at) FROM candidate_material_progress p JOIN training_material m ON m.id=p.material_id WHERE m.training_id=t.id AND p.session_id=s.id) readingCompletedAt,
 a.end_at examEnd,a.name examName,
 aa.id attemptId,aa.status attemptStatus,aa.score,aa.total_score totalScore,aa.passed,aa.submitted_at submittedAt,
 CASE WHEN aa.submitted_at IS NOT NULL THEN GREATEST(0,TIMESTAMPDIFF(SECOND,aa.started_at,aa.submitted_at)) END durationSeconds,
 (SELECT COUNT(*) FROM assessment_attempt x JOIN assessment_participant p ON p.id=x.participant_id WHERE p.task_id=a.id AND p.employee_id=ta.employee_id) attemptCount
FROM training t JOIN training_audience ta ON ta.training_id=t.id
LEFT JOIN assessment_task a ON a.id=t.assessment_id AND a.created_by=t.created_by
LEFT JOIN candidate_learning_session s ON s.id=(SELECT MAX(x.id) FROM candidate_learning_session x WHERE x.employee_id=ta.employee_id AND (x.training_id=t.id OR x.task_id=t.assessment_id))
LEFT JOIN assessment_attempt aa ON aa.id=(SELECT MAX(x.id) FROM assessment_attempt x JOIN assessment_participant p ON p.id=x.participant_id WHERE p.task_id=a.id AND p.employee_id=ta.employee_id)
""";
    sql += conditions(filters);
    if (filters.containsKey("departmentId")) sql += " AND ta.department_id=:departmentId";
    return jdbc.queryForList(
        sql + " ORDER BY t.id DESC,ta.employee_id", parameters(owner, filters));
  }
}
