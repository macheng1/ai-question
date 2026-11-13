package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.AssessmentRosterRepository;
import com.aiquestion.repository.TrainingAudienceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssessmentRosterServiceTest {
  private final AssessmentRosterRepository repository = mock(AssessmentRosterRepository.class);
  private final TrainingAudienceRepository audience = mock(TrainingAudienceRepository.class);
  private final AssessmentRosterService service = new AssessmentRosterService(repository,
      new TrainingAudienceService(audience, mock(TrainingEligibilityService.class)));

  @BeforeEach void defaultStartedTraining() {
    when(audience.countNotStartedTrainings(2L, false)).thenReturn(List.of(0));
  }
  @Test void unlinkedExamRemainsOpen() {
    when(repository.countTask(1L)).thenReturn(List.of(1));
    when(audience.findTrainingIdsForEntry(1L, true)).thenReturn(List.of());
    assertDoesNotThrow(() -> service.requireEligible(1L, "张三", "13900000000"));
  }
  @Test void linkedExamRejectsCandidateOutsideTrainingEvenWhenExamIsOpen() {
    when(repository.countTask(1L)).thenReturn(List.of(1));
    when(audience.findTrainingIdsForEntry(1L, true)).thenReturn(List.of(2L));
    when(audience.countCandidateAssignments(2L, "张三", "13900000000")).thenReturn(List.of(0));
    assertThrows(BusinessException.class, () -> service.requireEligible(1L, "张三", "13900000000"));
  }
  @Test void linkedExamAcceptsTrainingMember() {
    when(repository.countTask(1L)).thenReturn(List.of(1));
    when(audience.findTrainingIdsForEntry(1L, true)).thenReturn(List.of(2L));
    when(audience.countCandidateAssignments(2L, "张三", "13900000000")).thenReturn(List.of(1));
    assertDoesNotThrow(() -> service.requireEligible(1L, "张三", "13900000000"));
  }
  @Test void missingExamIsRejected() {
    when(repository.countTask(1L)).thenReturn(List.of(0));
    assertThrows(BusinessException.class, () -> service.requireEligible(1L, "张三", "13900000000"));
    verifyNoInteractions(audience);
  }
}
