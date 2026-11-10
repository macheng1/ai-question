package com.aiquestion.service;
import org.springframework.jdbc.core.JdbcTemplate;
/** 同一手机号跨页面、视频和学习会话共用计时上限；调用方必须位于事务内。 */
public final class LearningClock {
 private LearningClock(){}
 public static double credit(JdbcTemplate jdbc,long session,double requested){
  String phone=jdbc.queryForObject("SELECT phone FROM candidate_learning_session WHERE id=?",String.class,session);
  jdbc.update("INSERT IGNORE INTO learning_clock(phone) VALUES(?)",phone);
  var row=jdbc.queryForMap("SELECT GREATEST(0,TIMESTAMPDIFF(MICROSECOND,credited_at,CURRENT_TIMESTAMP(3))/1000000.0) elapsed FROM learning_clock WHERE phone=? FOR UPDATE",phone);
  double elapsed=((Number)row.get("elapsed")).doubleValue();
  jdbc.update("UPDATE learning_clock SET credited_at=CURRENT_TIMESTAMP(3) WHERE phone=?",phone);
  return Math.min(Math.max(0,requested),Math.min(15,elapsed));
 }
}
