package com.aiquestion.controller;

import com.aiquestion.common.*;
import com.aiquestion.domain.QuestionItem;
import com.aiquestion.security.JwtTokenService;
import com.aiquestion.service.QuestionImportService;
import com.aiquestion.service.QuestionItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "题目管理")
@RestController
@RequestMapping("/management/questions")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class QuestionItemController {
  private final QuestionItemService service;
  private final QuestionImportService importService;
  private final JwtTokenService tokens;

  public QuestionItemController(
      QuestionItemService service, QuestionImportService importService, JwtTokenService tokens) {
    this.service = service;
    this.importService = importService;
    this.tokens = tokens;
  }

  @Operation(summary = "分页查询题目")
  @GetMapping
  public ApiResponse<PageResult<QuestionItem>> page(
      @RequestParam(required = false) Long bankId,
      @Valid PageQuery query,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return ApiResponse.ok(service.page(bankId, query, userId(authorization)));
  }

  @Operation(summary = "题目详情")
  @GetMapping("/{id}")
  public ApiResponse<QuestionItem> get(
      @PathVariable Long id,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return ApiResponse.ok(service.get(id, userId(authorization)));
  }

  @Operation(summary = "新建题目")
  @PostMapping
  public ApiResponse<QuestionItem> create(
      @Valid @RequestBody SaveQuestionRequest r,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return ApiResponse.ok(
        service.create(
            r.bankId(),
            r.questionType(),
            r.stem(),
            r.answerText(),
            r.analysis(),
            r.tags(),
            r.source(),
            r.difficulty(),
            r.score(),
            r.options(),
            userId(authorization)));
  }

  @Operation(summary = "修改题目")
  @PutMapping("/{id}")
  public ApiResponse<QuestionItem> update(
      @PathVariable Long id,
      @Valid @RequestBody UpdateQuestionRequest r,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return ApiResponse.ok(
        service.update(
            id,
            r.questionType(),
            r.stem(),
            r.answerText(),
            r.analysis(),
            r.tags(),
            r.source(),
            r.difficulty(),
            r.score(),
            r.options(),
            userId(authorization)));
  }

  @Operation(summary = "删除题目")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(
      @PathVariable Long id,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    service.delete(id, userId(authorization));
    return ApiResponse.ok(null);
  }

  @Operation(summary = "下载试题导入模板")
  @GetMapping("/template")
  public ResponseEntity<byte[]> template() {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=question-import-template.xlsx")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(importService.template());
  }

  @Operation(summary = "导入试题模板")
  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<QuestionImportService.ImportResult> importQuestions(
      @RequestParam Long bankId,
      @RequestPart("file") MultipartFile file,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return ApiResponse.ok(importService.importFile(bankId, file, userId(authorization)));
  }

  private Long userId(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    try {
      return Long.parseLong(tokens.subject(authorization.substring(7), false));
    } catch (RuntimeException ex) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }
  }

  public record OptionRequest(
      @NotBlank String optionKey, @NotBlank String optionText, Boolean correct, Integer sortNo) {}

  public record SaveQuestionRequest(
      @NotNull Long bankId,
      @NotBlank @Size(max = 20) String questionType,
      @NotBlank String stem,
      String answerText,
      String analysis,
      String tags,
      String source,
      String difficulty,
      BigDecimal score,
      List<OptionRequest> options) {}

  public record UpdateQuestionRequest(
      @NotBlank @Size(max = 20) String questionType,
      @NotBlank String stem,
      String answerText,
      String analysis,
      String tags,
      String source,
      String difficulty,
      BigDecimal score,
      List<OptionRequest> options) {}
}
