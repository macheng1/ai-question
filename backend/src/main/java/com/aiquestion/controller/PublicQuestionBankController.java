package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.PageQuery;
import com.aiquestion.common.PageResult;
import com.aiquestion.domain.QuestionBank;
import com.aiquestion.service.QuestionBankService;
import com.aiquestion.service.QuestionItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "公开题库", description = "公开端题库查询接口")
@RestController
@RequestMapping("/public/banks")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class PublicQuestionBankController {
  private final QuestionBankService service;
  private final QuestionItemService questionService;

  public PublicQuestionBankController(
      QuestionBankService service, QuestionItemService questionService) {
    this.service = service;
    this.questionService = questionService;
  }

  @Operation(summary = "查询公开题库")
  @GetMapping
  public ApiResponse<PageResult<QuestionBank>> list(@Valid PageQuery query) {
    return ApiResponse.ok(service.list(query));
  }

  @Operation(summary = "查询题库详情")
  @GetMapping("/{id}")
  public ApiResponse<QuestionBank> get(@PathVariable Long id) {
    return ApiResponse.ok(service.getPublished(id));
  }

  @Operation(summary = "查询公开题库题目")
  @GetMapping("/{id}/questions")
  public ApiResponse<PageResult<com.aiquestion.domain.QuestionItem>> questions(
      @PathVariable Long id, @Valid PageQuery query) {
    return ApiResponse.ok(questionService.publicPage(id, query));
  }
}
