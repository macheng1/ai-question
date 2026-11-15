package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.LearnerAccess;
import com.aiquestion.service.AccountService.Profile;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 公开考试入口：加载真实试卷、保存逐题答案并完成交卷。
 */
@Tag(name = "公开考试", description = "考试详情、开始答题、逐题保存和交卷")
@RestController
@RequestMapping("/public/exams")
public class /**
 * 后端业务组件。
 *
 * @author aiquestion
 */
PublicExamController {

    @Operation(summary = "查询考试详情和题目")
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean revealAnswers, @RequestParam(required = false) Long attemptId) {
        return service.get(id, revealAnswers, attemptId);
    }

    @Operation(summary = "开始或恢复考试")
    @PostMapping("/{id}/start")
    public ApiResponse<Map<String, Object>> start(@PathVariable Long id, @RequestBody CandidateCommand command, @RequestAttribute(LearnerAccess.PROFILE) Profile learner) {
        return service.start(id, learner.userId(), command);
    }

    @Operation(summary = "检查考生是否可以开始或重做考试")
    @GetMapping("/{id}/eligibility")
    public ApiResponse<Map<String, Object>> eligibility(@PathVariable Long id, @RequestParam String name, @RequestParam String phone, @RequestAttribute(LearnerAccess.PROFILE) Profile learner) {
        return service.eligibility(id, learner.userId());
    }

    @Operation(summary = "查询考生进行中的考试")
    @GetMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> progress(@PathVariable Long id, @RequestParam String name, @RequestParam String phone, @RequestAttribute(LearnerAccess.PROFILE) Profile learner) {
        return service.progress(id, learner.userId());
    }

    @Operation(summary = "保存单题答案")
    @PutMapping("/attempts/{attemptId}/answers/{questionId}")
    public ApiResponse<Void> answer(@PathVariable Long attemptId, @PathVariable Long questionId, @RequestBody AnswerCommand command) {
        return service.answer(attemptId, questionId, command);
    }

    @Operation(summary = "交卷并计算成绩")
    @PostMapping("/attempts/{attemptId}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long attemptId) {
        return service.submit(attemptId);
    }

    @Operation(summary = "查询当前学员的考试成绩")
    @GetMapping("/{id}/records")
    public ApiResponse<List<Map<String, Object>>> records(@PathVariable Long id, @RequestParam String phone, @RequestAttribute(LearnerAccess.PROFILE) Profile learner) {
        return service.records(id, learner.userId());
    }

    @Operation(summary = "查询单次考试成绩详情")
    @GetMapping("/attempts/{attemptId}/result")
    public ApiResponse<Map<String, Object>> result(@PathVariable Long attemptId) {
        return service.result(attemptId);
    }

    public record CandidateCommand(String name, String phone) {
    }

    public record AnswerCommand(String answer) {
    }

    private final com.aiquestion.service.PublicExamService service;

    public /**
     * 后端业务组件。
     *
     * @author aiquestion
     */
    PublicExamController(com.aiquestion.service.PublicExamService service) {
        this.service = service;
    }
}
