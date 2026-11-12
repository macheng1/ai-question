package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.TrainingEligibilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrainingAudienceController {
  private final TrainingEligibilityService eligibility;

  public TrainingAudienceController(TrainingEligibilityService eligibility) {
    this.eligibility = eligibility;
  }

  @GetMapping("/management/trainings/audience-eligibility")
  public ApiResponse<?> eligibility(@RequestParam long assessmentId, @RequestParam(required = false) Long trainingId) {
    return ApiResponse.ok(eligibility.excludedEmployees(assessmentId, ManagementAccess.userId(), trainingId));
  }
}
