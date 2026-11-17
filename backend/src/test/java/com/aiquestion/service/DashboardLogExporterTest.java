package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class DashboardLogExporterTest {
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
          "逾期情况", "开始学习", "整体完成", "考试分数", "答题次数", "考试用时"), headers);
      var data = sheet.getRow(1);
      assertEquals(CellType.STRING, data.getCell(0).getCellType());
      assertEquals("13900001001", data.getCell(1).getStringCellValue());
      assertEquals("25%", new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(data.getCell(5)));
      assertEquals(5 / 3600.0, data.getCell(6).getNumericCellValue());
      assertEquals("\"<0.01 小时 缺失\"", data.getCell(6).getCellStyle().getDataFormatString());
      assertEquals("未通过", data.getCell(7).getStringCellValue());
      assertEquals("—", data.getCell(8).getStringCellValue());
      assertEquals("2026/11/11 09:00:00", data.getCell(9).getStringCellValue());
      assertEquals(0, data.getCell(11).getNumericCellValue());
      assertEquals("—", data.getCell(13).getStringCellValue());
    }
  }
}
