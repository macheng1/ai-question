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
            String[] sample = {"单选题", "示例：安全生产的方针是什么（请替换）", "安全第一", "预防为主", "综合治理", "以上都是", "ABCD", "填写解析（可选）", "安全生产", "中", "安全生产法"};
            Row header = sheet.createRow(0); Row example = sheet.createRow(1);
            CellStyle headerStyle = workbook.createCellStyle(); headerStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex()); headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i = 0; i < headers.length; i++) { Cell h = header.createCell(i); h.setCellValue(headers[i]); h.setCellStyle(headerStyle); example.createCell(i).setCellValue(sample[i]); sheet.setColumnWidth(i, i == 1 || i == 7 ? 36 * 256 : 18 * 256); }
            Sheet help = workbook.createSheet("填写说明");
            String[] notes = {"1. 一行一道题，第一行表头不要修改。", "2. 题型填写：单选题 / 多选题 / 判断题。", "3. 单选题、多选题填写选项 A-D；判断题选项可留空。", "4. 正确答案：单选填一个字母，多选填多个字母（如 ABC），判断题填写 正确 或 错误。", "5. 难度可填写：易 / 中 / 难，留空默认为中。", "6. 已存在相同题干的题目会自动跳过。"};
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
                    else {
                        String normalizedAnswer = answer.replaceAll("[，、,\\s]+", "").toUpperCase(Locale.ROOT);
                        for (int i = 0; i < 4; i++) { String text = value(formatter, row, 2 + i); if (!text.isBlank()) options.add(new OptionRequest(String.valueOf((char)('A' + i)), text, normalizedAnswer.contains(String.valueOf((char)('A' + i))), i)); }
                        if (options.size() < 2 || options.stream().noneMatch(OptionRequest::correct)) { throw new IllegalArgumentException("选项至少填写两项且必须包含正确答案"); }
                        answer = normalizedAnswer;
                    }
                    QuestionItem item = new QuestionItem(); item.setBankId(bankId); item.setQuestionType(type); item.setStem(stem); item.setAnswerText(answer); item.setAnalysis(value(formatter,row,7)); item.setTags(value(formatter,row,8)); item.setDifficulty(normalizeDifficulty(value(formatter,row,9))); item.setScore(BigDecimal.ONE); item.setSource(value(formatter,row,10)); item.setStatus("ENABLED"); itemRepository.insert(item);
                    for (OptionRequest request : options) { QuestionOption option = new QuestionOption(); option.setQuestionId(item.getId()); option.setOptionKey(request.optionKey()); option.setOptionText(request.optionText()); option.setCorrect(Boolean.TRUE.equals(request.correct())); option.setSortNo(request.sortNo()); optionRepository.insert(option); }
                    bankRepository.incrementQuestionCount(bankId); imported++;
                } catch (IllegalArgumentException e) { errors.add("第" + line + "行：" + e.getMessage()); }
            }
            return new ImportResult(imported, skipped, errors);
        } catch (Exception e) { if (e instanceof BusinessException be) throw be; throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 文件解析失败，请确认使用标准模板"); }
    }

    private static String value(DataFormatter formatter, Row row, int index) { Cell cell = row.getCell(index); return cell == null ? "" : formatter.formatCellValue(cell).trim(); }
    private static boolean blank(DataFormatter formatter, Row row, int index) { return value(formatter, row, index).isBlank(); }
    private static int findHeaderRow(Sheet sheet, DataFormatter formatter) { for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) { Row row = sheet.getRow(i); if (row != null && value(formatter, row, 0).replaceAll("\\s+", "").equals("题型")) return i; } return -1; }
    private static String normalizeType(String value) { String normalized = value == null ? "" : value.replaceAll("\\s+", "").replace("（必填）", "").replace("(必填)", "").toUpperCase(Locale.ROOT); return switch (normalized) { case "单选题", "单选", "SINGLE", "SINGLECHOICE" -> "SINGLE"; case "多选题", "多选", "MULTIPLE", "MULTIPLECHOICE" -> "MULTIPLE"; case "判断题", "判断", "TRUEFALSE", "TRUE_FALSE", "判断题型" -> "TRUE_FALSE"; default -> throw new IllegalArgumentException("题型必须是单选题、多选题或判断题"); }; }
    private static String normalizeBoolean(String answer) { return switch (answer.trim()) { case "正确", "对", "是", "TRUE", "true" -> "TRUE"; case "错误", "错", "否", "FALSE", "false" -> "FALSE"; default -> throw new IllegalArgumentException("判断题答案必须填写正确或错误"); }; }
    private static String normalizeDifficulty(String value) { return switch (value) { case "易", "简单", "EASY" -> "EASY"; case "难", "困难", "HARD" -> "HARD"; default -> "MEDIUM"; }; }
    public record ImportResult(int imported, int skipped, List<String> errors) {}
}
