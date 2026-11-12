package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.service.OrganizationService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/management")
public class OrganizationController {
  private final OrganizationService service;

  public OrganizationController(OrganizationService service) {
    this.service = service;
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
