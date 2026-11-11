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
    row.put("studiedSeconds", 5);
    row.put("examStatus", "FAILED");
    row.put("missingDuration", 1);
    row.put("startedAt", "2026-11-11T01:00:00Z");
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(DashboardLogExporter.write(List.of(row))))) {
      var sheet = workbook.getSheetAt(0);
      assertEquals(1, sheet.getLastRowNum());
      assertEquals("学习时长（秒）", sheet.getRow(0).getCell(6).getStringCellValue());
      var data = sheet.getRow(1);
      assertEquals(CellType.STRING, data.getCell(0).getCellType());
      assertEquals("13900001001", data.getCell(1).getStringCellValue());
      assertEquals(5, data.getCell(6).getNumericCellValue());
      assertEquals("部分历史时长缺失", data.getCell(7).getStringCellValue());
      assertEquals("未通过", data.getCell(9).getStringCellValue());
      assertEquals(0, data.getCell(10).getNumericCellValue());
      assertEquals(CellType.BLANK, data.getCell(13).getCellType());
      assertEquals("2026-11-11 09:00:00", data.getCell(14).getStringCellValue());
    }
  }
}
