package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.controller.QuestionItemController.OptionRequest;
import com.aiquestion.domain.QuestionItem;
import com.aiquestion.domain.QuestionOption;
import com.aiquestion.repository.QuestionBankRepository;
import com.aiquestion.repository.QuestionItemRepository;
import com.aiquestion.repository.QuestionOptionRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/** 负责按“试题导入模板.xlsx”解析、校验和批量写入题库。 */
@Service
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class QuestionImportService {
    private final QuestionBankRepository bankRepository;
    private final QuestionItemRepository itemRepository;
    private final QuestionOptionRepository optionRepository;

    public QuestionImportService(QuestionBankRepository bankRepository, QuestionItemRepository itemRepository, QuestionOptionRepository optionRepository) {
        this.bankRepository = bankRepository; this.itemRepository = itemRepository; this.optionRepository = optionRepository;
    }

    private static final String[][] EXAMPLES = {
                {"单选题", "示例：临时离开办公电脑时，应先做什么？", "锁定屏幕", "关闭显示器", "调低亮度", "最小化窗口", "A", "锁屏后需要重新验证身份才能继续操作。", "易"},
                {"多选题", "示例：哪些做法有助于保护工作账号？", "启用多因素认证", "多个系统共用密码", "使用独立密码", "把验证码发给同事", "AC", "独立密码和多因素认证都能降低账号被盗用的风险。", "中"},
                {"判断题", "示例：可以把工作账号的验证码发给自称是管理员的人。", "", "", "", "", "错误", "验证码仅供本人验证身份时使用。", "易"},
                {"问答题", "示例：收到要求输入工作账号密码的可疑邮件，应如何处理？", "", "", "", "", "先通过已知的官方渠道核实发件人身份，避免点击邮件链接或输入密码，并向信息安全人员报告。", "", "中"}
            };

    public byte[] wordTemplate() {
        try { return WordQuestionImport.template(EXAMPLES); }
        catch (IOException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Word 模板生成失败"); }
    }

    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("试题导入");
            String[] headers = {"题型", "题干", "选项A", "选项B", "选项C", "选项D", "正确答案", "解析", "难度"};
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 1 || i == 6 || i == 7 ? 48 * 256 : 18 * 256);
            }
            CellStyle exampleStyle = workbook.createCellStyle();
            exampleStyle.setWrapText(true);
            for (String[] example : EXAMPLES) {
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                row.setHeightInPoints(54);
                for (int i = 0; i < example.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(example[i]);
                    cell.setCellStyle(exampleStyle);
                }
            }
            Sheet help = workbook.createSheet("填写说明");
            String[] notes = {"1. 一行一道题，保留第一行表头，导入前请替换或删除四行示例题。", "2. 题型填写：单选题 / 多选题 / 判断题 / 问答题。", "3. 单选题、多选题填写选项 A-D；判断题选项可留空，问答题选项留空。", "4. 正确答案：单选填一个字母，多选填多个字母（如 ABC），判断题填写 正确 或 错误，问答题填写参考答案，可换行。", "5. 难度可填写：易 / 中 / 难，留空默认为中。", "6. 已存在相同题干的题目会自动跳过。"};
            for (int i = 0; i < notes.length; i++) help.createRow(i).createCell(0).setCellValue(notes[i]); help.setColumnWidth(0, 110 * 256);
            workbook.write(output); return output.toByteArray();
        } catch (IOException e) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "模板生成失败"); }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFile(Long bankId, MultipartFile file, Long userId) {
        if (bankRepository.lockOwnedForImport(bankId, userId) == null) { throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND); }
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择 Excel 或 Word 文件");
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        List<ImportQuestion> questions;
        try (var input = file.getInputStream()) {
            if (name.endsWith(".docx")) questions = WordQuestionImport.read(input);
            else if (name.endsWith(".xlsx")) questions = readExcel(input);
            else throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 .xlsx 和 .docx 格式");
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件解析失败，请检查格式并使用对应的导入模板");
        }
        int imported = 0, skipped = 0;
        List<ImportIssue> issues = new ArrayList<>();
        for (var question : questions) {
            try {
                if (insertQuestion(bankId, question)) imported++;
                else skipped++;
            } catch (IllegalArgumentException | BusinessException e) {
                issues.add(new ImportIssue(question.location, question.stem, e.getMessage()));
            }
        }
        if (questions.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "文件中没有题目，请填写后重新导入");
        return new ImportResult(imported, skipped, issues);
    }

    private List<ImportQuestion> readExcel(java.io.InputStream input) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int headerRow = findHeaderRow(sheet, formatter);
            if (headerRow < 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "未找到模板表头“题型”，请使用标准试题导入模板");
            List<ImportQuestion> questions = new ArrayList<>();
            for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null || java.util.stream.IntStream.range(0, 9).allMatch(col -> blank(formatter, row, col))) continue;
                var question = new ImportQuestion("第" + (i + 1) + "行", value(formatter,row,0), value(formatter,row,1));
                for (int j = 0; j < 4; j++) {
                    String text = value(formatter, row, j + 2);
                    if (!text.isEmpty()) question.options.put(String.valueOf((char) ('A' + j)), text);
                }
                question.answer = formatter.formatCellValue(row.getCell(6));
                question.analysis = value(formatter,row,7);
                question.difficulty = value(formatter, row, 8);
                questions.add(question);
            }
            return questions;
        }
    }

    private boolean insertQuestion(Long bankId, ImportQuestion question) {
        if (question.error != null) throw new IllegalArgumentException(question.error);
        String type = normalizeType(question.type);
        if (question.stem.isBlank()) throw new IllegalArgumentException("缺少题干");
        if (question.answer.isBlank()) throw new IllegalArgumentException("缺少答案");
        if (itemRepository.countByBankIdAndStem(bankId, question.stem) > 0) return false;
        List<OptionRequest> options = new ArrayList<>();
        String answer = question.answer;
        if ("TRUE_FALSE".equals(type)) answer = normalizeBoolean(answer.trim());
        else {
            String keys = answer.toUpperCase(Locale.ROOT);
            question.options.forEach((key, text) -> options.add(new OptionRequest(key, text, keys.contains(key), key.charAt(0) - 'A')));
        }
        answer = QuestionAnswerRules.validate(type, answer, options);
        validateTextSize("题干", question.stem);
        validateTextSize("答案", answer);
        validateTextSize("解析", question.analysis);
        for (var option : options) validateTextSize("选项 " + option.optionKey(), option.optionText());
        QuestionItem item = new QuestionItem();
        item.setBankId(bankId);
        item.setQuestionType(type);
        item.setStem(question.stem);
        item.setAnswerText(answer);
        item.setAnalysis(question.analysis);
        item.setTags("");
        item.setDifficulty(normalizeDifficulty(question.difficulty));
        item.setScore(BigDecimal.ONE);
        item.setSource("");
        item.setStatus("ENABLED");
        itemRepository.insert(item);
        for (var request : options) {
            var option = new QuestionOption();
            option.setQuestionId(item.getId());
            option.setOptionKey(request.optionKey());
            option.setOptionText(request.optionText());
            option.setCorrect(Boolean.TRUE.equals(request.correct()));
            option.setSortNo(request.sortNo());
            optionRepository.insert(option);
        }
        bankRepository.incrementQuestionCount(bankId);
        return true;
    }

    private static void validateTextSize(String field, String text) {
        // MySQL TEXT capacity is measured in bytes under utf8mb4.
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65_535)
            throw new IllegalArgumentException(field + "内容过长，请缩短后重试");
    }

    private static String value(DataFormatter formatter, Row row, int index) { Cell cell = row.getCell(index); return cell == null ? "" : formatter.formatCellValue(cell).trim(); }
    private static boolean blank(DataFormatter formatter, Row row, int index) { return value(formatter, row, index).isBlank(); }
    private static int findHeaderRow(Sheet sheet, DataFormatter formatter) { for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) { Row row = sheet.getRow(i); if (row != null && value(formatter, row, 0).replaceAll("\\s+", "").equals("题型")) return i; } return -1; }
    private static String normalizeType(String value) { String normalized = value == null ? "" : value.replaceAll("\\s+", "").replace("（必填）", "").replace("(必填)", "").toUpperCase(Locale.ROOT); return switch (normalized) { case "单选题", "单选", "SINGLE", "SINGLECHOICE" -> "SINGLE"; case "多选题", "多选", "MULTIPLE", "MULTIPLECHOICE" -> "MULTIPLE"; case "判断题", "判断", "TRUEFALSE", "TRUE_FALSE", "判断题型" -> "TRUE_FALSE"; case "问答题", "问答", "简答题", "简答", "SHORTANSWER", "SHORT_ANSWER" -> "SHORT_ANSWER"; default -> throw new IllegalArgumentException("题型必须是单选题、多选题、判断题或问答题"); }; }
    private static String normalizeBoolean(String answer) { return switch (answer.trim()) { case "正确", "对", "是", "TRUE", "true" -> "TRUE"; case "错误", "错", "否", "FALSE", "false" -> "FALSE"; default -> throw new IllegalArgumentException("判断题答案必须填写正确或错误"); }; }
    private static String normalizeDifficulty(String value) { return switch (value) { case "易", "简单", "EASY" -> "EASY"; case "难", "困难", "HARD" -> "HARD"; default -> "MEDIUM"; }; }
    public record ImportIssue(String location, String stem, String reason) {}
    public record ImportResult(int imported, int skipped, List<ImportIssue> issues) {
        // 兼容仍使用文本错误信息的客户端。
        public List<String> getErrors() {
            return issues.stream().map(issue -> issue.location() + "：" + issue.reason()).toList();
        }
    }
}
