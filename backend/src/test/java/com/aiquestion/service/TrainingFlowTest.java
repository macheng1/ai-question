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
  private final TrainingService service = new TrainingService(repository, new ObjectMapper(), mock(AssessmentRosterService.class), audience, mock(TrainingExamService.class));

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  void endedTrainingCannotChangeScopeEvenWhenSubmittedEndIsExtended(boolean timestamp) {
    var endedAt = java.time.LocalDateTime.now().minusDays(1);
    Object storedEnd = timestamp ? java.sql.Timestamp.valueOf(endedAt) : endedAt;
    when(repository.findById(eq(1L), anyBoolean())).thenReturn(List.of(Map.of(
        "created_by", 2L, "version", 1, "end_at", storedEnd, "questions_json", "[]")));
    when(repository.countSurveyResponses(1L)).thenReturn(List.of(0L));
    when(audienceRepository.countOwnedTrainings(1L, 2L)).thenReturn(List.of(1));
    when(audienceRepository.findAudienceType(1L)).thenReturn(List.of("USERS"));
    when(audienceRepository.findScopeTargetIds(1L)).thenReturn(List.of(3L));
    var error = assertThrows(BusinessException.class, () -> service.save(1L,
        new TrainingService.SaveTraining("培训", "", false, List.of(), 1, null, null, null,
            new TrainingAudienceService.Scope("USERS", List.of(3L, 4L)),
            endedAt.plusDays(2), endedAt.minusDays(1)), 2L));
    assertEquals("培训已结束，不能追加学员", error.getMessage());
    assertNoContentWrite();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"UTC", "Asia/Shanghai", "America/Los_Angeles"})
  void endingTrainingUsesBeijingTimeRegardlessOfJvmTimezone(String timezone) {
    var originalTimezone = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone(timezone));
      var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
      var row = new HashMap<String, Object>(Map.of("created_by", 2L, "start_at", now.minusMinutes(30)));
      when(repository.findById(eq(1L), anyBoolean())).thenReturn(List.of(row));
      var subject = spy(service);
      doReturn(Map.of()).when(subject).get(1L, 2L);
      assertDoesNotThrow(() -> subject.endTraining(1L, 2L));
      row.put("start_at", java.sql.Timestamp.from(now.minusMinutes(30)
          .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant()));
      assertDoesNotThrow(() -> subject.endTraining(1L, 2L));
      verify(repository, times(2)).endTraining(1L);
      clearInvocations(repository);
      row.put("start_at", now.plusMinutes(30));
      assertThrows(BusinessException.class, () -> subject.endTraining(1L, 2L));
      verify(repository, never()).endTraining(anyLong());
    } finally {
      TimeZone.setDefault(originalTimezone);
    }
  }

  @Test void surveyReturnsOnlyCurrentEmployeesSavedAnswers() {
    when(repository.findById(1L, false)).thenReturn(List.of(Map.of("created_by", 2L, "survey_enabled", true, "questions_json", "[]")));
    when(audienceRepository.countCandidateAssignments(1L, "张三", "13900000000")).thenReturn(List.of(1));
    when(repository.countSubmittedSurvey(1L, 9L, true)).thenReturn(List.of(1));
    when(repository.findOwnSurveyAnswers(1L, 9L, true)).thenReturn(List.of("{\"q1\":\"5\",\"q2\":\"很好\"}"));
    var employee = new AccountService.Profile(9L, "张三", "13900000000", "", "", "", com.aiquestion.security.AccountType.EMPLOYEE, 2L);
    var result = service.employeeSurvey(1L, employee);
    assertEquals(true, result.get("submitted"));
    assertEquals(Map.of("q1", "5", "q2", "很好"), result.get("submittedAnswers"));
    verify(repository).findOwnSurveyAnswers(1L, 9L, true);
    verify(repository, never()).findSurveyResponses(anyLong());
  }

  @Test void futureTrainingCannotStartLearning() {
    when(audienceRepository.countExamLinkedTrainings(1L)).thenReturn(List.of(0));
    when(audienceRepository.findTrainingIdsForEntry(1L, false)).thenReturn(List.of(1L));
    when(audienceRepository.countCandidateAssignments(1L, "张三", "13900000000")).thenReturn(List.of(1));
    when(audienceRepository.countNotStartedTrainings(1L, false)).thenReturn(List.of(1));
    var error = assertThrows(BusinessException.class, () -> audience.requireTask(-1L, "张三", "13900000000"));
    assertEquals("培训尚未开始", error.getMessage());
  }
  @Test void endedTrainingBlocksLearningButKeepsHistoryAccess() {
    when(audienceRepository.findTrainingIdsForEntry(9L, true)).thenReturn(List.of(1L));
    when(audienceRepository.countCandidateAssignments(1L, "张三", "13900000000")).thenReturn(List.of(1));
    when(audienceRepository.countNotStartedTrainings(1L, false)).thenReturn(List.of(0));
    when(audienceRepository.countEndedTrainings(1L)).thenReturn(List.of(1));
    assertDoesNotThrow(() -> audience.requireTask(9L, "张三", "13900000000"));
    var error = assertThrows(BusinessException.class, () -> audience.requireOpenTask(9L, "张三", "13900000000"));
    assertEquals("培训已结束，不能继续学习或考试", error.getMessage());
    when(audienceRepository.countEndedTrainings(1L)).thenReturn(List.of(0));
    assertDoesNotThrow(() -> audience.requireOpenTask(9L, "张三", "13900000000"));
  }
  @Test void standaloneEndedTrainingCannotOpenLearning() {
    when(audienceRepository.countExamLinkedTrainings(1L)).thenReturn(List.of(0));
    when(audienceRepository.findTrainingIdsForEntry(1L, false)).thenReturn(List.of(1L));
    when(audienceRepository.countCandidateAssignments(1L, "张三", "13900000000")).thenReturn(List.of(1));
    when(audienceRepository.countNotStartedTrainings(1L, false)).thenReturn(List.of(0));
    when(audienceRepository.countEndedTrainings(1L)).thenReturn(List.of(1));
    assertThrows(BusinessException.class, () -> audience.requireOpenTask(-1L, "张三", "13900000000"));
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
