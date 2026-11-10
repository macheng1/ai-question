package com.aiquestion.service;

import com.aiquestion.security.AccountIdentity;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only learner views. Existing candidate learning and exam submission routes remain unchanged.
 */
@Service
public class LearnerRecordService {
  private final JdbcTemplate jdbc;
  private final AccountService accounts;

  public LearnerRecordService(JdbcTemplate jdbc, AccountService accounts) {
    this.jdbc = jdbc;
    this.accounts = accounts;
  }

  public List<Map<String, Object>> trainings(AccountIdentity identity) {
    accounts.employee(identity.accountId());
    var rows =
        new NamedParameterJdbcTemplate(jdbc)
            .queryForList(
                """
SELECT t.id,t.name,t.description,t.category,t.requirement_type requirementType,
  t.assessment_id assessmentId,t.survey_enabled surveyEnabled,ta.assigned_at assignedAt,
  EXISTS(SELECT 1 FROM training_survey_response r WHERE r.training_id=t.id AND r.employee_id=:employee) surveySubmitted,
  s.id sessionId,s.started_at startedAt,s.completed_at confirmedAt,
  (SELECT COUNT(*) FROM training_video v WHERE v.training_id=t.id)
    +(SELECT COUNT(*) FROM training_material m JOIN training_material_page mp ON mp.material_id=m.id WHERE m.training_id=t.id) totalUnits,
  (SELECT COUNT(*) FROM candidate_video_progress p JOIN training_video v ON v.id=p.video_id WHERE p.session_id=s.id AND v.training_id=t.id AND p.completed=1)
    +(SELECT COUNT(*) FROM candidate_material_progress p JOIN training_material m ON m.id=p.material_id WHERE p.session_id=s.id AND m.training_id=t.id AND p.completed=1) completedUnits,
  COALESCE((SELECT SUM(p.studied_seconds) FROM candidate_video_progress p JOIN training_video v ON v.id=p.video_id WHERE v.training_id=t.id AND p.session_id=s.id),0)
    +COALESCE((SELECT SUM(p.studied_seconds) FROM candidate_material_progress p JOIN training_material m ON m.id=p.material_id WHERE m.training_id=t.id AND p.session_id=s.id),0) studiedSeconds,
  (SELECT MAX(p.completed_at) FROM candidate_video_progress p JOIN training_video v ON v.id=p.video_id WHERE v.training_id=t.id AND p.session_id=s.id) videoCompletedAt,
  (SELECT MAX(p.completed_at) FROM candidate_material_progress p JOIN training_material m ON m.id=p.material_id WHERE m.training_id=t.id AND p.session_id=s.id) readingCompletedAt,
  a.name examName,a.start_at examStartAt,a.end_at examEndAt,a.status examLifecycle,
  a.allow_retake allowRetake,a.max_attempts maxAttempts,a.show_result_immediately resultVisible,
  aa.id attemptId,aa.status attemptStatus,aa.score,aa.total_score totalScore,aa.passed,
  aa.started_at examStartedAt,aa.submitted_at examSubmittedAt,
  (SELECT COUNT(*) FROM assessment_attempt x JOIN assessment_participant p ON p.id=x.participant_id WHERE p.task_id=a.id AND p.employee_id=:employee) attemptCount
FROM training_audience ta JOIN training t ON t.id=ta.training_id
LEFT JOIN assessment_task a ON a.id=t.assessment_id
LEFT JOIN candidate_learning_session s ON s.id=(SELECT MAX(x.id) FROM candidate_learning_session x
  WHERE x.employee_id=:employee AND (x.training_id=t.id OR x.task_id=t.assessment_id))
LEFT JOIN assessment_attempt aa ON aa.id=(SELECT MAX(x.id) FROM assessment_attempt x
  JOIN assessment_participant p ON p.id=x.participant_id WHERE p.task_id=a.id AND p.employee_id=:employee)
WHERE ta.employee_id=:employee AND t.created_by=:enterprise
ORDER BY ta.assigned_at DESC,t.id DESC
""",
                Map.of("employee", identity.accountId(), "enterprise", identity.enterpriseId()));
    var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
    for (var row : rows) {
      String learning =
          LearnerTaskStatus.learning(
              row.get("sessionId") != null,
              number(row, "totalUnits"),
              number(row, "completedUnits"),
              row.get("confirmedAt") != null);
      row.put("learningStatus", learning);
      row.put("started", row.get("sessionId") != null);
      row.put(
          "progress",
          number(row, "totalUnits") == 0
              ? (learning.equals("COMPLETED") ? 100 : 0)
              : Math.min(100, number(row, "completedUnits") * 100 / number(row, "totalUnits")));
      Object completedAt = number(row, "totalUnits") == 0 ? row.get("confirmedAt") : null;
      if (completedAt == null) {
        var video = date(row.get("videoCompletedAt"));
        var reading = date(row.get("readingCompletedAt"));
        completedAt =
            video == null
                ? row.get("readingCompletedAt")
                : reading == null || video.isAfter(reading)
                    ? row.get("videoCompletedAt")
                    : row.get("readingCompletedAt");
      }
      row.put("completedAt", learning.equals("COMPLETED") ? completedAt : null);
      row.remove("videoCompletedAt");
      row.remove("readingCompletedAt");
      row.remove("confirmedAt");
      row.put("purpose", row.get("assessmentId") == null ? "STANDALONE" : "EXAM");
      if (row.get("assessmentId") != null) {
        var start = date(row.get("examStartAt"));
        var end = date(row.get("examEndAt"));
        boolean disabled = "DISABLED".equals(row.get("examLifecycle"));
        boolean closed =
            disabled
                || "ENDED".equals(row.get("examLifecycle"))
                || (end != null && !now.isBefore(end));
        boolean published =
            !"DRAFT".equals(row.get("examLifecycle")) && (start == null || !now.isBefore(start));
        boolean submitted = "SUBMITTED".equals(row.get("attemptStatus"));
        row.put(
            "examStatus",
            LearnerTaskStatus.exam(
                learning.equals("COMPLETED"),
                row.get("attemptId") != null && !submitted,
                submitted,
                flag(row.get("resultVisible")),
                flag(row.get("passed")),
                closed,
                published));
        row.put(
            "canRetake",
            submitted
                && published
                && LearnerTaskStatus.canRetake(
                    flag(row.get("allowRetake")),
                    (int) number(row, "maxAttempts"),
                    (int) number(row, "attemptCount"),
                    start,
                    end,
                    closed,
                    now));
        row.put(
            "remainingAttempts",
            Math.max(
                0,
                (flag(row.get("allowRetake")) ? number(row, "maxAttempts") : 1)
                    - number(row, "attemptCount")));
        if (!flag(row.get("resultVisible")) || !submitted) {
          row.put("score", null);
          row.put("passed", null);
        }
      }
      row.remove("resultVisible");
    }
    return rows;
  }

  private static long number(Map<String, Object> row, String key) {
    return row.get(key) instanceof Number n ? n.longValue() : 0;
  }

  private static boolean flag(Object value) {
    return Boolean.TRUE.equals(value) || "1".equals(String.valueOf(value));
  }

  private static java.time.LocalDateTime date(Object value) {
    if (value instanceof java.sql.Timestamp t)
      return java.time.LocalDateTime.ofInstant(t.toInstant(), java.time.ZoneOffset.UTC);
    if (value instanceof java.time.LocalDateTime t) return t;
    return null;
  }

  public Map<String, Object> detail(AccountIdentity identity, long trainingId) {
    var training =
        trainings(identity).stream()
            .filter(r -> ((Number) r.get("id")).longValue() == trainingId)
            .findFirst()
            .orElseThrow(
                () ->
                    new com.aiquestion.common.BusinessException(
                        com.aiquestion.common.ErrorCode.NOT_FOUND, "培训不存在或未分配给你"));
    Object session = training.get("sessionId");
    var materials =
        jdbc.queryForList(
            """
SELECT v.id,v.title,'VIDEO' materialType,1 totalUnits,COALESCE(p.completed,0) completedUnits,COALESCE(p.studied_seconds,0) studiedSeconds,p.completed_at completedAt
FROM training_video v LEFT JOIN candidate_video_progress p ON p.video_id=v.id AND p.session_id=? WHERE v.training_id=?
UNION ALL
SELECT m.id,m.title,m.material_type materialType,m.page_count totalUnits,
  COALESCE(SUM(p.completed),0) completedUnits,COALESCE(SUM(p.studied_seconds),0) studiedSeconds,
  CASE WHEN SUM(p.completed)=m.page_count THEN MAX(p.completed_at) END completedAt
FROM training_material m LEFT JOIN candidate_material_progress p ON p.material_id=m.id AND p.session_id=?
WHERE m.training_id=? GROUP BY m.id,m.title,m.material_type,m.page_count
""",
            session,
            trainingId,
            session,
            trainingId);
    var attempts =
        exams(identity).stream()
            .filter(r -> Objects.equals(r.get("taskId"), training.get("assessmentId")))
            .toList();
    return Map.of("training", training, "materials", materials, "attempts", attempts);
  }

  public List<Map<String, Object>> learningRecords(AccountIdentity identity) {
    return trainings(identity).stream()
        .filter(r -> r.get("sessionId") != null)
        .map(
            r -> {
              var result = new LinkedHashMap<>(r);
              result.put("trainingId", r.get("id"));
              result.put("completed", "COMPLETED".equals(r.get("learningStatus")));
              return (Map<String, Object>) result;
            })
        .toList();
  }

  public List<Map<String, Object>> exams(AccountIdentity identity) {
    accounts.employee(identity.accountId());
    return jdbc
        .queryForList(
            """
SELECT a.id,a.task_id taskId,t.name,a.status,a.attempt_no attemptNo,a.score,a.total_score totalScore,a.passed,
  a.started_at startedAt,a.submitted_at submittedAt,
  CASE WHEN a.submitted_at IS NOT NULL THEN GREATEST(0,TIMESTAMPDIFF(SECOND,a.started_at,a.submitted_at)) END durationSeconds,
  t.show_result_immediately showResultImmediately
FROM assessment_attempt a JOIN assessment_task t ON t.id=a.task_id
JOIN assessment_participant p ON p.id=a.participant_id
WHERE t.created_by=? AND p.employee_id=? ORDER BY a.id DESC
""",
            identity.enterpriseId(),
            identity.accountId())
        .stream()
        .map(
            row -> {
              if (!Boolean.TRUE.equals(row.get("showResultImmediately"))
                  && !"1".equals(String.valueOf(row.get("showResultImmediately")))) {
                row.put("score", null);
                row.put("passed", null);
              }
              return row;
            })
        .toList();
  }

  public List<Map<String, Object>> mistakes(AccountIdentity identity) {
    accounts.employee(identity.accountId());
    var mistakes =
        jdbc.queryForList(
            """
SELECT aa.id,q.id questionId,t.name examName,q.stem,aa.answer_text userAnswer,q.answer_text correctAnswer,q.analysis
FROM assessment_answer aa JOIN assessment_attempt a ON a.id=aa.attempt_id
JOIN assessment_task t ON t.id=a.task_id JOIN assessment_participant p ON p.id=a.participant_id
JOIN question_item q ON q.id=aa.question_id
WHERE t.created_by=? AND p.employee_id=? AND a.status='SUBMITTED'
  AND t.show_result_immediately=1 AND COALESCE(aa.is_correct,0)=0
  AND a.id=(SELECT MAX(a2.id) FROM assessment_attempt a2 WHERE a2.participant_id=p.id AND a2.status='SUBMITTED')
ORDER BY aa.id DESC
""",
            identity.enterpriseId(),
            identity.accountId());
    if (mistakes.isEmpty()) return mistakes;
    var questionIds = mistakes.stream().map(row -> row.get("questionId")).distinct().toList();
    var options =
        new NamedParameterJdbcTemplate(jdbc)
            .queryForList(
                """
                SELECT question_id questionId,option_key optionKey,option_text optionText
                FROM question_option WHERE question_id IN (:ids) ORDER BY sort_no,id
                """,
                Map.of("ids", questionIds));
    var optionsByQuestion =
        options.stream().collect(Collectors.groupingBy(row -> row.get("questionId")));
    mistakes.forEach(
        row ->
            row.put("options", optionsByQuestion.getOrDefault(row.get("questionId"), List.of())));
    return mistakes;
  }
}
