package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aiquestion.common.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.aiquestion.repository.TrainingEligibilityRepository;

class TrainingEligibilityServiceTest {
  @Test void standaloneHasNoExclusions() {
    var jdbc = mock(TrainingEligibilityRepository.class);
    assertTrue(new TrainingEligibilityService(jdbc).excludedEmployees(null, 2L).isEmpty());
    verifyNoInteractions(jdbc);
  }

  @Test void rejectsExamFromAnotherEnterprise() {
    var jdbc = mock(TrainingEligibilityRepository.class);
    assertThrows(BusinessException.class,
        () -> new TrainingEligibilityService(jdbc).excludedEmployees(9L, 2L));
  }

  @Test void submittedEmployeesAreDeduplicatedForOwnedPaper() {
    var jdbc = mock(TrainingEligibilityRepository.class);
    when(jdbc.findOwnedTaskPaper(9L, 2L))
        .thenReturn(List.of(Map.of("paper_id", 30L)));
    when(jdbc.findSubmittedEmployees(2L, 30L))
        .thenReturn(List.of(3L, 3L, 4L));
    assertEquals(java.util.Set.of(3L, 4L), new TrainingEligibilityService(jdbc).excludedEmployees(9L, 2L));
  }
}
