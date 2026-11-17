package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.integration.TrainingReminderMailer;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.DashboardLogExporter;
import com.aiquestion.service.DashboardService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/dashboard/overdue")
public class OverdueRosterController {
  private final DashboardService dashboard;
  private final TrainingReminderMailer mailer;

  public OverdueRosterController(DashboardService dashboard, TrainingReminderMailer mailer) {
    this.dashboard = dashboard;
    this.mailer = mailer;
  }

  public record Selection(LocalDate start, LocalDate end, Long trainingId, Long departmentId, String category,
                          List<String> records, List<String> recipients) {}

  private static String key(Map<String, Object> row) {
    return row.get("trainingId") + "-" + row.get("employeeId");
  }

  @GetMapping("/keys")
  public ApiResponse<List<String>> keys(@RequestParam(required = false) LocalDate start,
      @RequestParam(required = false) LocalDate end, @RequestParam(required = false) Long trainingId,
      @RequestParam(required = false) Long departmentId, @RequestParam(required = false) String category) {
    return ApiResponse.ok(dashboard.overdueRows(ManagementAccess.userId(), start, end, trainingId, departmentId, category)
        .stream().map(OverdueRosterController::key).toList());
  }

  @PostMapping("/email")
  public ApiResponse<List<String>> email(@RequestBody Selection selection) throws IOException {
    var rows = selectedRows(ManagementAccess.userId(), selection);
    return ApiResponse.ok(mailer.sendOverdueRoster(selection.recipients(), DashboardLogExporter.writeOverdue(rows), rows.size()));
  }

  public record NotifyResult(int sent, int missingEmail, int failed, List<String> unsentKeys) {}

  @PostMapping("/notify")
  public ApiResponse<NotifyResult> notifyLearners(@RequestBody Selection selection) throws IOException {
    long owner = ManagementAccess.userId();
    var rows = selectedRows(owner, selection);
    var groups = rows.stream().collect(java.util.stream.Collectors.groupingBy(row -> ((Number) row.get("employeeId")).longValue()));
    var emails = dashboard.learnerEmails(owner, groups.keySet());
    var unsent = new java.util.ArrayList<String>();
    int sent = 0, missing = 0, failed = 0;
    for (var group : groups.entrySet()) {
      var personal = group.getValue();
      String email = emails.get(group.getKey());
      if (!TrainingReminderMailer.validEmail(email)) {
        missing++;
      } else if (mailer.sendLearnerOverdue(email, personal)) {
        sent++;
        continue;
      } else {
        failed++;
      }
      personal.forEach(row -> unsent.add(key(row)));
    }
    return ApiResponse.ok(new NotifyResult(sent, missing, failed, unsent));
  }

  private List<Map<String,Object>> selectedRows(long owner, Selection selection) {
    if (selection.records() == null || selection.records().isEmpty())
      throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择逾期记录");
    var selected = new HashSet<>(selection.records());
    var rows = dashboard.overdueRows(owner, selection.start(), selection.end(), selection.trainingId(),
        selection.departmentId(), selection.category()).stream().filter(row -> selected.contains(key(row))).toList();
    if (rows.size() != selected.size())
      throw new BusinessException(ErrorCode.BAD_REQUEST, "所选名单已变化，请刷新后重新选择");
    return rows;
  }
}
