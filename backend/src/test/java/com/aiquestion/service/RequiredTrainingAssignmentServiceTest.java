package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.aiquestion.repository.RequiredTrainingAssignmentRepository;

class RequiredTrainingAssignmentServiceTest {
  @Test void inactiveEmployeeIsNotAssigned() {
    var jdbc = mock(RequiredTrainingAssignmentRepository.class);
    var eligibility = mock(TrainingEligibilityService.class);
    lenient().when(eligibility.bind(any(), anyLong(), anyLong(), anyLong())).thenReturn(true);
    new RequiredTrainingAssignmentService(jdbc, eligibility).assignEmployee(3L, 2L);
    verify(eligibility).lockAllocationOwner(2L);
    verifyNoMoreInteractions(eligibility);
    verify(jdbc, never()).insertAudienceIfAbsent(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyString(), anyLong(), anyLong());
  }

  @Test void submittedPaperIsExcludedButStandaloneIsAssigned() {
    var jdbc = mock(RequiredTrainingAssignmentRepository.class);
    var eligibility = mock(TrainingEligibilityService.class);
    lenient().when(eligibility.bind(any(), anyLong(), anyLong(), anyLong())).thenReturn(true);
    when(jdbc.findActiveEmployee(3L, 2L))
        .thenReturn(List.of(Map.of("id", 3L, "name", "学员", "phone", "13900000000", "department_id", 5L, "department_name", "研发部")));
    when(jdbc.findMatchingTrainings(2L, 5L))
        .thenReturn(List.of(Map.of("id", 10L), Map.of("id", 11L)));
    when(jdbc.lockMatchingTraining(10L, 2L, 5L))
        .thenReturn(List.of(Map.of("id", 10L, "assessment_id", 9L)));
    when(jdbc.lockMatchingTraining(11L, 2L, 5L))
        .thenReturn(List.of(Map.of("id", 11L)));
    when(eligibility.excludedEmployees(9L, 2L, 10L)).thenReturn(Set.of(3L));
    new RequiredTrainingAssignmentService(jdbc, eligibility).assignEmployee(3L, 2L);
    verify(jdbc, never()).insertAudienceIfAbsent(eq(10L), any(), any(), any(), any(), any(), any(), any());
    verify(jdbc).insertAudienceIfAbsent(11L, 3L, "学员", "13900000000", 5L, "研发部", 11L, 3L);
  }
  @Test void departmentAssignmentProcessesEveryEligibleEmployeeNotTheDepartmentId() {
    var repository = mock(RequiredTrainingAssignmentRepository.class);
    var eligibility = mock(TrainingEligibilityService.class);
    lenient().when(eligibility.bind(any(), anyLong(), anyLong(), anyLong())).thenReturn(true);
    when(repository.findActiveDepartmentEmployeeIds(5L, 2L)).thenReturn(List.of(21L, 22L));
    when(repository.findMatchingTrainings(2L, 5L)).thenReturn(List.of(Map.of("id", 10L)));
    when(repository.lockMatchingTraining(10L, 2L, 5L)).thenReturn(List.of(Map.of("id", 10L)));
    for (long employeeId : List.of(21L, 22L)) {
      when(repository.findActiveEmployee(employeeId, 2L)).thenReturn(List.of(Map.of(
          "id", employeeId, "name", "学员", "phone", "13900000000",
          "department_id", 5L, "department_name", "研发部")));
    }
    new RequiredTrainingAssignmentService(repository, eligibility).assignDepartment(5L, 2L);
    for (long employeeId : List.of(21L, 22L))
      verify(repository).insertAudienceIfAbsent(10L, employeeId, "学员", "13900000000", 5L, "研发部", 10L, employeeId);
    verify(repository, never()).findActiveEmployee(5L, 2L);
  }

  @Test void departmentWithoutEligibleEmployeesDoesNotAssignAnyone() {
    var repository = mock(RequiredTrainingAssignmentRepository.class);
    var eligibility = mock(TrainingEligibilityService.class);
    lenient().when(eligibility.bind(any(), anyLong(), anyLong(), anyLong())).thenReturn(true);
    new RequiredTrainingAssignmentService(repository, eligibility).assignDepartment(5L, 2L);
    verify(repository).findActiveDepartmentEmployeeIds(5L, 2L);
    verifyNoMoreInteractions(repository);
    verify(eligibility).lockAllocationOwner(2L);
    verifyNoMoreInteractions(eligibility);
  }
}
