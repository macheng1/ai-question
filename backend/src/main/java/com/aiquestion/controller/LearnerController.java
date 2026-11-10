package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.service.AccountService;
import com.aiquestion.service.LearnerRecordService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/learner")
public class LearnerController {
  private final AccountService accounts;
  private final LearnerRecordService records;
  public LearnerController(AccountService accounts, LearnerRecordService records) {
    this.accounts=accounts; this.records=records;
  }
  @GetMapping("/trainings")
  public ApiResponse<?> trainings(@RequestHeader(value="Authorization",required=false) String auth) {
    return ApiResponse.ok(records.trainings(accounts.requireEmployee(auth)));
  }
  @GetMapping("/trainings/{id}")
  public ApiResponse<?> detail(@PathVariable long id,@RequestHeader(value="Authorization",required=false) String auth) {
    return ApiResponse.ok(records.detail(accounts.requireEmployee(auth),id));
  }
  @GetMapping("/records")
  public ApiResponse<?> records(@RequestHeader(value="Authorization",required=false) String auth) {
    return ApiResponse.ok(records.learningRecords(accounts.requireEmployee(auth)));
  }
  @GetMapping("/exams")
  public ApiResponse<?> exams(@RequestHeader(value="Authorization",required=false) String auth) {
    return ApiResponse.ok(records.exams(accounts.requireEmployee(auth)));
  }
  @GetMapping("/mistakes")
  public ApiResponse<?> mistakes(@RequestHeader(value="Authorization",required=false) String auth) {
    return ApiResponse.ok(records.mistakes(accounts.requireEmployee(auth)));
  }
}
