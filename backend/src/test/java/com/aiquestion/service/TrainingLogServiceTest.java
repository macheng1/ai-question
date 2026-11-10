package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TrainingLogServiceTest {
  @Test
  void materialFreeTrainingNeedsExplicitCompletionInManagementLog() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    TrainingService trainings = mock(TrainingService.class);
    Map<String, Object> session = new HashMap<>();
    session.put("id", 1L);
    session.put("name", "张明");
    session.put("phone", "13900001001");
    when(jdbc.queryForList(anyString(), eq(23L))).thenReturn(List.of(session));
    TrainingLogService service = new TrainingLogService(jdbc, trainings);
    assertEquals(false, service.list(23L, 1L).get(0).get("completed"));
    session.put("completedAt", java.time.LocalDateTime.now());
    assertEquals(true, service.list(23L, 1L).get(0).get("completed"));
  }
}
