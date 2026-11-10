package com.aiquestion.service;

import com.aiquestion.common.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public TrainingService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public record SaveTraining(
      String name,
      String description,
      boolean surveyEnabled,
      List<TrainingSurveyRules.Question> questions,
      Integer version,
      Long assessmentId,
      String category,
      String requirementType,
      TrainingAudienceService.Scope audience, java.time.LocalDateTime endAt, java.time.LocalDateTime startAt) {}

  public record Submission(Integer version, Map<String, String> answers) {}

  public TrainingSurveyRules.Question createQuestionDraft(String type) {
    if (type == null || !Set.of("RATING", "SINGLE", "TEXT").contains(type)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的评估题型");
    }
    return new TrainingSurveyRules.Question(
        UUID.randomUUID().toString(),
        type,
        "",
        true,
        "SINGLE".equals(type) ? List.of("满意", "一般") : List.of());
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public List<TrainingSurveyRules.Question> questions(Map<String, Object> row) {
    try {
      return json.readValue(row.get("questions_json").toString(), new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Object> row(long id, boolean lock) {
    var rows =
        jdbc.queryForList("SELECT * FROM training WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
    return rows.get(0);
  }

  private void owner(Map<String, Object> row, long user) {
    if (((Number) row.get("created_by")).longValue() != user)
      throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
  }

  private boolean enabled(Map<String, Object> row) {
    return Boolean.TRUE.equals(row.get("survey_enabled"))
        || "1".equals(String.valueOf(row.get("survey_enabled")));
  }

  private long count(long id) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM training_survey_response WHERE training_id=?", Long.class, id);
  }

  private Map<String, Object> view(Map<String, Object> row) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", row.get("id"));
    data.put("name", row.get("name"));
    data.put("description", row.get("description"));
    data.put("purpose", row.get("assessment_id") == null ? "STANDALONE" : "EXAM");
    data.put("category", row.get("category"));
    data.put("requirementType", row.get("requirement_type"));
    data.put("assessmentId", row.get("assessment_id"));
    data.put("startAt", row.get("start_at") instanceof java.sql.Timestamp time ? time.toLocalDateTime() : row.get("start_at"));
    data.put("endAt", row.get("end_at") instanceof java.sql.Timestamp time ? time.toLocalDateTime() : row.get("end_at"));
    data.put("surveyEnabled", enabled(row));
    data.put("questions", questions(row));
    data.put("version", row.get("version"));
    return data;
  }

  public List<Map<String, Object>> list(long user) {
    return jdbc
        .queryForList("SELECT * FROM training WHERE created_by=? ORDER BY id DESC", user)
        .stream()
        .map(
            r -> {
              var v = view(r);
              v.put("responseCount", count(((Number) r.get("id")).longValue()));
              return v;
            })
        .toList();
  }

  public Map<String, Object> get(long id, long user) {
    var r = row(id, false);
    owner(r, user);
    var v = view(r);
    v.put("responseCount", count(id));
    v.put("audience", new TrainingAudienceService(jdbc).get(id, user));
    v.put(
        "learningLocked",
        (r.get("assessment_id") instanceof Number task
                && jdbc.queryForObject(
                        "SELECT COUNT(*) FROM assessment_participant WHERE task_id=?",
                        Integer.class,
                        task.longValue())
                    > 0)
            || jdbc.queryForObject(
                    "SELECT COUNT(*) FROM candidate_learning_session s JOIN training t ON"
                        + " (t.assessment_id=s.task_id OR t.id=s.training_id) WHERE t.id=?",
                    Integer.class,
                    id)
                > 0);
    return v;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> save(Long id, SaveTraining command, long user) {
    if (command.startAt() != null && command.endAt() != null
        && !command.endAt().isAfter(command.startAt()))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "培训结束时间必须晚于开始时间");
    if (command.name() == null
        || command.name().isBlank()
        || command.name().length() > 160
        || (command.description() != null && command.description().length() > 2000))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "请检查培训名称和简介长度");
    if (command.assessmentId() != null
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM assessment_task WHERE id=? AND created_by=?",
                Integer.class,
                command.assessmentId(),
                user)
            == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "只能关联自己创建的考试");
    // Use the same task-before-training lock order as starting learning or an exam.
    var assessmentLocks = new TreeSet<Long>();
    if (id != null) {
      var existing = row(id, false);
      owner(existing, user);
      if (existing.get("assessment_id") instanceof Number previous)
        assessmentLocks.add(previous.longValue());
    }
    if (command.assessmentId() != null) assessmentLocks.add(command.assessmentId());
    for (long task : assessmentLocks) new AssessmentRosterService(jdbc).lockTask(task);
    String purpose = command.assessmentId() == null ? "STANDALONE" : "EXAM";
    if (command.assessmentId() != null) {
      if (jdbc.queryForObject(
              "SELECT COUNT(*) FROM training WHERE assessment_id=? AND id<>?",
              Integer.class,
              command.assessmentId(),
              id == null ? 0 : id)
          > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "该考试已关联其他培训");
    }
    if (command.assessmentId() != null) {
      boolean associationChanged =
          id == null
              || !Objects.equals(row(id, false).get("assessment_id"), command.assessmentId());
      if (associationChanged
          && jdbc.queryForObject(
                  "SELECT (SELECT COUNT(*) FROM assessment_participant WHERE task_id=?)+(SELECT"
                      + " COUNT(*) FROM candidate_learning_session WHERE task_id=?)",
                  Long.class,
                  command.assessmentId(),
                  command.assessmentId())
              > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "该考试已有学习或答题记录，不能新增关联培训");
    }
    String category = Objects.toString(command.category(), "UNCATEGORIZED");
    String requirement = Objects.toString(command.requirementType(), "REQUIRED");
    if (!Set.of("UNCATEGORIZED", "ONBOARDING", "SAFETY", "POLICY").contains(category)
        || !Set.of("REQUIRED", "ELECTIVE").contains(requirement))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "培训分类或学习要求无效");
    TrainingSurveyRules.questions(command.questions());
    if (command.surveyEnabled() && command.questions().isEmpty())
      throw new BusinessException(ErrorCode.BAD_REQUEST, "开启评估时至少添加一道题");
    if (id == null) {
      jdbc.update(
          "INSERT INTO training(name,description,survey_enabled,questions_json,created_by)"
              + " VALUES(?,?,?,?,?)",
          command.name().trim(),
          Objects.toString(command.description(), ""),
          command.surveyEnabled(),
          encode(command.questions()),
          user);
      id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    } else {
      var old = row(id, true);
      owner(old, user);
      Long oldAssessment = old.get("assessment_id") instanceof Number n ? n.longValue() : null;
      if ((!Objects.equals(oldAssessment, command.assessmentId()))
          && jdbc.queryForObject(
                  "SELECT COUNT(*) FROM candidate_learning_session s WHERE s.training_id=? OR"
                      + " s.task_id=?",
                  Integer.class,
                  id,
                  oldAssessment)
              > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能变更关联考试");
      if ((!Objects.equals(oldAssessment, command.assessmentId()))
          && oldAssessment != null
          && jdbc.queryForObject(
                  "SELECT COUNT(*) FROM assessment_participant WHERE task_id=?",
                  Integer.class,
                  oldAssessment)
              > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "已有考试记录，不能变更关联考试");
      if (!Objects.equals(command.version(), ((Number) old.get("version")).intValue()))
        throw new BusinessException(ErrorCode.BAD_REQUEST, "培训已被修改，请刷新后重试");
      if (count(id) > 0 && !questions(old).equals(command.questions()))
        throw new BusinessException(ErrorCode.BAD_REQUEST, "已有反馈，不能修改题目；请新建培训");
      jdbc.update(
          "UPDATE training SET"
              + " name=?,description=?,survey_enabled=?,questions_json=?,version=version+1 WHERE"
              + " id=?",
          command.name().trim(),
          Objects.toString(command.description(), ""),
          command.surveyEnabled(),
          encode(command.questions()),
          id);
    }
    jdbc.update(
        "UPDATE training SET assessment_id=?,category=?,requirement_type=?,purpose=?,end_at=?,start_at=? WHERE id=?",
        command.assessmentId(),
        category,
        requirement,
        purpose,
        command.endAt(),
        command.startAt(),
        id);
    new TrainingAudienceService(jdbc).save(id, user, command.audience());
    return get(id, user);
  }

  @Transactional
  public void delete(long id, long user) {
    var r = row(id, true);
    owner(r, user);
    if (count(id) > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "已有反馈，不能删除培训，可关闭评估");
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM candidate_learning_session s JOIN training t ON"
                + " (t.assessment_id=s.task_id OR t.id=s.training_id) WHERE t.id=?",
            Integer.class,
            id)
        > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能删除培训");
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM training_material WHERE training_id=?", Integer.class, id)
        > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "请先删除培训材料，再删除培训");
    jdbc.update("DELETE FROM training WHERE id=?", id);
  }

  private void requireLearner(long id, long user) {
    var r = row(id, false);
    if (((Number) r.get("created_by")).longValue() == user) return;
    var person = jdbc.queryForMap("SELECT name,phone FROM app_user WHERE id=?", user);
    TrainingAudienceService.require(
        jdbc, id, person.get("name").toString(), person.get("phone").toString());
  }

  public Map<String, Object> survey(long id, long user) {
    requireLearner(id, user);
    return surveyView(id, "user_id", user);
  }

  public Map<String, Object> employeeSurvey(
      long id, com.aiquestion.service.AccountService.Profile employee) {
    requireEmployeeSurvey(id, employee);
    return surveyView(id, "employee_id", employee.userId());
  }

  private void requireEmployeeSurvey(
      long id, com.aiquestion.service.AccountService.Profile employee) {
    var course = row(id, false);
    if (((Number) course.get("created_by")).longValue() != employee.enterpriseId())
      throw new BusinessException(ErrorCode.FORBIDDEN);
    TrainingAudienceService.require(jdbc, id, employee.name(), employee.phone());
  }

  private Map<String, Object> surveyView(long id, String identityColumn, long accountId) {
    var r = row(id, false);
    if (!enabled(r)) throw new BusinessException(ErrorCode.BAD_REQUEST, "此培训尚未开启或已关闭评估");
    var result = view(r);
    result.put("submitted", submitted(id, identityColumn, accountId));
    return result;
  }

  private boolean submitted(long id, String identityColumn, long accountId) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM training_survey_response WHERE training_id=? AND "
                + identityColumn
                + "=?",
            Integer.class,
            id,
            accountId)
        > 0;
  }

  @Transactional
  public void submit(long id, Submission command, long user) {
    requireLearner(id, user);
    saveSurvey(id, command, "user_id", user);
  }

  @Transactional
  public void submitEmployee(
      long id, Submission command, com.aiquestion.service.AccountService.Profile employee) {
    requireEmployeeSurvey(id, employee);
    saveSurvey(id, command, "employee_id", employee.userId());
  }

  private void saveSurvey(long id, Submission command, String identityColumn, long accountId) {
    var r = row(id, true);
    if (!enabled(r)) throw new BusinessException(ErrorCode.BAD_REQUEST, "评估已关闭");
    if (!Objects.equals(command.version(), ((Number) r.get("version")).intValue()))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "评估内容已更新，请刷新页面后填写");
    TrainingSurveyRules.answers(questions(r), command.answers());
    if (submitted(id, identityColumn, accountId))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "你已提交过本次评估");
    jdbc.update(
        "INSERT INTO training_survey_response(training_id,"
            + identityColumn
            + ",answers_json) VALUES(?,?,?)",
        id,
        accountId,
        encode(command.answers()));
  }

  public List<Map<String, Object>> responses(long id, long user) {
    owner(row(id, false), user);
    return jdbc
        .queryForList(
            "SELECT r.id,COALESCE(e.name,u.name) respondentName,r.answers_json,r.created_at"
                + " submittedAt FROM training_survey_response r LEFT JOIN app_user u ON"
                + " u.id=r.user_id LEFT JOIN organization_employee e ON e.id=r.employee_id WHERE"
                + " r.training_id=? ORDER BY r.id DESC",
            id)
        .stream()
        .map(
            r -> {
              try {
                r.put(
                    "answers",
                    json.readValue(
                        r.remove("answers_json").toString(),
                        new TypeReference<Map<String, String>>() {}));
                return r;
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  public Map<String, Object> learning(long id, long user) {
    requireLearner(id, user);
    var data = view(row(id, false));
    data.put(
        "videos",
        jdbc.queryForList(
            "SELECT v.id,v.title,COALESCE(p.position_seconds,0)"
                + " positionSeconds,COALESCE(p.completed,false) completed FROM training_video v"
                + " LEFT JOIN training_video_progress p ON p.video_id=v.id AND p.user_id=? WHERE"
                + " v.training_id=? ORDER BY v.sort_order,v.id",
            user,
            id));
    return data;
  }

  public void progress(long id, long video, long user, double position, boolean completed) {
    requireLearner(id, user);
    video(id, video);
    if (!Double.isFinite(position) || position < 0 || position > 86400)
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    jdbc.update(
        "INSERT INTO training_video_progress(video_id,user_id,position_seconds,completed)"
            + " VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " position_seconds=VALUES(position_seconds),completed=completed OR VALUES(completed)",
        video,
        user,
        position,
        completed);
  }

  public Map<String, Object> video(long id, long video) {
    var rows =
        jdbc.queryForList("SELECT * FROM training_video WHERE training_id=? AND id=?", id, video);
    if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
    return rows.get(0);
  }

  @Transactional
  public void addVideo(long id, long user, String title, String path, String type) {
    owner(row(id, true), user);
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM candidate_learning_session s JOIN training t ON"
                + " (t.assessment_id=s.task_id OR t.id=s.training_id) WHERE t.id=?",
            Integer.class,
            id)
        > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能新增视频，请新建培训");
    jdbc.update(
        "INSERT INTO training_video(training_id,title,storage_path,content_type) VALUES(?,?,?,?)",
        id,
        title,
        path,
        type);
  }

  @Transactional
  public String removeVideo(long id, long video, long user) {
    owner(row(id, true), user);
    var data = video(id, video);
    if (jdbc.queryForObject(
                "SELECT COUNT(*) FROM candidate_video_progress WHERE video_id=?",
                Integer.class,
                video)
            > 0
        || jdbc.queryForObject(
                "SELECT COUNT(*) FROM training_video_progress WHERE video_id=?",
                Integer.class,
                video)
            > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "已有学习记录，不能删除视频");
    jdbc.update("DELETE FROM training_video WHERE id=? AND training_id=?", video, id);
    return data.get("storage_path").toString();
  }

  @Transactional
  public void reorderVideos(long id, long user, List<Long> ids) {
    owner(row(id, true), user);
    var current =
        jdbc.queryForList("SELECT id FROM training_video WHERE training_id=?", Long.class, id);
    if (ids == null
        || ids.stream().anyMatch(Objects::isNull)
        || ids.size() != current.size()
        || new HashSet<>(ids).size() != ids.size()
        || !new HashSet<>(ids).equals(new HashSet<>(current)))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "视频列表已变更，请刷新后重新排序");
    for (int i = 0; i < ids.size(); i++)
      jdbc.update(
          "UPDATE training_video SET sort_order=? WHERE training_id=? AND id=?", i, id, ids.get(i));
  }

  public List<String> videoFiles(long id, long user) {
    owner(row(id, false), user);
    return jdbc.queryForList(
        "SELECT storage_path FROM training_video WHERE training_id=?", String.class, id);
  }
}
