package com.aiquestion.controller;

import com.aiquestion.common.*;
import com.aiquestion.domain.QuestionItem;
import com.aiquestion.domain.TestPaper;
import com.aiquestion.service.TestPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@Tag(name="试卷管理")
@RestController @RequestMapping("/management/papers")
public class TestPaperController {
    private final TestPaperService service;
    public TestPaperController(TestPaperService service){this.service=service;}
    @Operation(summary="分页查询试卷") @GetMapping public ApiResponse<PageResult<TestPaper>> page(@Valid PageQuery query){return ApiResponse.ok(service.page(query));}
    @Operation(summary="试卷详情") @GetMapping("/{id}") public ApiResponse<TestPaper> get(@PathVariable Long id){return ApiResponse.ok(service.get(id));}
    @Operation(summary="创建试卷") @PostMapping public ApiResponse<TestPaper> create(@Valid @RequestBody SavePaperRequest r){return ApiResponse.ok(service.create(r.name(),r.description(),r.mode(),r.totalScore(),r.passScore(),r.durationMinutes()));}
    @Operation(summary="修改试卷") @PutMapping("/{id}") public ApiResponse<TestPaper> update(@PathVariable Long id,@Valid @RequestBody SavePaperRequest r){return ApiResponse.ok(service.update(id,r.name(),r.description(),r.mode(),r.totalScore(),r.passScore(),r.durationMinutes()));}
    @Operation(summary="修改试卷状态") @PatchMapping("/{id}/status") public ApiResponse<TestPaper> status(@PathVariable Long id,@RequestParam String value){return ApiResponse.ok(service.changeStatus(id,value));}
    @Operation(summary="删除试卷") @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id){service.delete(id);return ApiResponse.ok(null);}
    @Operation(summary="复制试卷") @PostMapping("/{id}/copy") public ApiResponse<TestPaper> copy(@PathVariable Long id,@Valid @RequestBody CopyPaperRequest r){return ApiResponse.ok(service.copy(id,r.name(),r.description()));}
    @Operation(summary="查询试卷题目") @GetMapping("/{id}/questions") public ApiResponse<List<QuestionItem>> questions(@PathVariable Long id){return ApiResponse.ok(service.questions(id));}
    @Operation(summary="向试卷添加题目") @PostMapping("/{id}/questions") public ApiResponse<QuestionItem> addQuestion(@PathVariable Long id,@Valid @RequestBody AddQuestionRequest r){return ApiResponse.ok(service.addQuestion(id,r.questionId(),r.score()));}
    @Operation(summary="移除试卷题目") @DeleteMapping("/{id}/questions/{questionId}") public ApiResponse<Void> removeQuestion(@PathVariable Long id,@PathVariable Long questionId){service.removeQuestion(id,questionId);return ApiResponse.ok(null);}
    @Operation(summary="修改试卷题目分值") @PatchMapping("/{id}/questions/{questionId}/score") public ApiResponse<QuestionItem> updateQuestionScore(@PathVariable Long id,@PathVariable Long questionId,@RequestParam BigDecimal value){return ApiResponse.ok(service.updateQuestionScore(id,questionId,value));}
    @Operation(summary="查询随机组卷配置") @GetMapping("/{id}/random-config") public ApiResponse<List<java.util.Map<String,Object>>> randomConfig(@PathVariable Long id){return ApiResponse.ok(service.randomConfig(id));}
    @Operation(summary="添加随机组卷题库") @PostMapping("/{id}/random-config/banks") public ApiResponse<Void> addRandomBank(@PathVariable Long id,@Valid @RequestBody RandomBankRequest r){service.addRandomBank(id,r.bankId());return ApiResponse.ok(null);}
    @Operation(summary="删除随机组卷题库") @DeleteMapping("/{id}/random-config/banks/{bankId}") public ApiResponse<Void> removeRandomBank(@PathVariable Long id,@PathVariable Long bankId){service.removeRandomBank(id,bankId);return ApiResponse.ok(null);}
    @Operation(summary="保存随机组卷配置") @PutMapping("/{id}/random-config") public ApiResponse<Void> saveRandomConfig(@PathVariable Long id,@RequestBody List<TestPaperService.RandomConfigItem> items){service.saveRandomConfig(id,items);return ApiResponse.ok(null);}
    public record SavePaperRequest(@NotBlank @Size(max=160) String name,@Size(max=500) String description,@NotBlank String mode,BigDecimal totalScore,BigDecimal passScore,Integer durationMinutes){}
    public record CopyPaperRequest(@NotBlank @Size(max=160) String name,@Size(max=500) String description){}
    public record AddQuestionRequest(@NotNull Long questionId,BigDecimal score){}
    public record RandomBankRequest(@NotNull Long bankId){}
}
