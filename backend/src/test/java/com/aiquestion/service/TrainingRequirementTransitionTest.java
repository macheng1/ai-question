package com.aiquestion.service;

import com.aiquestion.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingRequirementTransitionTest {
  @Test void switchingToRequiredBackfillsOnceAndPreservesOldAssignments() {
    verifyTransition("DEPARTMENT", false);
  }

  @Test void allEmployeesScopeAlsoBackfills() {
    verifyTransition("ALL", false);
  }

  @Test void endedTrainingDoesNotBackfill() {
    verifyTransition("DEPARTMENT", true);
  }

  @Test void explicitlySelectedPeopleAreNotExpanded() {
    verifyTransition("USERS", false);
  }

  @Test void futureTrainingBackfillPreservesPeopleWhoMovedOutOfDepartment() {
    verifyTransition("DEPARTMENT", false, true);
  }

  private void verifyTransition(String scopeType, boolean ended) {
    verifyTransition(scopeType, ended, false);
  }

  @Test void savingExistingRequiredTrainingRepairsMissingAssignmentsWithoutDuplicates() {
    verifySaves("DEPARTMENT", false, false, "REQUIRED", List.of("REQUIRED", "REQUIRED"));
  }

  @Test void savingEndedRequiredTrainingDoesNotAddLearners() {
    verifySaves("DEPARTMENT", true, false, "REQUIRED", List.of("REQUIRED", "REQUIRED"));
  }

  @Test void savingElectiveTrainingDoesNotAddLearners() {
    verifySaves("DEPARTMENT", false, false, "ELECTIVE", List.of("ELECTIVE", "ELECTIVE"));
  }

  private void verifyTransition(String scopeType, boolean ended, boolean future) {
    verifySaves(scopeType, ended, future, "ELECTIVE", List.of("REQUIRED", "ELECTIVE", "REQUIRED"));
  }

  private void verifySaves(String scopeType, boolean ended, boolean future, String initialRequirement, List<String> saves) {
    var repository = mock(TrainingRepository.class);
    var roster = mock(TrainingAudienceRepository.class);
    var eligibility = mock(TrainingEligibilityService.class);
    var audience = new TrainingAudienceService(roster, eligibility);
    var service = spy(new TrainingService(repository, new ObjectMapper(), mock(AssessmentRosterService.class), audience, mock(TrainingExamService.class)));
    doReturn(Map.of()).when(service).get(1L, 2L);
    var now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
    var row = new HashMap<String, Object>(Map.of("created_by", 2L, "version", 1,
        "questions_json", "[]", "requirement_type", initialRequirement, "start_at", future ? now.plusHours(1) : now.minusDays(2),
        "end_at", ended ? now.minusDays(1) : now.plusDays(1)));
    when(repository.findById(eq(1L), anyBoolean())).thenReturn(List.of(row));
    when(repository.countSurveyResponses(1L)).thenReturn(List.of(0L));
    when(roster.countOwnedTrainings(1L, 2L)).thenReturn(List.of(1));
    when(roster.lockTraining(1L)).thenReturn(List.of(row));
    when(roster.findAudienceType(1L)).thenReturn(List.of(scopeType));
    List<Long> targets = scopeType.equals("ALL") ? List.of() : List.of(5L);
    when(roster.findScopeTargetIds(1L)).thenReturn(targets);
    var assignedAt = java.sql.Timestamp.valueOf(now.minusDays(1));
    var original = Map.<String, Object>of("employee_id", 3L, "assigned_at", assignedAt);
    var assignments = new ArrayList<Map<String, Object>>(List.of(original));
    when(roster.findAssignments(1L)).thenAnswer(inv -> new ArrayList<>(assignments));
    when(roster.countLearningSessions(1L)).thenReturn(List.of(future ? 0 : 1));
    when(roster.findActiveDepartmentIds(2L)).thenReturn(List.of(5L));
    when(roster.findActiveEmployees(2L)).thenReturn(List.of(
        Map.of("id", 4L, "name", "新增学员", "phone", "13900000004", "department_id", 5L, "department_name", "测试部")));
    when(eligibility.bind(null, 2L, 1L, 4L)).thenReturn(true);
    doAnswer(inv -> {
      row.put("requirement_type", inv.getArgument(2));
      return 1;
    }).when(repository).updateSettings(any(), any(), any(), any(), any(), any(), eq(1L));
    doAnswer(inv -> {
      long employeeId = inv.getArgument(1);
      assertTrue(assignments.stream().noneMatch(a -> a.get("employee_id").equals(employeeId)), "重复分配");
      assignments.add(Map.of("employee_id", employeeId));
      return 1;
    }).when(roster).insertAssignment(any(), any(), any(), any(), any(), any(), any());
    boolean backfilled = false;
    for (String requirement : saves) {
      service.save(1L, new TrainingService.SaveTraining("培训", "", false, List.of(), 1, null,
          null, requirement, new TrainingAudienceService.Scope(scopeType, targets),
          (LocalDateTime) row.get("end_at"), (LocalDateTime) row.get("start_at")), 2L);
      backfilled |= !ended && !scopeType.equals("USERS") && requirement.equals("REQUIRED");
      int expected = backfilled ? 2 : 1;
      assertEquals(expected, assignments.size());
      assertEquals(original, assignments.get(0));
    }
    verify(roster, never()).deleteAudience(anyLong());
    verify(roster, never()).deleteScopeTargets(anyLong());
  }
}
