package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.DashboardService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/dashboard")
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping("/options")
  public ApiResponse<Map<String, Object>> options() {
    return ApiResponse.ok(service.options(ManagementAccess.userId()));
  }

  @GetMapping
  public ApiResponse<DashboardService.Result> load(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) Long trainingId,
      @RequestParam(required = false) Long departmentId,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "trainings") String tab,
      @RequestParam(defaultValue = "ALL") String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize) {
    return ApiResponse.ok(
        service.load(
            ManagementAccess.userId(),
            start,
            end,
            trainingId,
            departmentId,
            category,
            tab,
            status,
            page,
            pageSize));
  }
}
