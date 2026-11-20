package com.aiquestion.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
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
    String[] headers = roster ? new String[]{"姓名", "手机号", "部门", "培训名称", "整体状态", "学习进度（%）", "有效学时（小时）", "考试状态", "逾期情况"} : HEADERS;
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
      var percentStyle = workbook.createCellStyle();
      percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0%"));
      Map<String, CellStyle> durationStyles = new HashMap<>();
      int index = 1;
      for (var source : rows) {
        boolean missing = source.get("missingDuration") instanceof Number n && n.longValue() > 0;
        Object reasons = source.get("overdueReasons");
        String overdue = reasons instanceof List<?> list ? String.join("；", list.stream().map(String::valueOf).toList()) : "";
        var values = roster ? new Object[]{source.get("name"), source.get("phone"), source.get("departmentName"),
            source.get("trainingName"), label(source.get("status")), source.get("progress"),
            source.get("studiedHours"), label(source.get("examStatus")), overdue} : new Object[] {source.get("name"), source.get("phone"), source.get("departmentName"),
            source.get("trainingName"), label(source.get("status")), number(source.get("progress")) / 100,
            hours(source.get("studiedHours")), label(source.get("examStatus")),
            overdue.isEmpty() ? "—" : overdue, time(source.get("startedAt")), time(source.get("completedAt")),
            source.getOrDefault("score", "—"), source.get("attemptCount"), minutes(source.get("durationSeconds"))};
        if (overdueRoster) {
          values = java.util.Arrays.copyOf(values, headers.length);
          values[headers.length - 2] = time(source.get("trainingEnd"));
          values[headers.length - 1] = time(source.get("examEnd"));
        }
        var row = sheet.createRow(index++);
        for (int i = 0; i < values.length; i++) {
          var cell = row.createCell(i);
          if (!roster && i == 5) cell.setCellStyle(percentStyle);
          if (values[i] instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            if (!roster && (i == 6 || i == 13)) {
              double value = number.doubleValue();
              boolean tiny = i == 6 && value > 0 && value < 0.01;
              String unit = i == 13 ? "分钟" : missing ? "小时 缺失" : "小时";
              int scale = Math.max(0, BigDecimal.valueOf(value).stripTrailingZeros().scale());
              String digits = scale == 0 ? "0" : "0." + "0".repeat(scale);
              String format = tiny ? "\"<0.01 " + unit + "\"" : digits + "\" " + unit + "\"";
              cell.setCellStyle(durationStyles.computeIfAbsent(format, pattern -> {
                var style = workbook.createCellStyle();
                style.setDataFormat(workbook.createDataFormat().getFormat(pattern));
                return style;
              }));
            }
          }
          else if (values[i] != null) cell.setCellValue(values[i].toString());
          else if (!roster && i == 11) cell.setCellValue("—");
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

  private static double number(Object value) {
    return value instanceof Number n ? n.doubleValue() : 0;
  }

  private static double hours(Object value) {
    double hours = number(value);
    // Match the page's toFixed(2), which rounds the exact binary floating-point value.
    return hours < 0.01 ? hours : new BigDecimal(hours).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private static Object minutes(Object seconds) {
    return seconds == null ? "—" : Math.round(number(seconds) / 60 * 10) / 10.0;
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
