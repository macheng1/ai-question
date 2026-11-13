package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.TrainingAudienceRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrainingAudienceEligibilityTest {
  private final TrainingAudienceRepository repository = mock(TrainingAudienceRepository.class);
  private final TrainingEligibilityService eligibility = mock(TrainingEligibilityService.class);
  private final TrainingAudienceService service = new TrainingAudienceService(repository, eligibility);

  @BeforeEach void setup() {
    when(eligibility.bind(any(), anyLong(), anyLong(), anyLong())).thenReturn(true);
    when(repository.countOwnedTrainings(1L, 2L)).thenReturn(List.of(1));
    when(repository.lockTraining(1L)).thenReturn(List.of(Map.of("assessment_id", 9L)));
    when(repository.findAudienceType(1L)).thenReturn(List.of("USERS"));
    when(repository.countLearningSessions(1L)).thenReturn(List.of(0));
    when(repository.countExamParticipants(1L)).thenReturn(List.of(0));
    when(repository.findActiveEmployees(2L)).thenReturn(List.of(
        Map.of("id", 3L, "name", "甲", "phone", "13900000001", "department_id", 5L, "department_name", "研发部"),
        Map.of("id", 4L, "name", "乙", "phone", "13900000002", "department_id", 5L, "department_name", "研发部")));
    when(repository.findActiveDepartmentIds(2L)).thenReturn(List.of(5L));
    when(eligibility.excludedEmployees(9L, 2L, 1L)).thenReturn(Set.of(3L));
  }

  @Test void departmentsAutomaticallyExcludeSubmittedEmployees() {
    service.save(1L, 2L, new TrainingAudienceService.Scope("DEPARTMENT", List.of(5L)));
    verify(repository).insertScopeTarget(1L, 5L);
    verify(repository).insertAssignment(1L, 4L, "乙", "13900000002", 5L, "研发部", null);
    verify(repository, never()).insertAssignment(eq(1L), eq(3L), any(), any(), any(), any(), any());
  }

  @Test void individualSelectionCannotBypassExclusion() {
    assertThrows(BusinessException.class, () -> service.save(1L, 2L, new TrainingAudienceService.Scope("USERS", List.of(3L, 4L))));
    assertRosterUntouched();
  }

  @Test void allExcludedFailsBeforeReplacingExistingRoster() {
    when(eligibility.excludedEmployees(9L, 2L, 1L)).thenReturn(Set.of(3L, 4L));
    assertThrows(BusinessException.class, () -> service.save(1L, 2L, new TrainingAudienceService.Scope("ALL", List.of())));
    assertRosterUntouched();
  }

  @Test void copiedEmptySelectionCannotBeSaved() {
    assertThrows(BusinessException.class, () -> service.save(1L, 2L, new TrainingAudienceService.Scope("USERS", List.of())));
    assertRosterUntouched();
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void futureTrainingCanReplaceAssignedPeopleAndChangeScope(boolean timestamp) {
    when(repository.lockTraining(1L)).thenReturn(List.of(Map.of("assessment_id", 9L,
        "start_at", date(java.time.LocalDateTime.now().plusDays(1), timestamp))));
    when(repository.findScopeTargetIds(1L)).thenReturn(List.of(3L));
    when(repository.findAssignments(1L)).thenReturn(List.of(Map.of("employee_id", 3L)));
    service.save(1L, 2L, new TrainingAudienceService.Scope("DEPARTMENT", List.of(5L)));
    verify(repository).deleteAudience(1L);
    verify(repository).updateAudienceType("DEPARTMENT", 1L);
    verify(repository).insertAssignment(1L, 4L, "乙", "13900000002", 5L, "研发部", null);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void startedTrainingCannotRemoveAssignedPeople(boolean timestamp) {
    when(repository.lockTraining(1L)).thenReturn(List.of(Map.of("assessment_id", 9L,
        "start_at", date(java.time.LocalDateTime.now().minusHours(1), timestamp))));
    when(repository.findScopeTargetIds(1L)).thenReturn(List.of(3L));
    when(repository.findAssignments(1L)).thenReturn(List.of(Map.of("employee_id", 3L)));
    assertThrows(BusinessException.class, () -> service.save(1L, 2L,
        new TrainingAudienceService.Scope("USERS", List.of(4L))));
    assertRosterUntouched();
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void startedTrainingCanAppendPeopleWithoutReplacingExistingAssignments(boolean timestamp) {
    when(repository.lockTraining(1L)).thenReturn(List.of(Map.of("assessment_id", 9L,
        "start_at", date(java.time.LocalDateTime.now().minusDays(1), timestamp),
        "end_at", date(java.time.LocalDateTime.now().plusDays(1), timestamp))));
    when(repository.findScopeTargetIds(1L)).thenReturn(List.of(3L));
    when(repository.findAssignments(1L)).thenReturn(List.of(Map.of("employee_id", 3L)));
    service.save(1L, 2L, new TrainingAudienceService.Scope("USERS", List.of(3L, 4L)));
    verify(repository, never()).deleteAudience(anyLong());
    verify(repository).insertAssignment(1L, 4L, "乙", "13900000002", 5L, "研发部", null);
    verify(repository, never()).insertAssignment(eq(1L), eq(3L), any(), any(), any(), any(), any());
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void endedTrainingCannotAppendPeople(boolean timestamp) {
    when(repository.lockTraining(1L)).thenReturn(List.of(Map.of("assessment_id", 9L,
        "start_at", date(java.time.LocalDateTime.now().minusDays(2), timestamp),
        "end_at", date(java.time.LocalDateTime.now().minusDays(1), timestamp))));
    when(repository.findScopeTargetIds(1L)).thenReturn(List.of(3L));
    when(repository.findAssignments(1L)).thenReturn(List.of(Map.of("employee_id", 3L)));
    var error = assertThrows(BusinessException.class, () -> service.save(1L, 2L,
        new TrainingAudienceService.Scope("USERS", List.of(3L, 4L))));
    assertEquals("培训已结束，不能追加学员", error.getMessage());
    assertRosterUntouched();
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void futureTrainingWithLearningRecordsCannotReplaceScope(boolean timestamp) {
    protectFutureTraining(timestamp);
    when(repository.countLearningSessions(1L)).thenReturn(List.of(1));
    assertThrows(BusinessException.class, () -> service.save(1L, 2L,
        new TrainingAudienceService.Scope("DEPARTMENT", List.of(5L))));
    assertRosterUntouched();
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void futureTrainingWithExamRecordsCannotRemovePeople(boolean timestamp) {
    protectFutureTraining(timestamp);
    when(repository.countExamParticipants(1L)).thenReturn(List.of(1));
    assertThrows(BusinessException.class, () -> service.save(1L, 2L,
        new TrainingAudienceService.Scope("USERS", List.of(4L))));
    assertRosterUntouched();
  }

  private void protectFutureTraining(boolean timestamp) {
    when(repository.lockTraining(1L)).thenReturn(List.of(Map.of("assessment_id", 9L,
        "start_at", date(java.time.LocalDateTime.now().plusDays(1), timestamp))));
    when(repository.findScopeTargetIds(1L)).thenReturn(List.of(3L));
    when(repository.findAssignments(1L)).thenReturn(List.of(Map.of("employee_id", 3L)));
  }

  private static Object date(java.time.LocalDateTime time, boolean timestamp) {
    return timestamp ? java.sql.Timestamp.valueOf(time) : time;
  }

  private void assertRosterUntouched() {
    verify(repository, never()).deleteAudience(anyLong());
    verify(repository, never()).deleteScopeTargets(anyLong());
    verify(repository, never()).updateAudienceType(anyString(), anyLong());
    verify(repository, never()).insertAssignment(any(), any(), any(), any(), any(), any(), any());
  }
}
