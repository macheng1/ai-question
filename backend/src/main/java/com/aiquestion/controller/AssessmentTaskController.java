package com.aiquestion.controller;

import com.aiquestion.common.*;
import com.aiquestion.domain.AssessmentTask;
import com.aiquestion.service.AssessmentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@Tag(name = "考试任务管理")
@RestController
@RequestMapping("/management/assessments")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class AssessmentTaskController {
  private final AssessmentTaskService service;

  public AssessmentTaskController(AssessmentTaskService s) {
    service = s;
  }

  @Operation(summary = "分页查询考试任务")
  @GetMapping
  public ApiResponse<PageResult<AssessmentTask>> page(
      @Valid PageQuery q,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime startAt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime endAt) {
    return ApiResponse.ok(service.page(q, startAt, endAt));
  }

  @Operation(summary = "考试任务详情")
  @GetMapping("/{id}")
  public ApiResponse<AssessmentTask> get(@PathVariable Long id) {
    return ApiResponse.ok(service.get(id));
  }

  @Operation(summary = "创建考试任务")
  @PostMapping
  public ApiResponse<AssessmentTask> create(@Valid @RequestBody SaveTaskRequest r) {
    return ApiResponse.ok(
        service.create(
            r.name(),
            r.paperId(),
            r.startAt(),
            r.endAt(),
            r.durationMinutes(),
            r.allowRetake(),
            r.maxAttempts(),
            r.shuffleQuestions(),
            r.shuffleOptions(),
            r.showResultImmediately()));
  }

  @Operation(summary = "修改考试任务")
  @PutMapping("/{id}")
  public ApiResponse<AssessmentTask> update(
      @PathVariable Long id, @Valid @RequestBody UpdateTaskRequest r) {
    return ApiResponse.ok(
        service.update(
            id,
            r.name(),
            r.startAt(),
            r.endAt(),
            r.durationMinutes(),
            r.allowRetake(),
            r.maxAttempts(),
            r.shuffleQuestions(),
            r.shuffleOptions(),
            r.showResultImmediately()));
  }

  @Operation(summary = "发布或结束考试")
  @PatchMapping("/{id}/status")
  public ApiResponse<AssessmentTask> status(@PathVariable Long id, @RequestParam String value) {
    return ApiResponse.ok(service.updateStatus(id, value));
  }

  @Operation(summary = "考试成绩统计")
  @GetMapping("/{id}/statistics")
  public ApiResponse<Map<String, Object>> statistics(
      @PathVariable Long id, @RequestParam(defaultValue = "false") boolean includeRetake) {
    return ApiResponse.ok(service.statistics(id, includeRetake));
  }

  @Operation(summary = "考试统计详情")
  @GetMapping("/{id}/statistics/detail")
  public ApiResponse<Map<String, Object>> statisticsDetail(
      @PathVariable Long id, @RequestParam(defaultValue = "false") boolean includeRetake) {
    return ApiResponse.ok(service.statisticsDetail(id, includeRetake));
  }

  @Operation(summary = "考试成绩明细")
  @GetMapping("/{id}/records")
  public ApiResponse<List<Map<String, Object>>> records(
      @PathVariable Long id, @RequestParam(defaultValue = "false") boolean includeRetake) {
    return ApiResponse.ok(service.records(id, includeRetake));
  }

  @GetMapping("/{id}/roster") public ApiResponse<?> roster(@PathVariable Long id){return ApiResponse.ok(service.roster(id));}

  @Operation(summary = "删除考试任务")
  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ApiResponse.ok(null);
  }

  public record SaveTaskRequest(
      @NotBlank String name,
      @NotNull Long paperId,
      @NotNull LocalDateTime startAt,
      @NotNull LocalDateTime endAt,
      Integer durationMinutes,
      Boolean allowRetake,
      Integer maxAttempts,
      Boolean shuffleQuestions,
      Boolean shuffleOptions,
      Boolean showResultImmediately) {}

  public record UpdateTaskRequest(
      @NotBlank String name,
      @NotNull LocalDateTime startAt,
      @NotNull LocalDateTime endAt,
      Integer durationMinutes,
      Boolean allowRetake,
      Integer maxAttempts,
      Boolean shuffleQuestions,
      Boolean shuffleOptions,
      Boolean showResultImmediately) {}
}
