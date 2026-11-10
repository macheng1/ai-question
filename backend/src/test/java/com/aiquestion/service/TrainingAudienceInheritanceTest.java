package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiquestion.common.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TrainingAudienceInheritanceTest {
  @Test
  void lockedScopeCanBeSavedUnchangedButCannotBeReplaced() {
    var jdbc = mock(JdbcTemplate.class);
    var service = new TrainingAudienceService(jdbc);
    when(jdbc.queryForObject(
            "SELECT COUNT(*) FROM training WHERE id=? AND created_by=?", Integer.class, 1L, 2L))
        .thenReturn(1);
    when(jdbc.queryForMap("SELECT * FROM training WHERE id=? FOR UPDATE", 1L))
        .thenReturn(Map.of("assessment_id", 9L));
    when(jdbc.queryForObject("SELECT audience_type FROM training WHERE id=?", String.class, 1L))
        .thenReturn("USERS");
    when(jdbc.queryForList(
            "SELECT target_id FROM training_scope_target WHERE training_id=? ORDER BY target_id",
            Long.class,
            1L))
        .thenReturn(List.of(3L));
    when(jdbc.queryForObject(
            "SELECT COUNT(*) FROM candidate_learning_session s JOIN training t ON"
                + " (t.assessment_id=s.task_id OR t.id=s.training_id) WHERE t.id=?",
            Integer.class,
            1L))
        .thenReturn(0);
    when(jdbc.queryForObject(
            "SELECT COUNT(*) FROM assessment_participant WHERE task_id=?", Integer.class, 9L))
        .thenReturn(1);
    assertDoesNotThrow(
        () -> service.save(1L, 2L, new TrainingAudienceService.Scope("USERS", List.of(3L))));
    assertThrows(
        BusinessException.class,
        () -> service.save(1L, 2L, new TrainingAudienceService.Scope("USERS", List.of(4L))));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }
}
