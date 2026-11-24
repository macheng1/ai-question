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

  private static final String[] HEADERS = {"姓名", "手机号", "部门", "培训名称", "整体状态", "学习进度",
      "有效学时", "考试状态", "逾期情况", "开始学习", "整体完成", "考试分数", "答题次数", "考试用时"};
  private static final Map<String, String> STATUSES = Map.of(
      "NOT_STARTED", "未开始", "IN_PROGRESS", "进行中", "COMPLETED", "已完成",
      "PASSED", "已通过", "FAILED", "未通过", "NONE", "无考试", "PENDING_REVIEW", "待批阅");

  public static byte[] write(List<Map<String, Object>> rows) throws IOException {
    return write(rows, false, "学习日志");
  }

  public static byte[] writeRoster(List<Map<String, Object>> rows) throws IOException {
    return write(rows, true, "未完成人员清单");
  }

  public static byte[] writeOverdue(List<Map<String, Object>> rows) throws IOException {
    return write(rows, true, "逾期未完成人员清单");
  }

  private static byte[] write(List<Map<String, Object>> rows, boolean roster, String sheetName) throws IOException {
    boolean overdueRoster = "逾期未完成人员清单".equals(sheetName);
    String[] headers = roster ? new String[]{"姓名", "手机号", "部门", "培训名称", "整体状态", "学习进度（%）", "有效学时", "考试状态", "逾期情况"} : HEADERS;
    if (overdueRoster) {
      headers = java.util.Arrays.copyOf(headers, headers.length + 2);
      headers[headers.length - 2] = "培训截止时间";
      headers[headers.length - 1] = "考试截止时间";
    }
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet(sheetName);
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
      header.createCell(headers.length).setCellValue("有效学习秒数（可汇总）");
      header.getCell(headers.length).setCellStyle(headerStyle);
      sheet.setColumnWidth(headers.length, 26 * 256);
      var percentStyle = workbook.createCellStyle();
      percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0%"));
      int index = 1;
      for (var source : rows) {
        boolean missing = source.get("missingDuration") instanceof Number n && n.longValue() > 0;
        Object reasons = source.get("overdueReasons");
        String overdue = reasons instanceof List<?> list ? String.join("；", list.stream().map(String::valueOf).toList()) : "";
        var values = roster ? new Object[]{source.get("name"), source.get("phone"), source.get("departmentName"),
            source.get("trainingName"), label(source.get("status")), source.get("progress"),
            source.get("studiedHours"), label(source.get("examStatus")), overdue} : new Object[] {source.get("name"), source.get("phone"), source.get("departmentName"),
            source.get("trainingName"), label(source.get("status")), number(source.get("progress")) / 100,
            source.get("studiedHours"), label(source.get("examStatus")),
            overdue.isEmpty() ? "—" : overdue, time(source.get("startedAt")), time(source.get("completedAt")),
            source.getOrDefault("score", "—"), source.get("attemptCount"), examDuration(source.get("durationSeconds"))};
        if (overdueRoster) {
          values = java.util.Arrays.copyOf(values, headers.length);
          values[headers.length - 2] = time(source.get("trainingEnd"));
          values[headers.length - 1] = time(source.get("examEnd"));
        }
        values[6] = duration(number(source.get("studiedHours"))) + (missing ? " 缺失" : "");
        var row = sheet.createRow(index++);
        row.createCell(headers.length).setCellValue(Math.floor(Math.max(0, number(source.get("studiedHours"))) * 3600 + 1e-7));
        for (int i = 0; i < values.length; i++) {
          var cell = row.createCell(i);
          if (!roster && i == 5) cell.setCellStyle(percentStyle);
          if (values[i] instanceof Number number) {
            cell.setCellValue(number.doubleValue());

          }
          else if (values[i] != null) cell.setCellValue(values[i].toString());
          else if (!roster && i == 11) cell.setCellValue("—");
        }
      }
      sheet.createFreezePane(0, 1);
      sheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, headers.length));
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private static String label(Object value) {
    return value == null ? "" : STATUSES.getOrDefault(value.toString(), value.toString());
  }

  private static double number(Object value) {
    return value instanceof Number n ? n.doubleValue() : 0;
  }

  private static String duration(double hours) {
    if (!Double.isFinite(hours) || hours < 0) return "—";
    long seconds = (long) Math.floor(hours * 3600 + 1e-7);
    if (seconds == 0) return hours > 0 ? "不足1秒" : "0秒";
    return (seconds >= 3600 ? seconds / 3600 + "小时" : "")
        + (seconds / 60 % 60 > 0 ? seconds / 60 % 60 + "分" : "")
        + (seconds % 60 > 0 ? seconds % 60 + "秒" : "");
  }

  private static String examDuration(Object seconds) {
    return seconds instanceof Number n ? duration(n.doubleValue() / 3600.0) : "—";
  }

  private static String time(Object value) {
    if (value == null) return "—";
    var zone = ZoneId.of("Asia/Shanghai");
    LocalDateTime date;
    if (value instanceof Timestamp timestamp) date = timestamp.toInstant().atZone(zone).toLocalDateTime();
    else if (value instanceof LocalDateTime local) date = local;
    else date = Instant.parse(value.toString()).atZone(zone).toLocalDateTime();
    return date.format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"));
  }
}
