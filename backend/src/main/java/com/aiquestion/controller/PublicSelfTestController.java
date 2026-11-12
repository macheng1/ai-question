package com.aiquestion.controller;

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

/**
 * 在线自测公开目录接口。
 *
 * <p>自测目录和企业内部试卷是两个概念：目录面向游客，数据来源是公开题库； 题库详情页的“立即自测”仍然直接使用题库 ID，不会影响原有入口。
 */
@RestController
@RequestMapping("/public/self-tests")
public class /**
 * 后端业务组件。
 *
 * @author aiquestion
 */
PublicSelfTestController {

    @GetMapping
    public ApiResponse<PageResult<PublicSelfTestSummary>> list(@Valid PageQuery query) {
        return service.list(query);
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicSelfTestDetail> detail(@PathVariable Long id) {
        return service.detail(id);
    }

    public record PublicSelfTestSummary(Long id, Long bankId, String name, String description, int singleCount, int multipleCount, int trueFalseCount, int questionCount, String bankName) {
    }

    public record PublicSelfTestDetail(Long id, Long bankId, String name, String description, int singleCount, int multipleCount, int trueFalseCount, boolean randomOrder, int questionCount, String bankName, String category, String source) {
    }

    private final com.aiquestion.service.PublicSelfTestService service;

    public /**
     * 后端业务组件。
     *
     * @author aiquestion
     */
    PublicSelfTestController(com.aiquestion.service.PublicSelfTestService service) {
        this.service = service;
    }
}
