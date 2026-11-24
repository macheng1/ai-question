package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class DashboardLogExporterTest {
  @Test
  void overdueExportPreservesDeadlinesAlongsideReadableDuration() throws Exception {
    var row = Map.<String, Object>of("studiedHours", 90 / 3600.0,
        "trainingEnd", "2026-11-11T01:00:00Z", "examEnd", "2026-11-12T02:00:00Z");
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(DashboardLogExporter.writeOverdue(List.of(row))))) {
      var sheet = workbook.getSheetAt(0);
      assertEquals("有效学时", sheet.getRow(0).getCell(6).getStringCellValue());
      assertEquals("1分30秒", sheet.getRow(1).getCell(6).getStringCellValue());
      assertEquals("培训截止时间", sheet.getRow(0).getCell(9).getStringCellValue());
      assertEquals("考试截止时间", sheet.getRow(0).getCell(10).getStringCellValue());
      assertEquals("2026/11/11 09:00:00", sheet.getRow(1).getCell(9).getStringCellValue());
      assertEquals("2026/11/12 10:00:00", sheet.getRow(1).getCell(10).getStringCellValue());
      assertEquals("有效学习秒数（可汇总）", sheet.getRow(0).getCell(11).getStringCellValue());
      assertEquals(90, sheet.getRow(1).getCell(11).getNumericCellValue());
    }
  }
  @Test
  void exportsNumbersMissingValuesAndLiteralText() throws Exception {
    var row = new HashMap<String, Object>();
    row.put("name", "=SUM(1,2)");
    row.put("phone", "13900001001");
    row.put("assessmentId", 1L);
    row.put("score", 0);
    row.put("studiedHours", 5 / 3600.0);
    row.put("progress", 25);
    row.put("examStatus", "FAILED");
    row.put("missingDuration", 1);
    row.put("startedAt", "2026-11-11T01:00:00Z");
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(DashboardLogExporter.write(List.of(row))))) {
      var sheet = workbook.getSheetAt(0);
      assertEquals(1, sheet.getLastRowNum());
      var headers = new ArrayList<String>();
      sheet.getRow(0).forEach(cell -> headers.add(cell.getStringCellValue()));
      assertEquals(List.of("姓名", "手机号", "部门", "培训名称", "整体状态", "学习进度", "有效学时", "考试状态",
          "逾期情况", "开始学习", "整体完成", "考试分数", "答题次数", "考试用时", "有效学习秒数（可汇总）"), headers);
      var data = sheet.getRow(1);
      assertEquals(CellType.STRING, data.getCell(0).getCellType());
      assertEquals("13900001001", data.getCell(1).getStringCellValue());
      assertEquals("25%", new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(data.getCell(5)));
      assertEquals(5, data.getCell(14).getNumericCellValue());
      assertEquals("5秒 缺失", new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(data.getCell(6)));
      assertEquals("未通过", data.getCell(7).getStringCellValue());
      assertEquals("—", data.getCell(8).getStringCellValue());
      assertEquals("2026/11/11 09:00:00", data.getCell(9).getStringCellValue());
      assertEquals(0, data.getCell(11).getNumericCellValue());
      assertEquals("—", data.getCell(13).getStringCellValue());
    }
  }
  @Test
  void allExportsUseReadableNumericDurations() throws Exception {
    int[] seconds = {0, 30, 90, 3600, 3661, 90061};
    String[] labels = {"0秒", "30秒", "1分30秒", "1小时", "1小时1分1秒", "25小时1分1秒"};
    var rows = new ArrayList<Map<String, Object>>();
    for (int value : seconds) rows.add(Map.of("studiedHours", value / 3600.0));
    for (byte[] bytes : List.of(DashboardLogExporter.write(rows), DashboardLogExporter.writeRoster(rows), DashboardLogExporter.writeOverdue(rows))) {
      try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
        for (int i = 0; i < seconds.length; i++) {
          var cell = workbook.getSheetAt(0).getRow(i + 1).getCell(6);
          assertEquals(CellType.STRING, cell.getCellType());
          var numeric = workbook.getSheetAt(0).getRow(i + 1).getCell(workbook.getSheetAt(0).getRow(0).getLastCellNum() - 1);
          assertEquals(seconds[i], numeric.getNumericCellValue(), 1e-10);
          assertEquals(labels[i], formatter.formatCellValue(cell));
        }
      }
    }
  }
  @Test
  void examDurationUsesSecondsAndPreservesMissingValues() throws Exception {
    var rows = new ArrayList<Map<String, Object>>();
    rows.add(Map.of());
    for (double seconds : new double[]{0, 0.2, 30, 90, 3661, 90061})
      rows.add(Map.of("durationSeconds", seconds));
    String[] expected = {"—", "0秒", "不足1秒", "30秒", "1分30秒", "1小时1分1秒", "25小时1分1秒"};
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(DashboardLogExporter.write(rows)))) {
      var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
      for (int i = 0; i < expected.length; i++)
        assertEquals(expected[i], formatter.formatCellValue(workbook.getSheetAt(0).getRow(i + 1).getCell(13)));
    }
  }
}
