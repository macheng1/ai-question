package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiquestion.common.BusinessException;
import com.aiquestion.controller.PublicExamController;
import com.aiquestion.repository.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

class AssessmentLockOrderingTest {
  @Test
  void updateLocksBeforeAnyRepositoryRead() {
    var roster = mock(AssessmentRosterService.class);
    var repository = mock(AssessmentTaskRepository.class);
    var service = new AssessmentTaskService(repository, mock(TestPaperRepository.class));
    ReflectionTestUtils.setField(service, "roster", roster);
    doThrow(new IllegalStateException("lock marker")).when(roster).lockTask(1L);
    assertThrows(
        IllegalStateException.class,
        () ->
            service.update(
                1L,
                "Exam",
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1),
                30,
                false,
                1,
                false,
                false,
                true));
    verifyNoInteractions(repository);
  }

  @Test
  void startLocksBeforeTaskReadAndConvertsMissingTask() {
    var jdbc = mock(AssessmentRosterRepository.class);
    when(jdbc.shareTask(1L))
        .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
    assertThrows(
        BusinessException.class,
        () ->
            new PublicExamService(mock(PublicExamRepository.class), new AssessmentRosterService(jdbc, mock(TrainingAudienceService.class)), mock(CandidateLearningService.class))
                .start(1L, new PublicExamController.CandidateCommand("Alice", "13800000000")));
    verify(jdbc)
        .shareTask(1L);
    verifyNoMoreInteractions(jdbc);
  }
}
