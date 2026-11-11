package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiquestion.common.BusinessException;
import com.aiquestion.repository.DashboardRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
  @Test
  void dateFilterUsesDatabaseLocalDayBoundaries() {
    var repo = mock(DashboardRepository.class);
    when(repo.rows(eq(1L), any())).thenReturn(List.of());
    when(repo.trainings(eq(1L), any())).thenReturn(List.of());
    new DashboardService(repo).load(1, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9), null, null, null, "learners", "ALL", 1, 10);
    verify(repo).rows(eq(1L), argThat(filters ->
        LocalDateTime.of(2026, 11, 9, 0, 0).equals(filters.get("start"))
        && LocalDateTime.of(2026, 11, 10, 0, 0).equals(filters.get("end"))));
  }

  @Test
  void shanghaiDatetimeDeadlineIsComparedWithCurrentInstant() {
    var repo = mock(DashboardRepository.class);
    var item = row();
    item.put("completedUnits", 0L);
    item.put("trainingEnd", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1));
    when(repo.rows(eq(1L), any())).thenReturn(List.of(item));
    when(repo.trainings(eq(1L), any())).thenReturn(List.of());
    var result = new DashboardService(repo).load(1, null, null, null, null, null, "learners", "ALL", 1, 10);
    assertEquals(1, result.overview().overdue());
  }

  @Test
  void emptyDataHasNoInventedRates() {
    var repo = mock(DashboardRepository.class);
    when(repo.rows(eq(1L), any())).thenReturn(List.of());
    when(repo.trainings(eq(1L), any())).thenReturn(List.of());
    var result =
        new DashboardService(repo).load(1, null, null, null, null, null, "trainings", "ALL", 1, 10);
    assertNull(result.overview().completionRate());
    assertNull(result.overview().passRate());
    assertEquals(0, result.total());
  }

  @Test
  void rejectsInvalidRangeAndUnboundedPagination() {
    var service = new DashboardService(mock(DashboardRepository.class));
    assertThrows(
        BusinessException.class,
        () ->
            service.load(
                1,
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 1),
                null,
                null,
                null,
                "trainings",
                "ALL",
                1,
                10));
    assertThrows(
        BusinessException.class,
        () -> service.load(1, null, null, null, null, null, "trainings", "ALL", 1, 10000));
  }

  @Test
  void completedLearningDoesNotCompleteFailedExamAndOverdueIsDeduplicated() {
    var repo = mock(DashboardRepository.class);
    var row = row();
    row.put("assessmentId", 9L);
    row.put("attemptStatus", "SUBMITTED");
    row.put("passed", false);
    row.put("completedUnits", 0L);
    row.put("trainingEnd", LocalDateTime.of(2020, 1, 1, 0, 0));
    row.put("examEnd", LocalDateTime.of(2020, 1, 1, 0, 0));
    when(repo.rows(eq(1L), any())).thenReturn(List.of(row));
    when(repo.trainings(eq(1L), any()))
        .thenReturn(List.of(Map.of("id", 2L, "name", "培训", "assessmentId", 9L)));
    var result =
        new DashboardService(repo).load(1, null, null, null, null, null, "learners", "ALL", 1, 10);
    assertEquals(1, result.overview().overdue());
    assertEquals(0, result.overview().completed());
    row.put("completedUnits", 1L);
    result =
        new DashboardService(repo).load(1, null, null, null, null, null, "learners", "ALL", 1, 10);
    assertEquals(0, result.overview().completed());
    row.put("passed", true);
    result =
        new DashboardService(repo).load(1, null, null, null, null, null, "learners", "ALL", 1, 10);
    assertEquals(1, result.overview().completed());
    assertEquals(0, result.overview().overdue());
  }

  @Test
  void materialFreeTrainingRequiresExplicitCompletionAndUnknownDurationIsFlagged() {
    var repo = mock(DashboardRepository.class);
    var row = row();
    row.put("totalUnits", 0L);
    row.put("missingDuration", 1L);
    when(repo.rows(eq(1L), any())).thenReturn(List.of(row));
    when(repo.trainings(eq(1L), any())).thenReturn(List.of(Map.of("id", 2L, "name", "培训")));
    var result =
        new DashboardService(repo).load(1, null, null, null, null, null, "logs", "ALL", 1, 10);
    assertEquals(0, result.overview().completed());
    assertEquals(1, result.overview().missingDuration());
    row.put("confirmedAt", LocalDateTime.now());
    result = new DashboardService(repo).load(1, null, null, null, null, null, "logs", "ALL", 1, 10);
    assertEquals(1, result.overview().completed());
  }

  @Test
  void pagingAndStatusFilterNeverChangeOverview() {
    var repo = mock(DashboardRepository.class);
    var rows = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 23; i++) {
      var item = row();
      item.put("employeeId", (long) i);
      rows.add(item);
    }
    when(repo.rows(eq(7L), any())).thenReturn(rows);
    when(repo.trainings(eq(7L), any())).thenReturn(List.of(Map.of("id", 2L, "name", "培训")));
    var service = new DashboardService(repo);
    var result = service.load(7, null, null, null, null, null, "learners", "COMPLETED", 3, 10);
    assertEquals(23, result.total());
    assertEquals(3, result.rows().size());
    assertEquals(23, result.overview().assigned());
    var empty = service.load(7, null, null, null, null, null, "incomplete", "ALL", 1, 10);
    assertEquals(0, empty.total());
    assertEquals(23, empty.overview().completed());
  }

  @Test
  void exportIncludesAllFilteredLogs() {
    var repo = mock(DashboardRepository.class);
    var rows = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 55; i++) rows.add(row());
    var unfinished = row();
    unfinished.put("completedUnits", 0L);
    rows.add(unfinished);
    var notStarted = row();
    notStarted.remove("sessionId");
    rows.add(notStarted);
    when(repo.rows(eq(7L), any())).thenReturn(rows);
    when(repo.trainings(eq(7L), any())).thenReturn(List.of());
    var service = new DashboardService(repo);
    assertEquals(10, service.load(7, null, null, 2L, 4L, null, "logs", "COMPLETED", 1, 10).rows().size());
    assertEquals(55, service.exportLogs(7, null, null, 2L, 4L, null, "COMPLETED").size());
    assertEquals(56, service.exportLogs(7, null, null, 2L, 4L, null, "ALL").size());
  }

  private Map<String, Object> row() {
    var row = new HashMap<String, Object>();
    row.put("trainingId", 2L);
    row.put("trainingName", "培训");
    row.put("employeeId", 3L);
    row.put("sessionId", 1L);
    row.put("totalUnits", 1L);
    row.put("completedUnits", 1L);
    row.put("departmentId", 4L);
    row.put("departmentName", "研发部");
    row.put("studiedSeconds", 3600.0);
    return row;
  }
}
