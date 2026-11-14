package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.repository.DashboardRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
  private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
  private final DashboardRepository repository;

  public DashboardService(DashboardRepository repository) {
    this.repository = repository;
  }

  public record Overview(
      long trainingCount,
      long assigned,
      long completed,
      long notStarted,
      long inProgress,
      double studiedHours,
      long examAssigned,
      long passed,
      long overdue,
      long missingDuration,
      Double completionRate,
      Double passRate) {}

  public record Result(
      Overview overview,
      List<Map<String, Object>> departments,
      List<Map<String, Object>> trend,
      List<Map<String, Object>> rows,
      int total,
      int page,
      int pageSize,
      String updatedAt) {}

  public Map<String, Object> options(long owner) {
    return repository.options(owner);
  }

  @Transactional(readOnly = true)
  public Result load(
      long owner,
      LocalDate start,
      LocalDate end,
      Long trainingId,
      Long departmentId,
      String category,
      String tab,
      String status,
      int page,
      int pageSize) {
    return loadDetails(owner, start, end, trainingId, departmentId, category, tab, status, page, pageSize, true);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportLogs(long owner, LocalDate start, LocalDate end,
      Long trainingId, Long departmentId, String category, String status) {
    return loadDetails(owner, start, end, trainingId, departmentId, category, "logs", status, 1, 10, false).rows();
  }

  private Result loadDetails(long owner, LocalDate start, LocalDate end, Long trainingId,
      Long departmentId, String category, String tab, String status, int page, int pageSize,
      boolean paginate) {
    if (start != null && end != null && start.isAfter(end)) fail("开始日期不能晚于结束日期");
    if (page < 1 || page > 1000000 || !Set.of(10, 20, 50).contains(pageSize)) fail("分页参数无效");
    if (!Set.of("trainings", "learners", "incomplete", "exams", "logs").contains(tab))
      fail("明细类型无效");
    if (!Set.of("ALL", "COMPLETED", "NOT_STARTED", "IN_PROGRESS", "OVERDUE").contains(status))
      fail("状态无效");
    if ((trainingId != null && trainingId <= 0) || (departmentId != null && departmentId <= 0))
      fail("筛选参数无效");
    var filters = new HashMap<String, Object>();
    if (start != null) filters.put("start", databaseDay(start));
    if (end != null) filters.put("end", databaseDay(end.plusDays(1)));
    if (trainingId != null) filters.put("trainingId", trainingId);
    if (departmentId != null) filters.put("departmentId", departmentId);
    if (category != null && !category.isBlank()) filters.put("category", category);
    var trainings = repository.trainings(owner, filters);
    var rows = new ArrayList<Map<String, Object>>();
    var now = LocalDateTime.now(ZoneOffset.UTC);
    for (var source : repository.rows(owner, filters)) rows.add(decorate(source, now));
    long completed = count(rows, "completed"),
        passed = count(rows, "examPassed"),
        overdue = count(rows, "overdue");
    long notStarted = rows.stream().filter(r -> "NOT_STARTED".equals(r.get("status"))).count();
    long examAssigned = rows.stream().filter(r -> r.get("assessmentId") != null).count();
    var overview =
        new Overview(
            trainings.size(),
            rows.size(),
            completed,
            notStarted,
            rows.size() - completed - notStarted,
            hours(rows.stream().mapToDouble(r -> number(r, "studiedSeconds")).sum()),
            examAssigned,
            passed,
            overdue,
            rows.stream().filter(r -> number(r, "missingDuration") > 0).count(),
            rate(completed, rows.size()),
            rate(passed, examAssigned));
    var departmentGroups = new LinkedHashMap<Long, List<Map<String, Object>>>();
    for (var row : rows)
      departmentGroups
          .computeIfAbsent((long) number(row, "departmentId"), key -> new ArrayList<>())
          .add(row);
    var departments = new ArrayList<Map<String, Object>>();
    for (var entry : departmentGroups.entrySet()) {
      var group = entry.getValue();
      var item = new LinkedHashMap<String, Object>();
      item.put("id", entry.getKey());
      item.put("name", group.get(0).get("departmentName"));
      item.put("assigned", group.size());
      item.put("completed", count(group, "completed"));
      item.put("rate", rate(count(group, "completed"), group.size()));
      departments.add(item);
    }
    departments.sort(Comparator.comparingDouble(r -> -(double) r.get("rate")));
    var trend = trend(rows);
    List<Map<String, Object>> detail;
    if ("trainings".equals(tab)) {
      detail = new ArrayList<>();
      var grouped = new HashMap<Long, List<Map<String, Object>>>();
      for (var row : rows)
        grouped
            .computeIfAbsent((long) number(row, "trainingId"), key -> new ArrayList<>())
            .add(row);
      for (var training : trainings) {
        var group = grouped.getOrDefault(((Number) training.get("id")).longValue(), List.of());
        var item = new LinkedHashMap<String, Object>(training);
        item.put("assigned", group.size());
        item.put("completed", count(group, "completed"));
        item.put("completionRate", rate(count(group, "completed"), group.size()));
        item.put(
            "studiedHours",
            hours(group.stream().mapToDouble(r -> number(r, "studiedSeconds")).sum()));
        item.put("overdue", count(group, "overdue"));
        detail.add(item);
      }
    } else {
      detail =
          rows.stream()
              .filter(
                  row ->
                      switch (tab) {
                        case "incomplete" -> !Boolean.TRUE.equals(row.get("completed"));
                        case "exams" -> row.get("assessmentId") != null;
                        case "logs" -> row.get("sessionId") != null;
                        default -> true;
                      })
              .filter(
                  row ->
                      "ALL".equals(status)
                          || ("OVERDUE".equals(status)
                              ? Boolean.TRUE.equals(row.get("overdue"))
                              : status.equals(row.get("status"))))
              .toList();
    }
    int total = detail.size();
    int actualPage = Math.min(page, Math.max(1, (total + pageSize - 1) / pageSize));
    int offset = (actualPage - 1) * pageSize;
    return new Result(
        overview,
        departments,
        trend,
        List.copyOf(paginate ? detail.subList(offset, Math.min(total, offset + pageSize)) : detail),
        total,
        actualPage,
        pageSize,
        Instant.now().toString());
  }

  private Map<String, Object> decorate(Map<String, Object> source, LocalDateTime now) {
    var row = new LinkedHashMap<String, Object>(source);
    String learning =
        LearnerTaskStatus.learning(
            row.get("sessionId") != null,
            (long) number(row, "totalUnits"),
            (long) number(row, "completedUnits"),
            row.get("confirmedAt") != null);
    boolean learned = "COMPLETED".equals(learning),
        passed = "SUBMITTED".equals(row.get("attemptStatus")) && flag(row.get("passed"));
    boolean pendingReview = "PENDING_REVIEW".equals(row.get("attemptStatus"));
    boolean complete = learned && (row.get("assessmentId") == null || passed);
    var reasons =
        TrainingReminderRules.reasons(
            date(row.get("trainingEnd")),
            learned,
            row.get("assessmentId") == null ? null : date(row.get("examEnd")),
            passed || pendingReview,
            now);
    row.put("learningStatus", learning);
    row.put("examPassed", passed);
    row.put("completed", complete);
    row.put("overdue", !reasons.isEmpty());
    row.put("overdueReasons", reasons);
    row.put(
        "status",
        complete
            ? "COMPLETED"
            : row.get("sessionId") == null && row.get("attemptId") == null
                ? "NOT_STARTED"
                : "IN_PROGRESS");
    row.put(
        "examStatus",
        row.get("assessmentId") == null
            ? "NONE"
            : row.get("attemptId") == null
                ? "NOT_STARTED"
                : pendingReview ? "PENDING_REVIEW"
                : "SUBMITTED".equals(row.get("attemptStatus"))
                    ? passed ? "PASSED" : "FAILED"
                    : "IN_PROGRESS");
    row.put(
        "progress",
        number(row, "totalUnits") == 0
            ? (learned ? 100 : 0)
            : Math.min(
                100, Math.round(number(row, "completedUnits") * 100 / number(row, "totalUnits"))));
    var completedAt = later(date(row.get("videoCompletedAt")), date(row.get("readingCompletedAt")));
    if (number(row, "totalUnits") == 0) completedAt = date(row.get("confirmedAt"));
    if (row.get("assessmentId") != null)
      completedAt = later(completedAt, date(row.get(row.get("reviewedAt") == null ? "submittedAt" : "reviewedAt")));
    row.put(
        "completedAt",
        complete && completedAt != null ? completedAt.toInstant(ZoneOffset.UTC).toString() : null);
    row.put("studiedHours", hours(number(row, "studiedSeconds")));
    if (!"SUBMITTED".equals(row.get("attemptStatus"))) row.put("score", null);
    return row;
  }

  private List<Map<String, Object>> trend(List<Map<String, Object>> rows) {
    var days = new LinkedHashMap<LocalDate, Long>();
    var today = LocalDate.now(DISPLAY_ZONE);
    for (int i = 29; i >= 0; i--) days.put(today.minusDays(i), 0L);
    for (var row : rows)
      if (row.get("completedAt") instanceof String value) {
        var day = Instant.parse(value).atZone(DISPLAY_ZONE).toLocalDate();
        if (days.containsKey(day)) days.put(day, days.get(day) + 1);
      }
    var result = new ArrayList<Map<String, Object>>();
    days.forEach((day, value) -> result.add(Map.of("date", day.toString(), "count", value)));
    return result;
  }

  private static LocalDateTime databaseDay(LocalDate day) {
    // JDBC session uses +08:00; compare both DATETIME and TIMESTAMP in database local time.
    return day.atStartOfDay();
  }

  private static long count(List<Map<String, Object>> rows, String key) {
    return rows.stream().filter(row -> Boolean.TRUE.equals(row.get(key))).count();
  }

  private static Double rate(long numerator, long denominator) {
    return denominator == 0 ? null : Math.round(numerator * 1000.0 / denominator) / 10.0;
  }

  private static double hours(double seconds) {
    return seconds / 3600.0;
  }

  private static double number(Map<String, Object> row, String key) {
    return row.get(key) instanceof Number n ? n.doubleValue() : 0;
  }

  private static boolean flag(Object value) {
    return Boolean.TRUE.equals(value) || "1".equals(String.valueOf(value));
  }

  private static LocalDateTime date(Object value) {
    if (value instanceof java.sql.Timestamp t)
      return LocalDateTime.ofInstant(t.toInstant(), ZoneOffset.UTC);
    // MySQL DATETIME is a Shanghai wall-clock value; TIMESTAMP above already carries an instant.
    return value instanceof LocalDateTime t
        ? t.atZone(DISPLAY_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
  }

  private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
    return a == null ? b : b == null || a.isAfter(b) ? a : b;
  }

  private static void fail(String message) {
    throw new BusinessException(ErrorCode.BAD_REQUEST, message);
  }
}
