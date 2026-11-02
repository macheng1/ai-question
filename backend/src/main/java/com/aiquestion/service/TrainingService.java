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
  public TrainingService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
  public record SaveTraining(String name, String description, boolean surveyEnabled, List<TrainingSurveyRules.Question> questions, Integer version, Long assessmentId) {}
  public record Submission(Integer version, Map<String, String> answers) {}
  public TrainingSurveyRules.Question createQuestionDraft(String type) {
    if (type == null || !Set.of("RATING", "SINGLE", "TEXT").contains(type)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的评估题型");
    }
    return new TrainingSurveyRules.Question(UUID.randomUUID().toString(), type, "", true,
        "SINGLE".equals(type) ? List.of("满意", "一般") : List.of());
  }
  private String encode(Object value) {
    try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); }
  }
  public List<TrainingSurveyRules.Question> questions(Map<String, Object> row) {
    try { return json.readValue(row.get("questions_json").toString(), new TypeReference<>() {}); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
  private Map<String, Object> row(long id, boolean lock) {
    var rows = jdbc.queryForList("SELECT * FROM training WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
    return rows.get(0);
  }
  private void owner(Map<String, Object> row, long user) {
    if (((Number) row.get("created_by")).longValue() != user) throw new BusinessException(ErrorCode.NOT_FOUND, "培训不存在");
  }
  private boolean enabled(Map<String, Object> row) { return Boolean.TRUE.equals(row.get("survey_enabled")) || "1".equals(String.valueOf(row.get("survey_enabled"))); }
  private long count(long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM training_survey_response WHERE training_id=?", Long.class, id); }
  private Map<String, Object> view(Map<String, Object> row) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", row.get("id")); data.put("name", row.get("name")); data.put("description", row.get("description"));
    data.put("assessmentId", row.get("assessment_id")); data.put("surveyEnabled", enabled(row)); data.put("questions", questions(row)); data.put("version", row.get("version"));
    return data;
  }
  public List<Map<String, Object>> list(long user) {
    return jdbc.queryForList("SELECT * FROM training WHERE created_by=? ORDER BY id DESC", user).stream().map(r -> {
      var v = view(r); v.put("responseCount", count(((Number) r.get("id")).longValue())); return v;
    }).toList();
  }
  public Map<String, Object> get(long id, long user) {
    var r = row(id, false); owner(r, user); var v = view(r); v.put("responseCount", count(id)); return v;
  }
  @Transactional
  public Map<String, Object> save(Long id, SaveTraining command, long user) {
    if (command.name() == null || command.name().isBlank() || command.name().length() > 160 || (command.description() != null && command.description().length() > 2000))
      throw new BusinessException(ErrorCode.BAD_REQUEST, "请检查培训名称和简介长度");
    if (command.assessmentId() != null && jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=? AND created_by=?", Integer.class, command.assessmentId(), user) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "只能关联自己创建的考试");
    TrainingSurveyRules.questions(command.questions());
    if (command.surveyEnabled() && command.questions().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "开启评估时至少添加一道题");
    if (id == null) {
      jdbc.update("INSERT INTO training(name,description,survey_enabled,questions_json,created_by) VALUES(?,?,?,?,?)", command.name().trim(), Objects.toString(command.description(), ""), command.surveyEnabled(), encode(command.questions()), user);
      id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    } else {
      var old = row(id, true); owner(old, user);
      if (!Objects.equals(command.version(), ((Number) old.get("version")).intValue())) throw new BusinessException(ErrorCode.BAD_REQUEST, "培训已被修改，请刷新后重试");
      if (count(id) > 0 && !questions(old).equals(command.questions())) throw new BusinessException(ErrorCode.BAD_REQUEST, "已有反馈，不能修改题目；请新建培训");
      jdbc.update("UPDATE training SET name=?,description=?,survey_enabled=?,questions_json=?,version=version+1 WHERE id=?", command.name().trim(), Objects.toString(command.description(), ""), command.surveyEnabled(), encode(command.questions()), id);
    }
    jdbc.update("UPDATE training SET assessment_id=? WHERE id=?", command.assessmentId(), id);
    return get(id, user);
  }
  @Transactional
  public void delete(long id, long user) {
    var r = row(id, true); owner(r, user);
    if (count(id) > 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "已有反馈，不能删除培训，可关闭评估");
    jdbc.update("DELETE FROM training WHERE id=?", id);
  }
  public Map<String, Object> survey(long id, long user) {
    var r = row(id, false);
    if (!enabled(r)) throw new BusinessException(ErrorCode.BAD_REQUEST, "此培训尚未开启或已关闭评估");
    var v = view(r);
    v.put("submitted", jdbc.queryForObject("SELECT COUNT(*) FROM training_survey_response WHERE training_id=? AND user_id=?", Integer.class, id, user) > 0);
    return v;
  }
  @Transactional
  public void submit(long id, Submission command, long user) {
    var r = row(id, true);
    if (!enabled(r)) throw new BusinessException(ErrorCode.BAD_REQUEST, "评估已关闭");
    if (!Objects.equals(command.version(), ((Number) r.get("version")).intValue())) throw new BusinessException(ErrorCode.BAD_REQUEST, "评估内容已更新，请刷新页面后填写");
    TrainingSurveyRules.answers(questions(r), command.answers());
    if (jdbc.queryForObject("SELECT COUNT(*) FROM training_survey_response WHERE training_id=? AND user_id=?", Integer.class, id, user) > 0)
      throw new BusinessException(ErrorCode.BAD_REQUEST, "你已提交过本次评估");
    jdbc.update("INSERT INTO training_survey_response(training_id,user_id,answers_json) VALUES(?,?,?)", id, user, encode(command.answers()));
  }
  public List<Map<String, Object>> responses(long id, long user) {
    owner(row(id, false), user);
    return jdbc.queryForList("SELECT r.id,u.name respondentName,r.answers_json,r.created_at submittedAt FROM training_survey_response r JOIN app_user u ON u.id=r.user_id WHERE r.training_id=? ORDER BY r.id DESC", id).stream().map(r -> {
      try { r.put("answers", json.readValue(r.remove("answers_json").toString(), new TypeReference<Map<String,String>>() {})); return r; }
      catch (Exception e) { throw new IllegalStateException(e); }
    }).toList();
  }
  public Map<String,Object> learning(long id, long user) {
    var data = view(row(id, false));
    data.put("videos", jdbc.queryForList("SELECT v.id,v.title,COALESCE(p.position_seconds,0) positionSeconds,COALESCE(p.completed,false) completed FROM training_video v LEFT JOIN training_video_progress p ON p.video_id=v.id AND p.user_id=? WHERE v.training_id=? ORDER BY v.sort_order,v.id", user,id));
    return data;
  }
  public void progress(long id,long video,long user,double position,boolean completed) {
    video(id,video);
    if (!Double.isFinite(position) || position < 0 || position > 86400) throw new BusinessException(ErrorCode.BAD_REQUEST);
    jdbc.update("INSERT INTO training_video_progress(video_id,user_id,position_seconds,completed) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE position_seconds=VALUES(position_seconds),completed=completed OR VALUES(completed)",video,user,position,completed);
  }
  public Map<String,Object> video(long id,long video) {
    var rows=jdbc.queryForList("SELECT * FROM training_video WHERE training_id=? AND id=?",id,video);
    if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND);
    return rows.get(0);
  }
  public void addVideo(long id,long user,String title,String path,String type) {
    owner(row(id,false),user);
    jdbc.update("INSERT INTO training_video(training_id,title,storage_path,content_type) VALUES(?,?,?,?)",id,title,path,type);
  }
  @Transactional
  public String removeVideo(long id,long video,long user) {
    owner(row(id,true),user);
    var data=video(id,video);
    jdbc.update("DELETE FROM training_video WHERE id=? AND training_id=?",video,id);
    return data.get("storage_path").toString();
  }
  @Transactional
  public void reorderVideos(long id,long user,List<Long> ids) {
    owner(row(id,true),user);
    var current=jdbc.queryForList("SELECT id FROM training_video WHERE training_id=?",Long.class,id);
    if(ids==null || ids.stream().anyMatch(Objects::isNull) || ids.size()!=current.size() || new HashSet<>(ids).size()!=ids.size() || !new HashSet<>(ids).equals(new HashSet<>(current)))
      throw new BusinessException(ErrorCode.BAD_REQUEST,"视频列表已变更，请刷新后重新排序");
    for(int i=0;i<ids.size();i++) jdbc.update("UPDATE training_video SET sort_order=? WHERE training_id=? AND id=?",i,id,ids.get(i));
  }
  public List<String> videoFiles(long id,long user) {
    owner(row(id,false),user);
    return jdbc.queryForList("SELECT storage_path FROM training_video WHERE training_id=?",String.class,id);
  }
}
