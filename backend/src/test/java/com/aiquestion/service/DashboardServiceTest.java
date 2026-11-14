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
  void submittedExamAwaitingReviewIsNeitherFailedNorOverdue() {
    var repo = mock(DashboardRepository.class);
    var item = row();
    item.put("assessmentId", 9L);
    item.put("attemptId", 10L);
    item.put("attemptStatus", "PENDING_REVIEW");
    item.put("score", 0);
    item.put("examEnd", LocalDateTime.of(2020, 1, 1, 0, 0));
    when(repo.rows(eq(1L), any())).thenReturn(List.of(item));
    when(repo.trainings(eq(1L), any())).thenReturn(List.of());
    var result = new DashboardService(repo).load(1, null, null, null, null, null, "exams", "ALL", 1, 10);
    assertEquals("PENDING_REVIEW", result.rows().get(0).get("examStatus"));
    assertNull(result.rows().get(0).get("score"));
    assertEquals(0, result.overview().completed());
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

  @Test
  void dashboardCardsAndChartsAgreeForRepeatedAssignmentsAndManualReview() {
    var repo = mock(DashboardRepository.class);
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    var learned = row();
    learned.put("videoCompletedAt", today.minusDays(2).atTime(12, 0));
    learned.put("submittedAt", today.minusDays(1).atTime(12, 0));
    learned.put("reviewedAt", today.atStartOfDay().plusMinutes(5));
    learned.put("assessmentId", 10L);
    learned.put("attemptStatus", "SUBMITTED");
    learned.put("passed", true);
    var pending = new HashMap<>(learned);
    pending.put("trainingId", 3L);
    pending.put("attemptStatus", "PENDING_REVIEW");
    pending.put("examEnd", today.minusDays(1).atStartOfDay());
    pending.put("studiedSeconds", 1800);
    var overdue = row();
    overdue.remove("sessionId");
    overdue.put("departmentId", 5L);
    overdue.put("assessmentId", 10L);
    overdue.put("trainingEnd", today.minusDays(1).atStartOfDay());
    overdue.put("examEnd", today.minusDays(1).atStartOfDay());
    overdue.put("studiedSeconds", 0);
    overdue.put("employeeId", 7L);
    when(repo.trainings(eq(1L), any())).thenReturn(List.of(Map.of("id", 2L), Map.of("id", 3L)));
    when(repo.rows(eq(1L), any())).thenReturn(List.of(learned, pending, overdue));

    var result = new DashboardService(repo).load(1, null, null, null, null, null, "learners", "ALL", 1, 10);
    var overview = result.overview();
    assertEquals(2, overview.trainingCount());
    assertEquals(3, overview.assigned());
    assertEquals(1, overview.completed());
    assertEquals(33.3, overview.completionRate());
    assertEquals(1.5, overview.studiedHours());
    assertEquals(3, overview.examAssigned());
    assertEquals(1, overview.passed());
    assertEquals(33.3, overview.passRate());
    assertEquals(1, overview.overdue());
    assertEquals(1, overview.inProgress());
    assertEquals(1, overview.notStarted());
    assertEquals(2, result.departments().size());
    assertEquals(4L, result.departments().get(0).get("id"));
    assertEquals(50.0, result.departments().get(0).get("rate"));
    assertEquals(0.0, result.departments().get(1).get("rate"));
    assertEquals(30, result.trend().size());
    assertEquals(Map.of("date", today.toString(), "count", 1L), result.trend().get(29));
    assertEquals(0L, result.trend().get(28).get("count"));
  }

  @Test
  void learnerHoursCombinesAssignmentsBeforePaginationAndSeparatesNamesakes() {
    var repo = mock(DashboardRepository.class);
    var first = row();
    first.put("name", "李明");
    var second = new HashMap<>(first);
    second.put("trainingId", 8L);
    second.put("studiedSeconds", 1800);
    var namesake = new HashMap<>(first);
    namesake.put("employeeId", 17L);
    namesake.put("studiedSeconds", 0);
    when(repo.rows(eq(1L), any())).thenReturn(List.of(first, second, namesake));
    var result = new DashboardService(repo).load(1, null, null, null, null, null, "learnerHours", "ALL", 1, 10);
    assertEquals(2, result.total());
    assertEquals(2, result.rows().get(0).get("assigned"));
    assertEquals(1.5, result.rows().get(0).get("studiedHours"));
    assertEquals(0.0, result.rows().get(1).get("studiedHours"));
  }

  @Test
  void rosterExportIncludesEveryMatchingAssignmentAcrossPages() throws Exception {
    var repo = mock(DashboardRepository.class);
    var rows = new ArrayList<Map<String, Object>>();
    rows.add(row());
    var notStarted = row();
    notStarted.remove("sessionId");
    rows.add(notStarted);
    for (int i = 0; i < 12; i++) {
      var pending = row();
      pending.put("completedUnits", 0L);
      rows.add(pending);
    }
    when(repo.rows(eq(1L), any())).thenReturn(rows);
    var service = new DashboardService(repo);
    var page = service.load(1, null, null, 2L, null, null, "incomplete", "ALL", 1, 10);
    assertEquals(12, page.total());
    assertEquals(10, page.rows().size());
    var exported = service.exportRoster(1, null, null, 2L, null, null, "ALL");
    try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
        new java.io.ByteArrayInputStream(DashboardLogExporter.writeRoster(exported)))) {
      var sheet = book.getSheetAt(0);
      assertEquals(12, sheet.getLastRowNum());
      assertEquals("进行中", sheet.getRow(12).getCell(4).getStringCellValue());
    }
    assertEquals(1, service.exportRoster(1, null, null, 2L, null, null, "COMPLETED").size());
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
