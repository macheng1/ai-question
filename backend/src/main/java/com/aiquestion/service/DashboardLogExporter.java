package com.aiquestion.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** 学习日志导出，统计口径由 DashboardService 统一提供。 */
public final class DashboardLogExporter {
  private DashboardLogExporter() {}

  private static final String[] HEADERS = {"姓名", "手机号", "部门", "培训名称", "整体状态", "学习进度（%）",
      "学习时长（秒）", "时长完整性", "考试名称", "考试结果", "考试分数", "考试总分", "答题次数",
      "考试用时（秒）", "开始学习时间", "整体完成时间", "交卷时间", "逾期情况"};
  private static final Map<String, String> STATUSES = Map.of(
      "NOT_STARTED", "未开始", "IN_PROGRESS", "进行中", "COMPLETED", "已完成",
      "PASSED", "已通过", "FAILED", "未通过", "NONE", "无考试", "PENDING_REVIEW", "待批阅");

  public static byte[] write(List<Map<String, Object>> rows) throws IOException {
    return write(rows, false);
  }

  public static byte[] writeRoster(List<Map<String, Object>> rows) throws IOException {
    return write(rows, true);
  }

  private static byte[] write(List<Map<String, Object>> rows, boolean roster) throws IOException {
    String[] headers = roster ? new String[]{"姓名", "手机号", "部门", "培训名称", "整体状态", "学习进度（%）", "有效学时（小时）", "考试状态", "逾期情况"} : HEADERS;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet(roster ? "培训人员名单" : "学习日志");
      var headerStyle = workbook.createCellStyle();
      var font = workbook.createFont();
      font.setBold(true);
      headerStyle.setFont(font);
      var header = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
        header.getCell(i).setCellStyle(headerStyle);
        sheet.setColumnWidth(i, (i == 3 || i == 8 ? 36 : 22) * 256);
      }
      int index = 1;
      for (var source : rows) {
        boolean hasExam = source.get("assessmentId") != null;
        boolean missing = source.get("missingDuration") instanceof Number n && n.longValue() > 0;
        Object reasons = source.get("overdueReasons");
        String overdue = reasons instanceof List<?> list ? String.join("；", list.stream().map(String::valueOf).toList()) : "";
        var values = roster ? new Object[]{source.get("name"), source.get("phone"), source.get("departmentName"),
            source.get("trainingName"), label(source.get("status")), source.get("progress"),
            source.get("studiedHours"), label(source.get("examStatus")), overdue} : new Object[] {source.get("name"), source.get("phone"), source.get("departmentName"),
            source.get("trainingName"), label(source.get("status")), source.get("progress"),
            source.get("studiedSeconds"), missing ? "部分历史时长缺失" : "完整",
            source.get("examName"), label(source.get("examStatus")), source.get("score"),
            hasExam ? source.get("totalScore") : null, hasExam ? source.get("attemptCount") : null,
            source.get("durationSeconds"), time(source.get("startedAt")), time(source.get("completedAt")),
            time(source.get("submittedAt")), overdue};
        var row = sheet.createRow(index++);
        for (int i = 0; i < values.length; i++) {
          var cell = row.createCell(i);
          if (values[i] instanceof Number number) cell.setCellValue(number.doubleValue());
          else if (values[i] != null) cell.setCellValue(values[i].toString());
        }
      }
      sheet.createFreezePane(0, 1);
      sheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, headers.length - 1));
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private static String label(Object value) {
    return value == null ? "" : STATUSES.getOrDefault(value.toString(), value.toString());
  }

  private static String time(Object value) {
    if (value == null) return "";
    var zone = ZoneId.of("Asia/Shanghai");
    LocalDateTime date;
    if (value instanceof Timestamp timestamp) date = timestamp.toInstant().atZone(zone).toLocalDateTime();
    else if (value instanceof LocalDateTime local) date = local;
    else date = Instant.parse(value.toString()).atZone(zone).toLocalDateTime();
    return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
  }
}
