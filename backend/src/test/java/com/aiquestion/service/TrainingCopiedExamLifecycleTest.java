package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.TrainingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingCopiedExamLifecycleTest {
  private final TrainingRepository repository = mock(TrainingRepository.class);
  private final AssessmentRosterService roster = mock(AssessmentRosterService.class);
  private final TrainingAudienceService audiences = mock(TrainingAudienceService.class);
  private final TrainingService service = spy(new TrainingService(repository, new ObjectMapper(), roster, audiences));

  private void training(boolean generated) {
    Map<String, Object> row = new HashMap<>();
    row.put("created_by", 2L); row.put("assessment_id", 9L); row.put("version", 1);
    if (generated) row.put("copied_assessment_id", 9L);
    when(repository.findById(eq(1L), anyBoolean())).thenReturn(List.of(row));
    when(repository.countSurveyResponses(1L)).thenReturn(List.of(0L));
    when(repository.countUpcomingTrainings(1L)).thenReturn(List.of(1));
    when(repository.countOtherTrainingReferences(anyLong(), anyLong())).thenReturn(List.of(0));
    when(repository.countAssessmentActivity(anyLong(), anyLong())).thenReturn(List.of(0L));
    when(repository.countLearningBeforeRebind(1L, 9L)).thenReturn(List.of(0));
    when(repository.countParticipantsBeforeRebind(9L)).thenReturn(List.of(0));
    when(repository.countParticipantsBeforeDelete(9L)).thenReturn(List.of(0));
    when(repository.countSessionsBeforeDelete(1L)).thenReturn(List.of(0));
    when(repository.countMaterials(1L)).thenReturn(List.of(0));
    when(repository.countAssessmentReferences(9L)).thenReturn(List.of(0));
    when(repository.countCopiedExamParticipants(9L)).thenReturn(List.of(0));
    when(repository.countCopiedExamSessions(9L)).thenReturn(List.of(0));
    doReturn(Map.of("id", 1L)).when(service).get(1L, 2L);
  }

  private void save(Long exam) {
    when(repository.countOwnedAssessments(exam, 2L)).thenReturn(List.of(1));
    service.save(1L, new TrainingService.SaveTraining("培训", "", false, List.of(), 1,
        exam, null, null, null, null, null), 2L);
  }

  @Test void unlinkDeletesGeneratedExam() {
    training(true); save(null);
    verify(repository).deleteOwnedAssessment(9L, 2L);
  }

  @Test void rebindDeletesOnlyGeneratedExam() {
    training(true); save(10L);
    verify(repository).deleteOwnedAssessment(9L, 2L);
    verify(repository, never()).deleteOwnedAssessment(10L, 2L);
  }

  @Test void unchangedAssociationKeepsGeneratedExam() {
    training(true); save(9L);
    verify(repository, never()).deleteOwnedAssessment(any(), any());
  }

  @Test void deletingCopyDeletesGeneratedExam() {
    training(true); service.delete(1L, 2L);
    var order = inOrder(repository);
    order.verify(repository).deleteTraining(1L);
    order.verify(repository).deleteOwnedAssessment(9L, 2L);
  }

  @Test void manuallyLinkedExamSurvivesUnlinkAndDelete() {
    training(false); save(null); service.delete(1L, 2L);
    verify(repository, never()).deleteOwnedAssessment(any(), any());
  }

  @Test void learningPreventsUnlink() {
    training(true);
    when(repository.countLearningBeforeRebind(1L, 9L)).thenReturn(List.of(1));
    assertThrows(BusinessException.class, () -> save(null));
    assertNoContentWrite();
  }

  @Test void failedAudienceValidationDoesNotDeleteExam() {
    training(true);
    doThrow(new IllegalArgumentException("范围无效")).when(audiences).save(anyLong(), anyLong(), any());
    assertThrows(IllegalArgumentException.class, () -> save(null));
    verify(repository, never()).deleteOwnedAssessment(any(), any());
  }

  @Test void concurrentRebindPreventsDeletion() {
    training(true);
    when(repository.findById(1L, true)).thenReturn(List.of(Map.of("created_by", 2L, "assessment_id", 10L)));
    assertThrows(BusinessException.class, () -> service.delete(1L, 2L));
    assertNoContentWrite();
  }

  @Test void concurrentRebindPreventsSave() {
    training(true);
    when(repository.findById(1L, true)).thenReturn(List.of(Map.of("created_by", 2L, "assessment_id", 10L)));
    assertThrows(BusinessException.class, () -> save(null));
    assertNoContentWrite();
  }

  @Test void participantPreventsUnlink() {
    training(true);
    when(repository.countParticipantsBeforeRebind(9L)).thenReturn(List.of(1));
    assertThrows(BusinessException.class, () -> save(null));
    assertNoContentWrite();
  }

  @Test void anotherTrainingReferencePreventsExamRemoval() {
    training(true);
    when(repository.countAssessmentReferences(9L)).thenReturn(List.of(1));
    assertThrows(BusinessException.class, () -> save(null));
    verify(repository, never()).deleteOwnedAssessment(any(), any());
  }

  @Test void participantPreventsDeletion() {
    training(true);
    when(repository.countParticipantsBeforeDelete(9L)).thenReturn(List.of(1));
    assertThrows(BusinessException.class, () -> service.delete(1L, 2L));
    verify(repository, never()).deleteTraining(any());
    verify(repository, never()).deleteOwnedAssessment(any(), any());
  }

  @Test void trainingThatIsNotUpcomingCannotBeDeleted() {
    training(true);
    when(repository.countUpcomingTrainings(1L)).thenReturn(List.of(0));
    var error = assertThrows(BusinessException.class, () -> service.delete(1L, 2L));
    assertEquals("只有未开始的培训可以删除", error.getMessage());
    assertNoContentWrite();
  }

  private void assertNoContentWrite() {
    verify(repository, never()).updateContent(any(), any(), anyBoolean(), any(), any());
    verify(repository, never()).updateSettings(any(), any(), any(), any(), any(), any(), any());
    verify(repository, never()).deleteTraining(any());
    verify(repository, never()).deleteOwnedAssessment(any(), any());
  }
}
