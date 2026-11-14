package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.ManagementAccess;
import com.aiquestion.service.DashboardService;
import java.time.LocalDate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import com.aiquestion.service.DashboardLogExporter;
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

  @GetMapping("/logs/export")
  public ResponseEntity<byte[]> exportLogs(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) Long trainingId,
      @RequestParam(required = false) Long departmentId,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "ALL") String status) throws IOException {
    var rows = service.exportLogs(ManagementAccess.userId(), start, end, trainingId, departmentId, category, status);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename("学习日志.xlsx", StandardCharsets.UTF_8).build().toString())
        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(DashboardLogExporter.write(rows));
  }

  @GetMapping("/incomplete/export")
  public ResponseEntity<byte[]> exportRoster(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) Long trainingId,
      @RequestParam(required = false) Long departmentId,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "ALL") String status) throws IOException {
    var rows = service.exportRoster(ManagementAccess.userId(), start, end, trainingId, departmentId, category, status);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename("培训人员名单.xlsx", StandardCharsets.UTF_8).build().toString())
        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(DashboardLogExporter.writeRoster(rows));
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
