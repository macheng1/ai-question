package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.PageQuery;
import com.aiquestion.common.PageResult;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 在线自测公开目录接口。
 *
 * <p>自测目录和企业内部试卷是两个概念：目录面向游客，数据来源是公开题库；
 * 题库详情页的“立即自测”仍然直接使用题库 ID，不会影响原有入口。</p>
 */
@RestController
@RequestMapping("/public/self-tests")
public class PublicSelfTestController {
    private final JdbcTemplate jdbcTemplate;

    public PublicSelfTestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<PageResult<PublicSelfTestSummary>> list(@Valid PageQuery query) {
        String keyword = query.getKeyword();
        String condition = keyword == null || keyword.isBlank() ? "" : " AND (p.name LIKE ? OR p.description LIKE ? OR b.name LIKE ?)";
        Object[] args = keyword == null || keyword.isBlank() ? new Object[]{} : new Object[]{like(keyword), like(keyword), like(keyword)};
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_self_test p JOIN question_bank b ON b.id=p.bank_id WHERE p.published=TRUE AND b.published=TRUE" + condition,
                Long.class, args);
        List<PublicSelfTestSummary> records = jdbcTemplate.query(
                "SELECT p.id,p.bank_id,p.name,p.description,p.single_count,p.multiple_count,p.true_false_count," +
                        "b.question_count,b.name bank_name FROM public_self_test p JOIN question_bank b ON b.id=p.bank_id " +
                        "WHERE p.published=TRUE AND b.published=TRUE" + condition +
                        " ORDER BY p.sort_no ASC,p.id ASC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new PublicSelfTestSummary(rs.getLong("id"), rs.getLong("bank_id"), rs.getString("name"),
                        rs.getString("description"), rs.getInt("single_count"), rs.getInt("multiple_count"),
                        rs.getInt("true_false_count"), rs.getInt("question_count"), rs.getString("bank_name")),
                concat(args, query.getPageSize(), query.getOffset()));
        return ApiResponse.ok(PageResult.of(records, total, query));
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicSelfTestDetail> detail(@PathVariable Long id) {
        List<PublicSelfTestDetail> rows = jdbcTemplate.query(
                "SELECT p.id,p.bank_id,p.name,p.description,p.single_count,p.multiple_count,p.true_false_count,p.random_order," +
                        "b.question_count,b.name bank_name,b.category,b.source FROM public_self_test p JOIN question_bank b ON b.id=p.bank_id " +
                        "WHERE p.id=? AND p.published=TRUE AND b.published=TRUE",
                (rs, rowNum) -> new PublicSelfTestDetail(rs.getLong("id"), rs.getLong("bank_id"), rs.getString("name"),
                        rs.getString("description"), rs.getInt("single_count"), rs.getInt("multiple_count"),
                        rs.getInt("true_false_count"), rs.getBoolean("random_order"), rs.getInt("question_count"),
                        rs.getString("bank_name"), rs.getString("category"), rs.getString("source")), id);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
        return ApiResponse.ok(rows.get(0));
    }

    private static String like(String value) { return "%" + value.trim() + "%"; }

    private static Object[] concat(Object[] args, Object... tail) {
        Object[] result = new Object[args.length + tail.length];
        System.arraycopy(args, 0, result, 0, args.length);
        System.arraycopy(tail, 0, result, args.length, tail.length);
        return result;
    }

    public record PublicSelfTestSummary(Long id, Long bankId, String name, String description, int singleCount,
                                        int multipleCount, int trueFalseCount, int questionCount, String bankName) {}

    public record PublicSelfTestDetail(Long id, Long bankId, String name, String description, int singleCount,
                                       int multipleCount, int trueFalseCount, boolean randomOrder, int questionCount,
                                       String bankName, String category, String source) {}
}
