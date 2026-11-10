package com.aiquestion.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TrainingLogService {
  private final JdbcTemplate jdbc;
  private final TrainingService trainings;

  public TrainingLogService(JdbcTemplate jdbc, TrainingService trainings) {
    this.jdbc = jdbc;
    this.trainings = trainings;
  }

  /** 提醒名单只需完成状态，批量查询计数，避免逐人加载材料与答卷明细。 */
  public Set<String> completedParticipants(long id, long user) {
    trainings.get(id, user);
    long total =
        jdbc.queryForObject(
            """
SELECT (SELECT COUNT(*) FROM training_video WHERE training_id=?) +
(SELECT COUNT(*) FROM training_material_page p JOIN training_material m ON m.id=p.material_id WHERE m.training_id=?)
""",
            Long.class,
            id,
            id);
    var completed = new HashSet<String>();
    for (var session :
        jdbc.queryForList(
            """
SELECT s.phone,s.candidate_name,s.completed_at,
(SELECT COUNT(*) FROM candidate_video_progress p JOIN training_video v ON v.id=p.video_id
  WHERE p.session_id=s.id AND v.training_id=t.id AND p.completed=TRUE) +
(SELECT COUNT(*) FROM candidate_material_progress p JOIN training_material m ON m.id=p.material_id
  WHERE p.session_id=s.id AND m.training_id=t.id AND p.completed=TRUE) completed_parts
FROM candidate_learning_session s JOIN training t ON (t.assessment_id=s.task_id OR t.id=s.training_id)
WHERE t.id=?
""",
            id)) {
      if ("COMPLETED"
          .equals(
              LearnerTaskStatus.learning(
                  true,
                  total,
                  ((Number) session.get("completed_parts")).longValue(),
                  session.get("completed_at") != null))) {
        completed.add(session.get("phone") + "|" + session.get("candidate_name"));
      }
    }
    return completed;
  }

  public List<Map<String, Object>> list(long id, long user) {
    trainings.get(id, user);
    var sessions =
        jdbc.queryForList(
            "SELECT s.id,s.completed_at completedAt,s.candidate_name name,s.phone,s.task_id"
                + " taskId,COALESCE(a.department_name,'') department FROM"
                + " candidate_learning_session s JOIN training t ON (t.assessment_id=s.task_id OR"
                + " t.id=s.training_id) LEFT JOIN training_audience a ON a.training_id=t.id AND"
                + " a.phone=s.phone WHERE t.id=? ORDER BY s.id DESC",
            id);
    for (var s : sessions) {
      var videos =
          jdbc.queryForList(
              "SELECT v.id,v.title,p.position_seconds positionSeconds,p.studied_seconds"
                  + " studiedSeconds,p.completed,p.first_studied_at firstStudiedAt,p.updated_at"
                  + " lastStudiedAt,p.completed_at completedAt FROM training_video v LEFT JOIN"
                  + " candidate_video_progress p ON p.video_id=v.id AND p.session_id=? WHERE"
                  + " v.training_id=? ORDER BY v.sort_order,v.id",
              s.get("id"),
              id);
      var pages =
          jdbc.queryForList(
              "SELECT m.id,m.title,p.page_number pageNumber,p.required_seconds"
                  + " requiredSeconds,r.studied_seconds studiedSeconds,r.completed,r.first_seen"
                  + " firstStudiedAt,r.last_seen lastStudiedAt,r.completed_at completedAt FROM"
                  + " training_material m JOIN training_material_page p ON p.material_id=m.id LEFT"
                  + " JOIN candidate_material_progress r ON r.material_id=m.id AND"
                  + " r.page_number=p.page_number AND r.session_id=? WHERE m.training_id=? ORDER BY"
                  + " m.sort_order,m.id,p.page_number",
              s.get("id"),
              id);
      var parts = new ArrayList<Map<String, Object>>();
      parts.addAll(videos);
      parts.addAll(pages);
      s.put("materials", parts);
      s.put(
          "studiedSeconds",
          parts.stream()
              .filter(p -> p.get("studiedSeconds") instanceof Number)
              .mapToDouble(p -> ((Number) p.get("studiedSeconds")).doubleValue())
              .sum());
      long completedParts =
          parts.stream()
              .filter(
                  p ->
                      Boolean.TRUE.equals(p.get("completed"))
                          || "1".equals(String.valueOf(p.get("completed"))))
              .count();
      s.put(
          "completed",
          "COMPLETED"
              .equals(
                  LearnerTaskStatus.learning(
                      true, parts.size(), (int) completedParts, s.get("completedAt") != null)));
      s.put(
          "attempts",
          jdbc.queryForList(
              "SELECT a.id,a.attempt_no attemptNo,a.status,a.started_at startedAt,a.submitted_at"
                  + " submittedAt,CASE WHEN a.submitted_at IS NOT NULL THEN"
                  + " GREATEST(0,TIMESTAMPDIFF(SECOND,a.started_at,a.submitted_at)) END"
                  + " durationSeconds,a.score,a.total_score totalScore,a.passed FROM"
                  + " assessment_attempt a JOIN assessment_participant p ON p.id=a.participant_id"
                  + " WHERE a.task_id=? AND p.phone=? AND p.candidate_name=? ORDER BY a.attempt_no",
              s.get("taskId"),
              s.get("phone"),
              s.get("name")));
    }
    return sessions;
  }
}
