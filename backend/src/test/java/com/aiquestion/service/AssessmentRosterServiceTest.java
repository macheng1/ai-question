package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiquestion.common.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AssessmentRosterServiceTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AssessmentRosterService service = new AssessmentRosterService(jdbc);

  @Test
  void unlinkedExamRemainsOpen() {
    when(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=?", Integer.class, 1L))
        .thenReturn(1);
    when(jdbc.queryForList("SELECT id FROM training WHERE assessment_id=?", Long.class, 1L))
        .thenReturn(List.of());
    assertDoesNotThrow(() -> service.requireEligible(1L, "张三", "13900000000"));
  }

  @Test
  void linkedExamRejectsCandidateOutsideTrainingEvenWhenExamIsOpen() {
    when(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=?", Integer.class, 1L))
        .thenReturn(1);
    when(jdbc.queryForList("SELECT id FROM training WHERE assessment_id=?", Long.class, 1L))
        .thenReturn(List.of(2L));
    when(jdbc.queryForObject(
            "SELECT COUNT(*) FROM training_audience WHERE training_id=? AND candidate_name=? AND"
                + " phone=?",
            Integer.class,
            2L,
            "张三",
            "13900000000"))
        .thenReturn(0);
    assertThrows(BusinessException.class, () -> service.requireEligible(1L, "张三", "13900000000"));
  }

  @Test
  void linkedExamAcceptsTrainingMember() {
    when(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=?", Integer.class, 1L))
        .thenReturn(1);
    when(jdbc.queryForList("SELECT id FROM training WHERE assessment_id=?", Long.class, 1L))
        .thenReturn(List.of(2L));
    when(jdbc.queryForObject(
            "SELECT COUNT(*) FROM training_audience WHERE training_id=? AND candidate_name=? AND"
                + " phone=?",
            Integer.class,
            2L,
            "张三",
            "13900000000"))
        .thenReturn(1);
    assertDoesNotThrow(() -> service.requireEligible(1L, "张三", "13900000000"));
  }

  @Test
  void missingExamIsRejected() {
    when(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE id=?", Integer.class, 1L))
        .thenReturn(0);
    assertThrows(BusinessException.class, () -> service.requireEligible(1L, "张三", "13900000000"));
  }
}
