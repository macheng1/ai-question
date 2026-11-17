package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.service.OrganizationService;
import com.aiquestion.service.EmployeeWorkbookService;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management")
public class OrganizationController {
  private final OrganizationService service;

  private final EmployeeWorkbookService workbooks;

  public OrganizationController(OrganizationService service, EmployeeWorkbookService workbooks) {
    this.service = service;
    this.workbooks = workbooks;
  }

  @GetMapping("/departments")
  public ApiResponse<?> departments() {
    return ApiResponse.ok(service.departments());
  }

  @GetMapping("/employees")
  public ApiResponse<?> employees() {
    return ApiResponse.ok(service.employees());
  }

  @PostMapping("/departments")
  public ApiResponse<?> createDepartment(@RequestBody Department r) {
    return ApiResponse.ok(
        service.saveDepartment(null, r.name(), r.status() == null ? "ACTIVE" : r.status()));
  }

  @PutMapping("/departments/{id}")
  public ApiResponse<?> updateDepartment(@PathVariable Long id, @RequestBody Department r) {
    return ApiResponse.ok(service.saveDepartment(id, r.name(), r.status()));
  }

  @GetMapping("/employees/import/template")
  public ResponseEntity<byte[]> employeeTemplate() throws IOException {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename*=UTF-8''%E5%AD%A6%E5%91%98%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF.xlsx")
        .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .body(workbooks.template());
  }

  @PostMapping("/employees/import")
  public ApiResponse<Integer> importEmployees(@RequestParam("file") MultipartFile file) throws IOException {
    return ApiResponse.ok(workbooks.importFile(file));
  }

  @PostMapping("/employees")
  public ApiResponse<?> createEmployee(@RequestBody Employee r) {
    return save(null, r);
  }

  @PutMapping("/employees/{id}")
  public ApiResponse<?> updateEmployee(@PathVariable Long id, @RequestBody Employee r) {
    return save(id, r);
  }

  private ApiResponse<?> save(Long id, Employee r) {
    return ApiResponse.ok(
        service.saveEmployee(
            id,
            r.name(),
            r.phone(),
            r.email(),
            r.jobTitle(),
            r.departmentId(),
            r.status() == null ? "ACTIVE" : r.status(),
            r.learnerType(), r.gender(), r.employeeNo(), r.remark()));
  }

  public record Department(String name, String status) {}

  public record Employee(
      String name, String phone, String email, String jobTitle, Long departmentId, String status,
      String learnerType, String gender, String employeeNo, String remark) {}
}
