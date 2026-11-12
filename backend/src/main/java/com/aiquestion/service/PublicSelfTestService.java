package com.aiquestion.service;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.common.PageQuery;
import com.aiquestion.common.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.aiquestion.controller.PublicSelfTestController.*;
import com.aiquestion.repository.PublicSelfTestRepository;

/**
 * 在线自测公开目录接口。
 *
 * <p>自测目录和企业内部试卷是两个概念：目录面向游客，数据来源是公开题库； 题库详情页的“立即自测”仍然直接使用题库 ID，不会影响原有入口。
 */
@org.springframework.stereotype.Service()
public class PublicSelfTestService {

    private final PublicSelfTestRepository persistence;

    public PublicSelfTestService(PublicSelfTestRepository persistence) {
        this.persistence = persistence;
    }

    public ApiResponse<PageResult<PublicSelfTestSummary>> list(PageQuery query) {
        String keyword = query.getKeyword();
        String search = keyword == null || keyword.isBlank() ? null : like(keyword);
        long total = com.aiquestion.repository.RepositoryRows.one(persistence.countPublishedTests(search, search, search, search));
        List<PublicSelfTestSummary> records = persistence.findPublishedTests(search, search, search, search, query.getPageSize(), query.getOffset()).stream().map(rs -> new PublicSelfTestSummary(com.aiquestion.repository.RepositoryRows.longValue(rs, "id"), com.aiquestion.repository.RepositoryRows.longValue(rs, "bank_id"), com.aiquestion.repository.RepositoryRows.string(rs, "name"), com.aiquestion.repository.RepositoryRows.string(rs, "description"), com.aiquestion.repository.RepositoryRows.intValue(rs, "single_count"), com.aiquestion.repository.RepositoryRows.intValue(rs, "multiple_count"), com.aiquestion.repository.RepositoryRows.intValue(rs, "true_false_count"), com.aiquestion.repository.RepositoryRows.intValue(rs, "question_count"), com.aiquestion.repository.RepositoryRows.string(rs, "bank_name"))).toList();
        return ApiResponse.ok(PageResult.of(records, total, query));
    }

    public ApiResponse<PublicSelfTestDetail> detail(Long id) {
        List<PublicSelfTestDetail> rows = persistence.findPublishedTest(id).stream().map(rs -> new PublicSelfTestDetail(com.aiquestion.repository.RepositoryRows.longValue(rs, "id"), com.aiquestion.repository.RepositoryRows.longValue(rs, "bank_id"), com.aiquestion.repository.RepositoryRows.string(rs, "name"), com.aiquestion.repository.RepositoryRows.string(rs, "description"), com.aiquestion.repository.RepositoryRows.intValue(rs, "single_count"), com.aiquestion.repository.RepositoryRows.intValue(rs, "multiple_count"), com.aiquestion.repository.RepositoryRows.intValue(rs, "true_false_count"), com.aiquestion.repository.RepositoryRows.booleanValue(rs, "random_order"), com.aiquestion.repository.RepositoryRows.intValue(rs, "question_count"), com.aiquestion.repository.RepositoryRows.string(rs, "bank_name"), com.aiquestion.repository.RepositoryRows.string(rs, "category"), com.aiquestion.repository.RepositoryRows.string(rs, "source"))).toList();
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND);
        }
        return ApiResponse.ok(rows.get(0));
    }

    private static String like(String value) {
        return "%" + value.trim() + "%";
    }
}
