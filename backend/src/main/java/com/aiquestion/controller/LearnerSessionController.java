package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.security.AccountIdentity;
import com.aiquestion.security.JwtTokenService;
import com.aiquestion.service.AccountService;
import com.aiquestion.service.LearnerIdentityService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/learner")
public class LearnerSessionController {
  private final LearnerIdentityService identities;
  private final AccountService accounts;
  private final JwtTokenService tokens;

  public LearnerSessionController(LearnerIdentityService identities, AccountService accounts, JwtTokenService tokens) {
    this.identities = identities;
    this.accounts = accounts;
    this.tokens = tokens;
  }

  public record ConfirmCommand(String type, long id, String name, String phone) {}

  @PostMapping("/session")
  public ApiResponse<AuthController.AuthResult> confirm(@RequestBody ConfirmCommand command) {
    var employee = identities.confirm(command.type(), command.id(), command.name(), command.phone());
    var identity = new AccountIdentity(employee.userId(), employee.accountType(), employee.enterpriseId());
    return ApiResponse.ok(new AuthController.AuthResult(employee.userId(), employee.name(), employee.phone(), tokens.issue(identity), employee.accountType(), employee.enterpriseId()));
  }

  @GetMapping("/me")
  public ApiResponse<AccountService.Profile> me(@RequestHeader(value="Authorization", required=false) String authorization) {
    return ApiResponse.ok(accounts.employee(accounts.requireEmployee(authorization).accountId()));
  }
}
