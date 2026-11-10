package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.TrainingReminderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/trainings/{id}/reminders")
public class TrainingReminderController {
  private final TrainingReminderService service;

  public TrainingReminderController(TrainingReminderService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<?> preview(@PathVariable long id) {
    return ApiResponse.ok(service.preview(id, ManagementAccess.userId()));
  }

  @PostMapping
  public ApiResponse<?> send(
      @PathVariable long id, @RequestBody TrainingReminderService.SendRequest request) {
    return ApiResponse.ok(service.send(id, ManagementAccess.userId(), request));
  }
}
