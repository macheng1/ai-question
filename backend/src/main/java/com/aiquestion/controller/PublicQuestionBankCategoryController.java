package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.domain.QuestionBankCategory;
import com.aiquestion.service.QuestionBankCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 游客端查询题库分类。分类查询不要求登录。 */
@Tag(name = "公开题库分类")
@RestController
@RequestMapping("/public/bank-categories")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class PublicQuestionBankCategoryController {
  private final QuestionBankCategoryService service;

  public PublicQuestionBankCategoryController(QuestionBankCategoryService service) {
    this.service = service;
  }

  @Operation(summary = "查询公开题库分类")
  @GetMapping
  public ApiResponse<List<QuestionBankCategory>> list() {
    return ApiResponse.ok(service.listPublicRoots());
  }
}
