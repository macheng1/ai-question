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

    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("试题导入");
            String[] headers = {"题型", "题干", "选项A", "选项B", "选项C", "选项D", "正确答案", "解析", "标签", "难度", "出处"};
            String[][] examples = {
                {"单选题", "示例：临时离开办公电脑时，应先做什么？", "锁定屏幕", "关闭显示器", "调低亮度", "最小化窗口", "A", "锁屏后需要重新验证身份才能继续操作。", "信息安全", "易", "入职培训"},
                {"多选题", "示例：哪些做法有助于保护工作账号？", "启用多因素认证", "多个系统共用密码", "使用独立密码", "把验证码发给同事", "AC", "独立密码和多因素认证都能降低账号被盗用的风险。", "账号安全", "中", "入职培训"},
                {"判断题", "示例：可以把工作账号的验证码发给自称是管理员的人。", "", "", "", "", "错误", "验证码仅供本人验证身份时使用。", "账号安全", "易", "入职培训"},
                {"问答题", "示例：收到要求输入工作账号密码的可疑邮件，应如何处理？", "", "", "", "", "先通过已知的官方渠道核实发件人身份，避免点击邮件链接或输入密码，并向信息安全人员报告。", "", "邮件安全", "中", "入职培训"}
            };
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
            for (String[] example : examples) {
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
        if (bankRepository.findOwnedById(bankId, userId) == null) { throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND); }
        if (file == null || file.isEmpty()) { throw new BusinessException(ErrorCode.BAD_REQUEST, "请上传 Excel 文件"); }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) { throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 .xlsx 格式"); }
        int imported = 0, skipped = 0; List<String> errors = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); DataFormatter formatter = new DataFormatter();
            int headerRow = findHeaderRow(sheet, formatter);
            if (headerRow < 0) { throw new BusinessException(ErrorCode.BAD_REQUEST, "未找到模板表头“题型”，请使用标准试题导入模板"); }
            for (int rowIndex = headerRow + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex); if (row == null || blank(formatter, row, 1)) continue;
                int line = rowIndex + 1;
                try {
                    String typeText = value(formatter, row, 0), stem = value(formatter, row, 1), answer = value(formatter, row, 6);
                    String type = normalizeType(typeText);
                    if (stem.isBlank() || answer.isBlank()) { throw new IllegalArgumentException("题干和正确答案不能为空"); }
                    if (itemRepository.countByBankIdAndStem(bankId, stem) > 0) { skipped++; continue; }
                    List<OptionRequest> options = new ArrayList<>();
                    if ("TRUE_FALSE".equals(type)) { answer = normalizeBoolean(answer); }
                    else if ("SHORT_ANSWER".equals(type)) {
                        for (int i = 2; i <= 5; i++) {
                            if (!blank(formatter, row, i)) throw new IllegalArgumentException("问答题不能包含选项");
                        }
                        answer = formatter.formatCellValue(row.getCell(6));
                    } else {
                        String normalizedAnswer = answer.replaceAll("[，、,\\s]+", "").toUpperCase(Locale.ROOT);
                        for (int i = 0; i < 4; i++) { String text = value(formatter, row, 2 + i); if (!text.isBlank()) options.add(new OptionRequest(String.valueOf((char)('A' + i)), text, normalizedAnswer.contains(String.valueOf((char)('A' + i))), i)); }
                        if (options.size() < 2 || options.stream().noneMatch(OptionRequest::correct)) { throw new IllegalArgumentException("选项至少填写两项且必须包含正确答案"); }
                        answer = normalizedAnswer;
                    }
                    answer = QuestionAnswerRules.validate(type, answer, options);
                    QuestionItem item = new QuestionItem(); item.setBankId(bankId); item.setQuestionType(type); item.setStem(stem); item.setAnswerText(answer); item.setAnalysis(value(formatter,row,7)); item.setTags(value(formatter,row,8)); item.setDifficulty(normalizeDifficulty(value(formatter,row,9))); item.setScore(BigDecimal.ONE); item.setSource(value(formatter,row,10)); item.setStatus("ENABLED"); itemRepository.insert(item);
                    for (OptionRequest request : options) { QuestionOption option = new QuestionOption(); option.setQuestionId(item.getId()); option.setOptionKey(request.optionKey()); option.setOptionText(request.optionText()); option.setCorrect(Boolean.TRUE.equals(request.correct())); option.setSortNo(request.sortNo()); optionRepository.insert(option); }
                    bankRepository.incrementQuestionCount(bankId); imported++;
                } catch (IllegalArgumentException | BusinessException e) { errors.add("第" + line + "行：" + e.getMessage()); }
            }
            return new ImportResult(imported, skipped, errors);
        } catch (Exception e) { if (e instanceof BusinessException be) throw be; throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件解析失败，请确认使用标准模板"); }
    }

    private static String value(DataFormatter formatter, Row row, int index) { Cell cell = row.getCell(index); return cell == null ? "" : formatter.formatCellValue(cell).trim(); }
    private static boolean blank(DataFormatter formatter, Row row, int index) { return value(formatter, row, index).isBlank(); }
    private static int findHeaderRow(Sheet sheet, DataFormatter formatter) { for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) { Row row = sheet.getRow(i); if (row != null && value(formatter, row, 0).replaceAll("\\s+", "").equals("题型")) return i; } return -1; }
    private static String normalizeType(String value) { String normalized = value == null ? "" : value.replaceAll("\\s+", "").replace("（必填）", "").replace("(必填)", "").toUpperCase(Locale.ROOT); return switch (normalized) { case "单选题", "单选", "SINGLE", "SINGLECHOICE" -> "SINGLE"; case "多选题", "多选", "MULTIPLE", "MULTIPLECHOICE" -> "MULTIPLE"; case "判断题", "判断", "TRUEFALSE", "TRUE_FALSE", "判断题型" -> "TRUE_FALSE"; case "问答题", "问答", "简答题", "简答", "SHORTANSWER", "SHORT_ANSWER" -> "SHORT_ANSWER"; default -> throw new IllegalArgumentException("题型必须是单选题、多选题、判断题或问答题"); }; }
    private static String normalizeBoolean(String answer) { return switch (answer.trim()) { case "正确", "对", "是", "TRUE", "true" -> "TRUE"; case "错误", "错", "否", "FALSE", "false" -> "FALSE"; default -> throw new IllegalArgumentException("判断题答案必须填写正确或错误"); }; }
    private static String normalizeDifficulty(String value) { return switch (value) { case "易", "简单", "EASY" -> "EASY"; case "难", "困难", "HARD" -> "HARD"; default -> "MEDIUM"; }; }
    public record ImportResult(int imported, int skipped, List<String> errors) {}
}
