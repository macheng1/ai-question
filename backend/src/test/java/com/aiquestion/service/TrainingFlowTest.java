package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingFlowTest {
  private final TrainingRepository repository = mock(TrainingRepository.class);
  private final TrainingAudienceRepository audienceRepository = mock(TrainingAudienceRepository.class);
  private final TrainingAudienceService audience = new TrainingAudienceService(audienceRepository, mock(TrainingEligibilityService.class));
  private final TrainingService service = new TrainingService(repository, new ObjectMapper(), mock(AssessmentRosterService.class), audience);

  @Test void futureTrainingCannotStartLearning() {
    when(audienceRepository.countNotStartedTrainings(1L, false)).thenReturn(List.of(1));
    var error = assertThrows(BusinessException.class, () -> audience.requireTask(-1L, "张三", "13900000000"));
    assertEquals("培训尚未开始", error.getMessage());
  }
  @Test void rejectsInvalidTrainingTimeRangeBeforeDatabaseAccess() {
    var start = java.time.LocalDateTime.of(2026, 11, 6, 12, 0);
    for (var end : List.of(start, start.minusMinutes(1))) {
      assertThrows(BusinessException.class, () -> service.save(null,
          new TrainingService.SaveTraining("培训", "", false, List.of(), null, null, null, null, null, end, start), 2L));
    }
    verifyNoInteractions(repository);
  }
  @Test void rejectsUnlinkAfterLearning() {
    when(repository.findById(eq(1L), anyBoolean())).thenReturn(List.of(Map.of("created_by", 2L, "assessment_id", 9L)));
    when(repository.countLearningBeforeRebind(1L, 9L)).thenReturn(List.of(1));
    assertThrows(BusinessException.class, () -> service.save(1L, new TrainingService.SaveTraining("培训", "", false, List.of(), 1, null, null, null, null, null, null), 2L));
    assertNoContentWrite();
  }
  @Test void examTrainingCannotUseStandaloneEntry() {
    when(audienceRepository.countNotStartedTrainings(1L, false)).thenReturn(List.of(0));
    when(audienceRepository.countExamLinkedTrainings(1L)).thenReturn(List.of(1));
    assertThrows(BusinessException.class, () -> audience.requireTask(-1L, "张三", "13900000000"));
  }
  @Test void rejectsUnknownClassificationBeforeWriting() {
    for (String category : List.of("INVALID", "")) {
      assertThrows(BusinessException.class, () -> service.save(null, new TrainingService.SaveTraining("培训", "", false, List.of(), null, null, category, "REQUIRED", null, null, null), 2L));
    }
    assertThrows(BusinessException.class, () -> service.save(null, new TrainingService.SaveTraining("培训", "", false, List.of(), null, null, "SAFETY", "INVALID", null, null, null), 2L));
    assertNoContentWrite();
  }
  @Test void rejectsAssessmentBelongingToAnotherUser() {
    when(repository.countOwnedAssessments(9L, 2L)).thenReturn(List.of(0));
    assertThrows(BusinessException.class, () -> service.save(null, new TrainingService.SaveTraining("培训", "", false, List.of(), null, 9L, null, null, null, null, null), 2L));
    assertNoContentWrite();
  }
  @Test void videoMutationRequiresTrainingOwner() {
    when(repository.findById(1L, true)).thenReturn(List.of(Map.of("created_by", 3L)));
    assertThrows(BusinessException.class, () -> service.removeVideo(1L, 8L, 2L));
    assertThrows(BusinessException.class, () -> service.reorderVideos(1L, 2L, List.of(8L)));
    verify(repository, never()).deleteVideo(any(), any());
    verify(repository, never()).updateVideoOrder(anyInt(), any(), any());
  }
  @Test void uploadRequiresTrainingOwner() {
    when(repository.findById(1L, false)).thenReturn(List.of(Map.of("created_by", 3L)));
    assertThrows(BusinessException.class, () -> service.addVideo(1L, 2L, "video", "key.mp4", "video/mp4"));
  }
  @Test void preventsCrossTrainingProgressAndRejectsUnboundedPositions() {
    when(repository.findById(1L, false)).thenReturn(List.of(Map.of("created_by", 2L)));
    assertThrows(BusinessException.class, () -> service.progress(1L, 8L, 2L, 10, false));
    when(repository.findVideo(1L, 8L)).thenReturn(List.of(Map.of("id", 8L)));
    for (double value : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, 86401})
      assertThrows(BusinessException.class, () -> service.progress(1L, 8L, 2L, value, false));
  }
  @Test void progressUsesAuthenticatedUserAndMonotonicCompletion() {
    when(repository.findById(1L, false)).thenReturn(List.of(Map.of("created_by", 2L)));
    when(repository.findVideo(1L, 8L)).thenReturn(List.of(Map.of("id", 8L)));
    service.progress(1L, 8L, 2L, 15, true);
    verify(repository).saveAdministratorVideoProgress(8L, 2L, 15d, true);
  }
  @Test void reorderRequiresExactVideoSet() {
    when(repository.findById(1L, true)).thenReturn(List.of(Map.of("created_by", 2L)));
    when(repository.findVideoIds(1L)).thenReturn(List.of(8L, 9L));
    assertThrows(BusinessException.class, () -> service.reorderVideos(1L, 2L, List.of(8L, 8L)));
    assertThrows(BusinessException.class, () -> service.reorderVideos(1L, 2L, List.of(8L, 10L)));
    service.reorderVideos(1L, 2L, List.of(9L, 8L));
    verify(repository).updateVideoOrder(0, 1L, 9L);
  }
  private void assertNoContentWrite() {
    verify(repository, never()).insertTraining(any(), any(), anyBoolean(), any(), any(), any());
    verify(repository, never()).updateContent(any(), any(), anyBoolean(), any(), any());
    verify(repository, never()).updateSettings(any(), any(), any(), any(), any(), any(), any());
  }
}
