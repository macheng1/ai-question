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
 private static String trainingWhere(long task){return task>0?"t.assessment_id=?":"t.id=?";}
 private static String sessionWhere(long task){return task>0?"task_id":"training_id";}
 public List<Map<String,Object>> trainings(long task) { return jdbc.queryForList("SELECT id,name,category,requirement_type requirementType,survey_enabled surveyEnabled FROM training t WHERE "+trainingWhere(task)+" ORDER BY id",Math.abs(task)); }
 public void requireComplete(long task,String name,String phone) {
  TrainingAudienceService.requireTask(jdbc,task,name,phone);
  Integer missing=jdbc.queryForObject("SELECT COUNT(*) FROM training_video v JOIN training t ON t.id=v.training_id WHERE "+trainingWhere(task)+" AND NOT EXISTS (SELECT 1 FROM candidate_video_progress p JOIN candidate_learning_session s ON s.id=p.session_id WHERE p.video_id=v.id AND p.completed=TRUE AND s."+sessionWhere(task)+"=? AND s.candidate_name=? AND s.phone=?)",Integer.class,Math.abs(task),Math.abs(task),name,phone);
  missing += jdbc.queryForObject("SELECT COUNT(*) FROM training_material_page p JOIN training_material m ON m.id=p.material_id JOIN training t ON t.id=m.training_id WHERE "+trainingWhere(task)+" AND NOT EXISTS (SELECT 1 FROM candidate_material_progress s JOIN candidate_learning_session c ON c.id=s.session_id WHERE s.material_id=p.material_id AND s.page_number=p.page_number AND s.completed=TRUE AND c."+sessionWhere(task)+"=? AND c.candidate_name=? AND c.phone=?)",Integer.class,Math.abs(task),Math.abs(task),name,phone);
  if(missing!=null && missing>0) throw new BusinessException(ErrorCode.BAD_REQUEST,"请先完成全部培训材料学习");
  jdbc.update("UPDATE candidate_learning_session SET completed_at=COALESCE(completed_at,CURRENT_TIMESTAMP(3)) WHERE "+sessionWhere(task)+"=? AND candidate_name=? AND phone=?",Math.abs(task),name,phone);
 }
 @Transactional
 public Map<String,Object> learning(long task,String name,String phone) {
  validate(name,phone);
  if(task>0){
   new AssessmentRosterService(jdbc).lockTask(task);
   jdbc.queryForList("SELECT id FROM training WHERE assessment_id=? ORDER BY id FOR UPDATE",task);
   new AssessmentRosterService(jdbc).requireEligible(task,name,phone);
  }else jdbc.queryForMap("SELECT id FROM training WHERE id=? FOR UPDATE",-task);
  TrainingAudienceService.requireTask(jdbc,task,name,phone);
  if(task>0 && jdbc.queryForList("SELECT id FROM assessment_task WHERE id=?",task).isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"考试不存在");
  jdbc.update("INSERT INTO candidate_learning_session("+sessionWhere(task)+",candidate_name,phone,token) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE id=id",Math.abs(task),name.trim(),phone.trim(),UUID.randomUUID().toString());
  jdbc.update("UPDATE candidate_learning_session s JOIN training t ON (t.id=s.training_id OR t.assessment_id=s.task_id) JOIN training_audience a ON a.training_id=t.id AND a.candidate_name=s.candidate_name AND a.phone=s.phone SET s.employee_id=a.employee_id WHERE s.employee_id IS NULL AND s."+sessionWhere(task)+"=? AND s.candidate_name=? AND s.phone=?",Math.abs(task),name.trim(),phone.trim());
  var session=jdbc.queryForMap("SELECT id,token FROM candidate_learning_session WHERE "+sessionWhere(task)+"=? AND candidate_name=? AND phone=?",Math.abs(task),name.trim(),phone.trim());
  jdbc.update("INSERT IGNORE INTO candidate_video_progress(session_id,video_id) SELECT ?,v.id FROM training_video v JOIN training t ON t.id=v.training_id WHERE "+trainingWhere(task),session.get("id"),Math.abs(task));
  var videos=jdbc.queryForList("SELECT v.id,v.training_id trainingId,v.title,p.position_seconds positionSeconds,p.completed FROM training_video v JOIN training t ON t.id=v.training_id JOIN candidate_video_progress p ON p.video_id=v.id AND p.session_id=? WHERE "+trainingWhere(task)+" ORDER BY t.id,v.sort_order,v.id",session.get("id"),Math.abs(task));
  return Map.of("videos",videos,"materials",new MaterialLearningService(jdbc).list(task,((Number)session.get("id")).longValue()),"trainings",trainings(task),"token",session.get("token"));
 }
 public Map<String,Object> video(long task,long video,String token) {
  var rows=jdbc.queryForList("SELECT v.*,p.session_id FROM training_video v JOIN training t ON t.id=v.training_id JOIN candidate_video_progress p ON p.video_id=v.id JOIN candidate_learning_session s ON s.id=p.session_id WHERE "+trainingWhere(task)+" AND s."+sessionWhere(task)+"=? AND s.token=? AND v.id=?",Math.abs(task),Math.abs(task),token,video);
  if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"视频或学习凭证无效");
  var identity=jdbc.queryForMap("SELECT candidate_name,phone FROM candidate_learning_session WHERE token=? AND "+sessionWhere(task)+"=?",token,Math.abs(task));
  if(task>0)new AssessmentRosterService(jdbc).requireEligible(task,String.valueOf(identity.get("candidate_name")),String.valueOf(identity.get("phone")));
  TrainingAudienceService.requireTask(jdbc,task,String.valueOf(identity.get("candidate_name")),String.valueOf(identity.get("phone")));
  return rows.get(0);
 }
 public static void validateProgress(double position,double duration,double previous,double elapsed,boolean completed) {
  if(!Double.isFinite(position)||!Double.isFinite(duration)||position<0||duration<=0||position>duration+1||position>previous+elapsed+2|| (completed && position<duration-Math.min(1,duration*0.02))) throw new BusinessException(ErrorCode.BAD_REQUEST,"视频进度无效，请按顺序观看");
 }
 @Transactional
 public Map<String,Object> progress(long task,long video,String token,double position,double duration,boolean completed) {
  var row=video(task,video,token);
  var saved=jdbc.queryForMap("SELECT studied_seconds,last_play_position,GREATEST(0,TIMESTAMPDIFF(MICROSECOND,updated_at,CURRENT_TIMESTAMP(3))/1000000.0) recent_elapsed,position_seconds,baseline_position,completed,GREATEST(0,TIMESTAMPDIFF(MICROSECOND,baseline_at,CURRENT_TIMESTAMP(3))/1000000.0) elapsed FROM candidate_video_progress WHERE session_id=? AND video_id=? FOR UPDATE",row.get("session_id"),video);
  double previous=((Number)saved.get("position_seconds")).doubleValue();
  // The allowance belongs to the persisted baseline, never to each request.
  validateProgress(position,duration,((Number)saved.get("baseline_position")).doubleValue(),((Number)saved.get("elapsed")).doubleValue(),completed);
  boolean done=completed || Boolean.TRUE.equals(saved.get("completed")) || "1".equals(String.valueOf(saved.get("completed")));
  double next=Math.max(previous,position);
  double elapsed=((Number)saved.get("recent_elapsed")).doubleValue();
  double watched=saved.get("studied_seconds") instanceof Number n?n.doubleValue():0;
  double delta=saved.get("last_play_position") instanceof Number n?position-n.doubleValue():0;
  double requested=elapsed<=15 && delta>0 && delta<=elapsed+1?Math.min(delta,elapsed):0;
  watched+=LearningClock.credit(jdbc,((Number)row.get("session_id")).longValue(),requested);
  jdbc.update("UPDATE candidate_video_progress SET position_seconds=?,completed=?,studied_seconds=?,last_play_position=?,first_studied_at=COALESCE(first_studied_at,CURRENT_TIMESTAMP(3)),completed_at=IF(? AND completed_at IS NULL,CURRENT_TIMESTAMP(3),completed_at),updated_at=CURRENT_TIMESTAMP(3) WHERE session_id=? AND video_id=?",next,done,watched,position,done,row.get("session_id"),video);
  return Map.of("positionSeconds",next,"completed",done);
 }
}
