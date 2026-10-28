package com.aiquestion.service;

import com.aiquestion.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CandidateLearningService {
 private final JdbcTemplate jdbc;
 public CandidateLearningService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public static void validate(String name,String phone) {
  if(name==null || name.isBlank() || phone==null || phone.isBlank() || name.trim().length()>100 || phone.trim().length()>50 || !phone.trim().matches("^1[0-9]{10}$")) throw new BusinessException(ErrorCode.BAD_REQUEST,"请填写姓名和手机号");
 }
 public List<Map<String,Object>> trainings(long task) { return jdbc.queryForList("SELECT id,name,survey_enabled surveyEnabled FROM training WHERE assessment_id=? ORDER BY id",task); }
 public void requireComplete(long task,String name,String phone) {
  Integer missing=jdbc.queryForObject("SELECT COUNT(*) FROM training_video v JOIN training t ON t.id=v.training_id WHERE t.assessment_id=? AND NOT EXISTS (SELECT 1 FROM candidate_video_progress p JOIN candidate_learning_session s ON s.id=p.session_id WHERE p.video_id=v.id AND p.completed=TRUE AND s.task_id=? AND s.candidate_name=? AND s.phone=?)",Integer.class,task,task,name,phone);
  if(missing!=null && missing>0) throw new BusinessException(ErrorCode.BAD_REQUEST,"请先完成全部培训视频学习");
 }
 @Transactional
 public Map<String,Object> learning(long task,String name,String phone) {
  validate(name,phone);
  if(jdbc.queryForList("SELECT id FROM assessment_task WHERE id=?",task).isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"考试不存在");
  jdbc.update("INSERT INTO candidate_learning_session(task_id,candidate_name,phone,token) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE id=id",task,name.trim(),phone.trim(),UUID.randomUUID().toString());
  var session=jdbc.queryForMap("SELECT id,token FROM candidate_learning_session WHERE task_id=? AND candidate_name=? AND phone=?",task,name.trim(),phone.trim());
  jdbc.update("INSERT IGNORE INTO candidate_video_progress(session_id,video_id) SELECT ?,v.id FROM training_video v JOIN training t ON t.id=v.training_id WHERE t.assessment_id=?",session.get("id"),task);
  var videos=jdbc.queryForList("SELECT v.id,v.training_id trainingId,v.title,p.position_seconds positionSeconds,p.completed FROM training_video v JOIN training t ON t.id=v.training_id JOIN candidate_video_progress p ON p.video_id=v.id AND p.session_id=? WHERE t.assessment_id=? ORDER BY t.id,v.sort_order,v.id",session.get("id"),task);
  return Map.of("videos",videos,"trainings",trainings(task),"token",session.get("token"));
 }
 public Map<String,Object> video(long task,long video,String token) {
  var rows=jdbc.queryForList("SELECT v.*,p.session_id FROM training_video v JOIN training t ON t.id=v.training_id JOIN candidate_video_progress p ON p.video_id=v.id JOIN candidate_learning_session s ON s.id=p.session_id WHERE t.assessment_id=? AND s.task_id=? AND s.token=? AND v.id=?",task,task,token,video);
  if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"视频或学习凭证无效");
  return rows.get(0);
 }
 public static void validateProgress(double position,double duration,double previous,double elapsed,boolean completed) {
  if(!Double.isFinite(position)||!Double.isFinite(duration)||position<0||duration<=0||position>duration+1||position>previous+elapsed+2|| (completed && position<duration-Math.min(1,duration*0.02))) throw new BusinessException(ErrorCode.BAD_REQUEST,"视频进度无效，请按顺序观看");
 }
 @Transactional
 public Map<String,Object> progress(long task,long video,String token,double position,double duration,boolean completed) {
  var row=video(task,video,token);
  var saved=jdbc.queryForMap("SELECT position_seconds,baseline_position,completed,GREATEST(0,TIMESTAMPDIFF(MICROSECOND,baseline_at,CURRENT_TIMESTAMP(3))/1000000.0) elapsed FROM candidate_video_progress WHERE session_id=? AND video_id=? FOR UPDATE",row.get("session_id"),video);
  double previous=((Number)saved.get("position_seconds")).doubleValue();
  // The allowance belongs to the persisted baseline, never to each request.
  validateProgress(position,duration,((Number)saved.get("baseline_position")).doubleValue(),((Number)saved.get("elapsed")).doubleValue(),completed);
  boolean done=completed || Boolean.TRUE.equals(saved.get("completed")) || "1".equals(String.valueOf(saved.get("completed")));
  double next=Math.max(previous,position);
  jdbc.update("UPDATE candidate_video_progress SET position_seconds=?,completed=?,updated_at=CURRENT_TIMESTAMP(3) WHERE session_id=? AND video_id=?",next,done,row.get("session_id"),video);
  return Map.of("positionSeconds",next,"completed",done);
 }
}
