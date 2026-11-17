package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import java.io.*;
import java.util.*;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EmployeeWorkbookService {
    private static final List<String> HEADERS = List.of("姓名", "手机号", "部门", "邮箱", "岗位", "学员类型", "性别", "工号", "备注", "状态");
    private final OrganizationService organization;

    public EmployeeWorkbookService(OrganizationService organization) {
        this.organization = organization;
    }

    public byte[] template() throws IOException {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("学员名单");
            var header = sheet.createRow(0);
            var text = book.createCellStyle();
            text.setDataFormat(book.createDataFormat().getFormat("@"));
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
                sheet.setColumnWidth(i, (i == 3 ? 32 : 20) * 256);
                sheet.setDefaultColumnStyle(i, text);
            }
            sheet.createFreezePane(0, 1);
            var departments = organization.departments().stream()
                    .filter(department -> "ACTIVE".equals(department.get("status")))
                    .map(department -> (String) department.get("name")).toList();
            String sampleDepartment = departments.isEmpty() ? "质量部" : departments.get(0);
            var samples = List.of(
                    List.of("张晨（示例）", "1380000XXXX", sampleDepartment, "zhangchen@example.com", "质检员", "内部", "男", "00018", "新员工培训", "在用"),
                    List.of("李悦（示例）", "1390000XXXX", sampleDepartment));
            for (var sample : samples) {
                var sampleRow = sheet.createRow(sheet.getLastRowNum() + 1);
                for (int i = 0; i < sample.size(); i++) sampleRow.createCell(i).setCellValue(sample.get(i));
            }
            var help = book.createSheet("填写说明");
            help.setColumnWidth(0, 92 * 256);
            var lines = List.of("「学员名单」第 2、3 行为示例。请替换为实际学员信息或删除示例行，保留表头，每行填写一名学员。",
                    "姓名、手机号、部门必填。手机号为 11 位，须与已有学员及本表其他学员不同。",
                    "学员类型填内部或外部（默认外部），性别填男、女或未填写（默认未填写）。",
                    "状态可选在用或停用（默认在用）。部门手动填写，已有部门直接匹配，新部门在导入成功时自动创建。下方列出当前在用部门。");
            int row = 0;
            for (String line : lines) help.createRow(row++).createCell(0).setCellValue(line);
            for (String department : departments) help.createRow(row++).createCell(0).setCellValue(department);
            var lists = sheet.getDataValidationHelper();
            addDropdown(sheet, "学员类型", lists.createExplicitListConstraint(new String[]{"内部", "外部"}));
            addDropdown(sheet, "性别", lists.createExplicitListConstraint(new String[]{"男", "女", "未填写"}));
            addDropdown(sheet, "状态", lists.createExplicitListConstraint(new String[]{"在用", "停用"}));
            book.write(bytes);
            return bytes.toByteArray();
        }
    }

    private static void addDropdown(Sheet sheet, String header, DataValidationConstraint options) {
        int column = HEADERS.indexOf(header);
        var cells = new CellRangeAddressList(1, SpreadsheetVersion.EXCEL2007.getLastRowIndex(), column, column);
        var validation = sheet.getDataValidationHelper().createValidation(options, cells);
        validation.setShowErrorBox(true);
        validation.createErrorBox("选项无效", "请从下拉列表选择" + header);
        sheet.addValidationData(validation);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private static String value(Row row, int col, DataFormatter formatter) {
        var cell = row.getCell(col);
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)
            throw invalid("请将" + HEADERS.get(col) + "填写为普通文本");
        if (cell.getCellType() == CellType.NUMERIC && (col == 1 || col == 7)
                && List.of("General", "@").contains(cell.getCellStyle().getDataFormatString()))
            return NumberToTextConverter.toText(cell.getNumericCellValue());
        return formatter.formatCellValue(cell).trim();
    }

    private static String choice(String value, Map<String, String> options, String fallback, String field) {
        if (value.isEmpty()) return fallback;
        if (!options.containsKey(value)) throw invalid(field + "填写有误，请参照模板说明");
        return options.get(value);
    }

    @Transactional
    public int importFile(MultipartFile file) throws IOException {
        XSSFWorkbook workbook;
        try (var input = file.getInputStream()) {
            workbook = new XSSFWorkbook(input);
        } catch (IOException | RuntimeException e) {
            throw invalid("无法读取文件，请使用下载的 .xlsx 模板");
        }
        try (workbook) {
            var sheet = workbook.getSheet("学员名单");
            var formatter = new DataFormatter();
            if (sheet == null || sheet.getRow(0) == null) throw invalid("缺少学员名单工作表或表头");
            for (int i = 0; i < HEADERS.size(); i++)
                if (!HEADERS.get(i).equals(formatter.formatCellValue(sheet.getRow(0).getCell(i)).trim()))
                    throw invalid("表头与模板不符，请使用下载的模板");
            Map<String, Long> departments = new HashMap<>();
            int count = 0;
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                try {
                    List<String> fields = new ArrayList<>();
                    for (int i = 0; i < HEADERS.size(); i++) fields.add(value(row, i, formatter));
                    if (fields.stream().allMatch(String::isEmpty)) continue;
                    long department = departments.computeIfAbsent(fields.get(2), organization::resolveImportDepartment);
                    organization.persistEmployee(null, fields.get(0), fields.get(1), fields.get(3), fields.get(4), department,
                            choice(fields.get(9), Map.of("在用", "ACTIVE", "停用", "INACTIVE"), "ACTIVE", "状态"),
                            choice(fields.get(5), Map.of("内部", "INTERNAL", "外部", "EXTERNAL"), "EXTERNAL", "学员类型"),
                            choice(fields.get(6), Map.of("男", "MALE", "女", "FEMALE", "未填写", "UNKNOWN"), "UNKNOWN", "性别"),
                            fields.get(7), fields.get(8));
                    count++;
                } catch (BusinessException e) {
                    throw invalid("第 " + (row.getRowNum() + 1) + " 行：" + e.getMessage() + "。本次未导入任何学员");
                }
            }
            if (count == 0) throw invalid("请至少填写一名学员");
            return count;
        }
    }
}
