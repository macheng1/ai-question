package com.aiquestion.controller;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.common.RedisService;
import com.aiquestion.security.JwtTokenService;
import com.aiquestion.security.TokenPair;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户认证", description = "用户注册、登录和令牌接口")
@RestController
@RequestMapping("/auth")
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class AuthController {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final JwtTokenService tokens;
  private final RedisService redis;
  private final com.aiquestion.service.AccountService accounts;

  public AuthController(
      JdbcTemplate jdbc, PasswordEncoder encoder, JwtTokenService tokens, RedisService redis, com.aiquestion.service.AccountService accounts) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.tokens = tokens;
    this.redis = redis;
    this.accounts = accounts;
  }

  public record AuthCommand(
      @NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
      @NotBlank @Size(min = 6, max = 64) String password,
      String confirmPassword,
      String captcha,
      String name,
      String unitName,
      String province,
      String city,
      String district,
      String address) {}

  public record AuthResult(Long userId, String name, String phone, TokenPair token,
      com.aiquestion.security.AccountType accountType, long enterpriseId) {
    public AuthResult(Long id, String name, String phone, TokenPair token) {
      this(id, name, phone, token, com.aiquestion.security.AccountType.ADMIN, id);
    }
  }

  @PostMapping("/employee/login")
  public ApiResponse<AuthResult> employeeLogin(@Valid @RequestBody AuthCommand command) {
    var employee = accounts.loginEmployee(command.phone(), command.password());
    return ApiResponse.ok(new AuthResult(employee.userId(), employee.name(), employee.phone(),
        tokens.issue(new com.aiquestion.security.AccountIdentity(employee.userId(), employee.accountType(), employee.enterpriseId())),
        employee.accountType(), employee.enterpriseId()));
  }

  public record UserProfile(
      Long userId,
      String name,
      String phone,
      String unitName,
      String province,
      String city,
      String district,
      String address,
      String roleCode) {
    public com.aiquestion.security.AccountType getAccountType() { return com.aiquestion.security.AccountType.ADMIN; }
    public Long getEnterpriseId() { return userId; }
  }

  public record ProfileUpdateCommand(
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 160) String unitName,
      @Size(max = 255) String address) {}

  public record PasswordChangeCommand(
      @NotBlank String currentPassword,
      @NotBlank @Size(min = 6, max = 64) String newPassword,
      @NotBlank String confirmPassword) {}

  @Operation(summary = "发送注册验证码")
  @PostMapping("/captcha")
  public ApiResponse<Map<String, String>> captcha(
      @RequestParam @Pattern(regexp = "^1\\d{10}$") String phone) {
    String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    redis.set("auth:captcha:" + phone, code, Duration.ofMinutes(1));
    return ApiResponse.ok(Map.of("message", "验证码已发送", "testCode", code));
  }

  @Operation(summary = "注册账号")
  @PostMapping("/register")
  public ApiResponse<AuthResult> register(@Valid @RequestBody AuthCommand command) {
    if (command.confirmPassword() == null
        || !command.password().equals(command.confirmPassword())) {
      throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
    }
    String cachedCaptcha = redis.get("auth:captcha:" + command.phone());
    if (cachedCaptcha != null) cachedCaptcha = cachedCaptcha.replace("\"", "");
    if (cachedCaptcha == null || !cachedCaptcha.equals(command.captcha())) {
      throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
    }
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE phone=?", Integer.class, command.phone())
        > 0) throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
    String name =
        command.name() == null || command.name().isBlank()
            ? "用户" + command.phone().substring(7)
            : command.name();
    if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE name=?", Integer.class, name)
        > 0) {
      throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
    }
    jdbc.update(
        "INSERT INTO app_user(name,unit_name,province,city,district,address,phone,password_hash,role_code,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        name,
        command.unitName(),
        command.province(),
        command.city(),
        command.district(),
        command.address(),
        command.phone(),
        encoder.encode(command.password()),
        "CANDIDATE",
        "ACTIVE",
        LocalDateTime.now(),
        LocalDateTime.now());
    redis.delete("auth:captcha:" + command.phone());
    Long id =
        jdbc.queryForObject("SELECT id FROM app_user WHERE phone=?", Long.class, command.phone());
    return ApiResponse.ok(
        new AuthResult(id, name, command.phone(), tokens.issue(String.valueOf(id))));
  }

  @Operation(summary = "登录")
  @PostMapping("/login")
  public ApiResponse<AuthResult> login(@Valid @RequestBody AuthCommand command) {
    var user = accounts.loginAdministrator(command.phone(), command.password());
    return ApiResponse.ok(new AuthResult(user.userId(), user.name(), user.phone(),
        tokens.issue(new com.aiquestion.security.AccountIdentity(user.userId(), user.accountType(), user.enterpriseId())),
        user.accountType(), user.enterpriseId()));
  }

  @Operation(summary = "获取当前用户信息")
  @GetMapping("/me")
  public ApiResponse<?> me(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    var identity = accounts.current(authorization);
    if (identity.accountType() == com.aiquestion.security.AccountType.EMPLOYEE)
      return ApiResponse.ok(accounts.employee(identity.accountId()));
    String userId = String.valueOf(identity.accountId());
    var users =
        jdbc.query(
            "SELECT id,name,phone,unit_name,province,city,district,address,role_code FROM app_user WHERE id=? AND status='ACTIVE'",
            (rs, row) ->
                new UserProfile(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("phone"),
                    rs.getString("unit_name"),
                    rs.getString("province"),
                    rs.getString("city"),
                    rs.getString("district"),
                    rs.getString("address"),
                    rs.getString("role_code")),
            userId);
    if (users.isEmpty()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    return ApiResponse.ok(users.get(0));
  }

  @Operation(summary = "修改当前用户信息")
  @PutMapping("/me/profile")
  public ApiResponse<?> updateProfile(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody ProfileUpdateCommand command) {
    String userId = currentUserId(authorization);
    String name = command.name().trim();
    String unitName = command.unitName().trim();
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE name=? AND id<>?", Integer.class, name, userId)
        > 0) {
      throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
    }
    jdbc.update(
        "UPDATE app_user SET name=?, unit_name=?, address=?, updated_at=? WHERE id=? AND status='ACTIVE'",
        name,
        unitName,
        blankToNull(command.address()),
        LocalDateTime.now(),
        userId);
    return me(authorization);
  }

  @Operation(summary = "修改当前用户密码")
  @PutMapping("/me/password")
  public ApiResponse<Void> changePassword(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody PasswordChangeCommand command) {
    var identity = accounts.current(authorization);
    if (!command.newPassword().equals(command.confirmPassword())) {
      throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
    }
    if (identity.accountType() == com.aiquestion.security.AccountType.EMPLOYEE) {
      accounts.changeEmployeePassword(identity, command.currentPassword(), command.newPassword());
      return ApiResponse.ok(null);
    }
    String userId = String.valueOf(identity.accountId());
    String passwordHash =
        jdbc.queryForObject(
            "SELECT password_hash FROM app_user WHERE id=? AND status='ACTIVE'",
            String.class,
            userId);
    if (passwordHash == null || !encoder.matches(command.currentPassword(), passwordHash)) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    jdbc.update(
        "UPDATE app_user SET password_hash=?, updated_at=? WHERE id=? AND status='ACTIVE'",
        encoder.encode(command.newPassword()),
        LocalDateTime.now(),
        userId);
    return ApiResponse.ok(null);
  }

  private String currentUserId(String authorization) {
    var identity = accounts.current(authorization);
    if (identity.accountType() != com.aiquestion.security.AccountType.ADMIN)
      throw new BusinessException(ErrorCode.FORBIDDEN);
    return String.valueOf(identity.accountId());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
