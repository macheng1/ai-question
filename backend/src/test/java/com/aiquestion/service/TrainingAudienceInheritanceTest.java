package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.TrainingAudienceRepository;
import java.util.*;
import org.junit.jupiter.api.Test;

class TrainingAudienceInheritanceTest {
  @Test void lockedScopeCanBeSavedUnchangedButCannotBeReplaced() {
    var repository = mock(TrainingAudienceRepository.class);
    var service = new TrainingAudienceService(repository, mock(TrainingEligibilityService.class));
    when(repository.countOwnedTrainings(1L, 2L)).thenReturn(List.of(1));
    when(repository.lockTraining(1L)).thenReturn(List.of(Map.of("assessment_id", 9L)));
    when(repository.findAudienceType(1L)).thenReturn(List.of("USERS"));
    when(repository.findScopeTargetIds(1L)).thenReturn(List.of(3L));
    when(repository.countLearningSessions(1L)).thenReturn(List.of(0));
    when(repository.countExamParticipants(9L)).thenReturn(List.of(1));
    assertDoesNotThrow(() -> service.save(1L, 2L, new TrainingAudienceService.Scope("USERS", List.of(3L))));
    assertThrows(BusinessException.class, () -> service.save(1L, 2L, new TrainingAudienceService.Scope("USERS", List.of(4L))));
    verify(repository, never()).deleteAudience(anyLong());
    verify(repository, never()).deleteScopeTargets(anyLong());
    verify(repository, never()).updateAudienceType(anyString(), anyLong());
    verify(repository, never()).insertAssignment(any(), any(), any(), any(), any(), any(), any());
  }
}
