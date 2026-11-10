package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management/overview")
public class ManagementOverviewController {
  private final JdbcTemplate jdbc;
  public ManagementOverviewController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  @GetMapping
  public ApiResponse<?> overview() {
    long owner = ManagementAccess.userId();
    return ApiResponse.ok(Map.of(
        "trainingCount", jdbc.queryForObject("SELECT COUNT(*) FROM training WHERE created_by=?", Long.class, owner),
        "employeeCount", jdbc.queryForObject("SELECT COUNT(*) FROM organization_employee WHERE created_by=?", Long.class, owner),
        "examCount", jdbc.queryForObject("SELECT COUNT(*) FROM assessment_task WHERE created_by=?", Long.class, owner)));
  }
}
