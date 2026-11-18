package com.aiquestion.service;

import java.io.*;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionImportTemplateTest {
    @SuppressWarnings("unchecked")
    @Test void templatesKeepDifficultyAfterRemovingUnusedColumns() throws Exception {
        var service = new QuestionImportService(null, null, null);
        byte[] excel = service.template();
        var read = QuestionImportService.class.getDeclaredMethod("readExcel", InputStream.class);
        read.setAccessible(true);
        var questions = (List<ImportQuestion>) read.invoke(service, new ByteArrayInputStream(excel));
        assertEquals(List.of("易", "中", "易", "中"), questions.stream().map(q -> q.difficulty).toList());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            var header = workbook.getSheetAt(0).getRow(0);
            assertEquals(9, header.getLastCellNum());
            assertEquals("难度", header.getCell(8).getStringCellValue());
        }
        byte[] word = service.wordTemplate();
        try (var doc = new XWPFDocument(new ByteArrayInputStream(word))) {
            String text = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", String::concat);
            assertFalse(text.contains("标签"));
            assertFalse(text.contains("出处"));
        }
        var wordQuestions = WordQuestionImport.read(new ByteArrayInputStream(word));
        assertEquals(List.of("易", "中", "易", "中"), wordQuestions.stream().map(q -> q.difficulty).toList());
    }
}
