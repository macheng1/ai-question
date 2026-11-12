package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.aiquestion.repository.TrainingLogRepository;

class TrainingLogServiceTest {
  @Test
  void materialFreeTrainingNeedsExplicitCompletionInManagementLog() {
    TrainingLogRepository jdbc = mock(TrainingLogRepository.class);
    TrainingService trainings = mock(TrainingService.class);
    Map<String, Object> session = new HashMap<>();
    session.put("id", 1L);
    session.put("name", "张明");
    session.put("phone", "13900001001");
    when(jdbc.findLearningSessions(23L)).thenReturn(List.of(session));
    TrainingLogService service = new TrainingLogService(jdbc, trainings);
    assertEquals(false, service.list(23L, 1L).get(0).get("completed"));
    session.put("completedAt", java.time.LocalDateTime.now());
    assertEquals(true, service.list(23L, 1L).get(0).get("completed"));
  }
}
