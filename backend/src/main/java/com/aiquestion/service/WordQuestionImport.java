package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.*;

final class WordQuestionImport {
    private static final Pattern HEADER = Pattern.compile("^(?:([0-9]+)\\s*[.．、)）]\\s*)?[【\\[]([^】\\]]+)[】\\]]\\s*(.*)$");
    private static final Pattern OPTION = Pattern.compile("^([A-Za-z])\\s*[.．、)）]\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^(正确答案|参考答案|答案|解析|难度|标签|出处)\\s*[:：]\\s*(.*)$");

    static byte[] template(String[][] examples) throws IOException {
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            paragraph(doc, "试题导入模板", true, 20);
            paragraph(doc, "请替换或删除示例题。每题以【题型】开头，选项各占一行，答案写在本题下方。问答题答案可分段，解析、标签、难度和出处可选。", false, 11);
            int number = 0;
            for (String[] row : examples) {
                var heading = paragraph(doc, (++number) + ".【" + row[0] + "】" + row[1], true, 12);
                heading.setSpacingBefore(180);
                for (int i = 0; i < 4; i++)
                    if (!row[2 + i].isEmpty()) paragraph(doc, (char) ('A' + i) + ". " + row[2 + i], false, 11);
                paragraph(doc, ("问答题".equals(row[0]) ? "参考答案：" : "答案：") + row[6], false, 11);
                if (!row[7].isEmpty()) paragraph(doc, "解析：" + row[7], false, 11);
                paragraph(doc, "标签：" + row[8], false, 11);
                paragraph(doc, "难度：" + row[9], false, 11);
                paragraph(doc, "出处：" + row[10], false, 11);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static XWPFParagraph paragraph(XWPFDocument doc, String text, boolean bold, int size) {
        var p = doc.createParagraph();
        p.setSpacingAfter(40);
        var run = p.createRun();
        run.setBold(bold);
        run.setFontSize(size);
        run.setText(text);
        return p;
    }

    static List<ImportQuestion> read(InputStream in) throws IOException {
        try (var doc = new XWPFDocument(in)) {
            if (!doc.getTables().isEmpty())
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Word 模板按段落识别，请将表格中的题目改为模板中的段落格式");
            List<ImportQuestion> questions = new ArrayList<>();
            ImportQuestion current = null;
            String field = "题干";
            for (var paragraph : doc.getParagraphs()) {
                for (String raw : paragraph.getText().split("\\R")) {
                    String line = raw.strip();
                    if (line.isEmpty()) continue;
                    var header = HEADER.matcher(line);
                    if (header.matches()) {
                        String number = header.group(1) == null ? String.valueOf(questions.size() + 1) : header.group(1);
                        current = new ImportQuestion("第" + number + "题", header.group(2), header.group(3));
                        questions.add(current);
                        field = "题干";
                        continue;
                    }
                    if (current == null) continue;
                    var label = FIELD.matcher(line);
                    var option = OPTION.matcher(line);
                    if (label.matches()) {
                        field = label.group(1);
                        if (field.endsWith("答案")) {
                            field = "答案";
                            if (!current.answer.isEmpty()) current.error = "本题有多个答案标记，请检查题型和分题位置";
                        }
                        line = label.group(2);
                    } else if ((field.equals("题干") || field.length() == 1) && option.matches()) {
                        field = option.group(1).toUpperCase(Locale.ROOT);
                        if (current.options.containsKey(field)) current.error = "选项 " + field + " 重复";
                        line = option.group(2);
                    }
                    switch (field) {
                        case "题干" -> current.stem = append(current.stem, line);
                        case "答案" -> current.answer = append(current.answer, line);
                        case "解析" -> current.analysis = append(current.analysis, line);
                        case "难度" -> current.difficulty = append(current.difficulty, line);
                        case "标签" -> current.tags = append(current.tags, line);
                        case "出处" -> current.source = append(current.source, line);
                        default -> current.options.merge(field, line, WordQuestionImport::append);
                    }
                }
            }
            if (questions.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "未找到题目，请按 Word 模板填写，每题以【题型】开头");
            return questions;
        }
    }

    private static String append(String previous, String text) {
        return previous.isEmpty() ? text : previous + "\n" + text;
    }
}
