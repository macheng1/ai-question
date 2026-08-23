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

  public AuthController(
      JdbcTemplate jdbc, PasswordEncoder encoder, JwtTokenService tokens, RedisService redis) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.tokens = tokens;
    this.redis = redis;
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

  public record AuthResult(Long userId, String name, String phone, TokenPair token) {}

  public record UserProfile(
      Long userId,
      String name,
      String phone,
      String unitName,
      String province,
      String city,
      String district,
      String address,
      String roleCode) {}

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
    var users =
        jdbc.query(
            "SELECT id,name,phone,password_hash FROM app_user WHERE phone=? AND status='ACTIVE'",
            (rs, row) ->
                new Object[] {
                  rs.getLong("id"),
                  rs.getString("name"),
                  rs.getString("phone"),
                  rs.getString("password_hash")
                },
            command.phone());
    if (users.isEmpty() || !encoder.matches(command.password(), (String) users.get(0)[3])) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    Object[] user = users.get(0);
    return ApiResponse.ok(
        new AuthResult(
            (Long) user[0],
            (String) user[1],
            (String) user[2],
            tokens.issue(String.valueOf(user[0]))));
  }

  @Operation(summary = "获取当前用户信息")
  @GetMapping("/me")
  public ApiResponse<UserProfile> me(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    final String userId;
    try {
      userId = tokens.subject(authorization.substring(7), false);
    } catch (RuntimeException exception) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }
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
}
