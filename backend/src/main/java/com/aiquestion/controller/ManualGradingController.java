package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.repository.ManualGradingRepository;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.ManualGradingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/assessments/{taskId}/grading")
public class ManualGradingController {
    private final ManualGradingService grading;
    public ManualGradingController(ManualGradingService grading) { this.grading = grading; }

    @GetMapping
    public ApiResponse<List<ManualGradingRepository.Pending>> pending(@PathVariable long taskId) {
        return ApiResponse.ok(grading.pending(taskId, ManagementAccess.userId()));
    }
    @GetMapping("/{attemptId}")
    public ApiResponse<List<ManualGradingRepository.Question>> questions(@PathVariable long taskId, @PathVariable long attemptId) {
        return ApiResponse.ok(grading.questions(taskId, attemptId, ManagementAccess.userId()));
    }
    @PutMapping("/{attemptId}")
    public ApiResponse<Void> finish(@PathVariable long taskId, @PathVariable long attemptId, @RequestBody Map<Long, BigDecimal> scores) {
        grading.finish(taskId, attemptId, ManagementAccess.userId(), scores);
        return ApiResponse.ok(null);
    }
}
