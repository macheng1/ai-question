package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingFlowTest {
  private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
  private final TrainingService service=new TrainingService(jdbc,new ObjectMapper());
  @Test void futureTrainingCannotStartLearning() {
    when(jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE id=? AND start_at>CURRENT_TIMESTAMP(3)", Integer.class, 1L)).thenReturn(1);
    var error = assertThrows(BusinessException.class,
        () -> TrainingAudienceService.requireTask(jdbc, -1L, "张三", "13900000000"));
    assertEquals("培训尚未开始", error.getMessage());
  }
  @Test void rejectsInvalidTrainingTimeRangeBeforeDatabaseAccess() {
    var start = java.time.LocalDateTime.of(2026, 11, 6, 12, 0);
    for (var end : List.of(start, start.minusMinutes(1))) {
      assertThrows(BusinessException.class, () -> service.save(null,
          new TrainingService.SaveTraining("培训", "", false, List.of(), null, null,
              null, null, null, end, start), 2L));
    }
    verifyNoInteractions(jdbc);
  }
  @Test void rejectsUnlinkAfterLearning() {
    when(jdbc.queryForList("SELECT * FROM training WHERE id=?",1L)).thenReturn(List.of(Map.of("created_by",2L,"assessment_id",9L)));
    when(jdbc.queryForList("SELECT * FROM training WHERE id=? FOR UPDATE",1L)).thenReturn(List.of(Map.of("created_by",2L,"assessment_id",9L)));
    when(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_learning_session s WHERE s.training_id=? OR s.task_id=?",Integer.class,1L,9L)).thenReturn(1);
    assertThrows(BusinessException.class,()->service.save(1L,new TrainingService.SaveTraining("培训","",false,List.of(),1,null,null,null,null,null,null),2L));
    verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
  @Test void examTrainingCannotUseStandaloneEntry() {
    when(jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE id=? AND assessment_id IS NOT NULL",Integer.class,1L)).thenReturn(1);
    assertThrows(BusinessException.class,()->TrainingAudienceService.requireTask(jdbc,-1L,"张三","13900000000"));
  }
  @Test void rejectsUnknownClassificationBeforeWriting() {
    for(String category:List.of("INVALID", "")) {
      assertThrows(BusinessException.class,()->service.save(null,new TrainingService.SaveTraining("培训","",false,List.of(),null,null,category,"REQUIRED",null,null,null),2L));
    }
    assertThrows(BusinessException.class,()->service.save(null,new TrainingService.SaveTraining("培训","",false,List.of(),null,null,"SAFETY","INVALID",null,null,null),2L));
    verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
  @Test void rejectsAssessmentBelongingToAnotherUser() {
    when(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=? AND created_by=?",Integer.class,9L,2L)).thenReturn(0);
    assertThrows(BusinessException.class,()->service.save(null,new TrainingService.SaveTraining("培训","",false,List.of(),null,9L,null,null,null,null,null),2L));
    verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
  @Test void videoMutationRequiresTrainingOwner() {
    when(jdbc.queryForList("SELECT * FROM training WHERE id=? FOR UPDATE",1L)).thenReturn(List.of(Map.of("created_by",3L)));
    assertThrows(BusinessException.class,()->service.removeVideo(1L,8L,2L));
    assertThrows(BusinessException.class,()->service.reorderVideos(1L,2L,List.of(8L)));
    verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
  @Test void uploadRequiresTrainingOwner() {
    when(jdbc.queryForList("SELECT * FROM training WHERE id=?",1L)).thenReturn(List.of(Map.of("created_by",3L)));
    assertThrows(BusinessException.class,()->service.addVideo(1L,2L,"video","key.mp4","video/mp4"));
  }
  @Test void preventsCrossTrainingProgressAndRejectsUnboundedPositions() {
    when(jdbc.queryForList("SELECT * FROM training WHERE id=?",1L)).thenReturn(List.of(Map.of("created_by",2L)));
    when(jdbc.queryForList("SELECT * FROM training_video WHERE training_id=? AND id=?",1L,8L)).thenReturn(List.of());
    assertThrows(BusinessException.class,()->service.progress(1L,8L,2L,10,false));
    when(jdbc.queryForList("SELECT * FROM training_video WHERE training_id=? AND id=?",1L,8L)).thenReturn(List.of(Map.of("id",8L)));
    for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,86401}) assertThrows(BusinessException.class,()->service.progress(1L,8L,2L,value,false));
  }
  @Test void progressUsesAuthenticatedUserAndMonotonicCompletion() {
    when(jdbc.queryForList("SELECT * FROM training WHERE id=?",1L)).thenReturn(List.of(Map.of("created_by",2L)));
    when(jdbc.queryForList("SELECT * FROM training_video WHERE training_id=? AND id=?",1L,8L)).thenReturn(List.of(Map.of("id",8L)));
    service.progress(1L,8L,2L,15,true);
    verify(jdbc).update("INSERT INTO training_video_progress(video_id,user_id,position_seconds,completed) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE position_seconds=VALUES(position_seconds),completed=completed OR VALUES(completed)",8L,2L,15d,true);
  }
  @Test void reorderRequiresExactVideoSet() {
    when(jdbc.queryForList("SELECT * FROM training WHERE id=? FOR UPDATE",1L)).thenReturn(List.of(Map.of("created_by",2L)));
    when(jdbc.queryForList("SELECT id FROM training_video WHERE training_id=?",Long.class,1L)).thenReturn(List.of(8L,9L));
    assertThrows(BusinessException.class,()->service.reorderVideos(1L,2L,List.of(8L,8L)));
    assertThrows(BusinessException.class,()->service.reorderVideos(1L,2L,List.of(8L,10L)));
    service.reorderVideos(1L,2L,List.of(9L,8L));
    verify(jdbc).update("UPDATE training_video SET sort_order=? WHERE training_id=? AND id=?",0,1L,9L);
  }
}
