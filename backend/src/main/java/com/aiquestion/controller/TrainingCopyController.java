package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.TrainingCopyService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrainingCopyController {
  private final TrainingCopyService service;

  public TrainingCopyController(TrainingCopyService service) { this.service = service; }

  @PostMapping("/management/trainings/{id}/copy")
  public ApiResponse<?> copy(@PathVariable long id) {
    return ApiResponse.ok(service.copy(id, ManagementAccess.userId()));
  }
}
