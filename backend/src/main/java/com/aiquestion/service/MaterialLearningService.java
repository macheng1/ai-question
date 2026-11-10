package com.aiquestion.service;
import com.aiquestion.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialLearningService {
 private final JdbcTemplate jdbc;
 public MaterialLearningService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Map<String,Object> session(long task,String token) {
  var rows=jdbc.queryForList("SELECT * FROM candidate_learning_session WHERE "+(task>0?"task_id":"training_id")+"=? AND token=?",Math.abs(task),token);
  if(rows.isEmpty()) throw new BusinessException(ErrorCode.UNAUTHORIZED,"学习凭证无效");
  var s=rows.get(0);if(task>0)new AssessmentRosterService(jdbc).requireEligible(task,s.get("candidate_name").toString(),s.get("phone").toString());TrainingAudienceService.requireTask(jdbc,task,s.get("candidate_name").toString(),s.get("phone").toString());return s;
 }
 public List<Map<String,Object>> list(long task,long session) {
  return jdbc.queryForList("SELECT m.id,m.training_id trainingId,m.title,m.material_type materialType,m.page_count pageCount FROM training_material m JOIN training t ON t.id=m.training_id WHERE "+(task>0?"t.assessment_id=?":"t.id=?")+" ORDER BY t.id,m.sort_order,m.id",Math.abs(task)).stream().map(m->{
   var pages=jdbc.queryForList("SELECT p.page_number pageNumber,p.required_seconds requiredSeconds,COALESCE(s.studied_seconds,0) studiedSeconds,COALESCE(s.completed,FALSE) completed FROM training_material_page p LEFT JOIN candidate_material_progress s ON s.material_id=p.material_id AND s.page_number=p.page_number AND s.session_id=? WHERE p.material_id=? ORDER BY p.page_number",session,m.get("id"));
   m.put("pages",pages);m.put("completed",pages.stream().allMatch(p->Boolean.TRUE.equals(p.get("completed"))||"1".equals(String.valueOf(p.get("completed")))));return m;
  }).toList();
 }
 public long authorize(long task,long material,String token,int page) {
  var s=session(task,token);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM training_material m JOIN training t ON t.id=m.training_id WHERE m.id=? AND "+(task>0?"t.assessment_id=?":"t.id=?")+" AND ? BETWEEN 1 AND m.page_count",Integer.class,material,Math.abs(task),page)==0) throw new BusinessException(ErrorCode.NOT_FOUND);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM training_material_page p WHERE p.material_id=? AND p.page_number<? AND NOT EXISTS (SELECT 1 FROM candidate_material_progress s WHERE s.session_id=? AND s.material_id=p.material_id AND s.page_number=p.page_number AND s.completed=TRUE)",Integer.class,material,page,s.get("id"))>0) throw new BusinessException(ErrorCode.BAD_REQUEST,"请先完成前面的页面");
  return ((Number)s.get("id")).longValue();
 }
 public static double creditedSeconds(double elapsed,double reported){if(!Double.isFinite(reported)||reported<0||reported>15) throw new BusinessException(ErrorCode.BAD_REQUEST,"阅读计时无效");return elapsed>20?0:Math.min(Math.max(0,elapsed),reported);}
 @Transactional
 public Map<String,Object> heartbeat(long task,long material,int page,String token,String previous,double seconds) {
  long session=authorize(task,material,token,page);
  jdbc.queryForMap("SELECT id FROM training_material WHERE id=? FOR UPDATE",material);
  jdbc.queryForMap("SELECT id FROM candidate_learning_session WHERE id=? FOR UPDATE",session);
  jdbc.update("INSERT IGNORE INTO candidate_material_progress(session_id,material_id,page_number) VALUES(?,?,?)",session,material,page);
  var r=jdbc.queryForMap("SELECT s.*,p.required_seconds,GREATEST(0,TIMESTAMPDIFF(MICROSECOND,s.last_seen,CURRENT_TIMESTAMP(3))/1000000.0) elapsed FROM candidate_material_progress s JOIN training_material_page p ON p.material_id=s.material_id AND p.page_number=s.page_number WHERE s.session_id=? AND s.material_id=? AND s.page_number=? FOR UPDATE",session,material,page);
  double total=((Number)r.get("studied_seconds")).doubleValue();
  if(previous!=null&&previous.equals(r.get("heartbeat_token"))&&r.get("elapsed") instanceof Number elapsed) total+=LearningClock.credit(jdbc,session,creditedSeconds(elapsed.doubleValue(),seconds));
  else if(seconds!=0) throw new BusinessException(ErrorCode.BAD_REQUEST,"阅读会话已更新，请重新打开当前页");
  if(seconds==0)LearningClock.credit(jdbc,session,0);
  boolean done=total>=((Number)r.get("required_seconds")).intValue();String next=UUID.randomUUID().toString();
  // 一名学员只允许一个材料页面累计时长。
  jdbc.update("UPDATE candidate_material_progress SET heartbeat_token=NULL WHERE session_id=?",session);
  jdbc.update("UPDATE candidate_material_progress SET studied_seconds=?,completed=?,last_seen=CURRENT_TIMESTAMP(3),heartbeat_token=?,completed_at=IF(? AND completed_at IS NULL,CURRENT_TIMESTAMP(3),completed_at) WHERE session_id=? AND material_id=? AND page_number=?",total,done,next,done,session,material,page);
  return Map.of("studiedSeconds",total,"completed",done,"heartbeatToken",next);
 }
}
