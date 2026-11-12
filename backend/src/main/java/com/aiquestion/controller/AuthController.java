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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户认证", description = "用户注册、登录和令牌接口")
@RestController
@RequestMapping("/auth")
public class /**
 * 后端业务组件。
 *
 * @author aiquestion
 */
AuthController {

    public record AuthCommand(@NotBlank @Pattern(regexp = "^1\\d{10}$") String phone, @NotBlank @Size(min = 6, max = 64) String password, String confirmPassword, String captcha, String name, String unitName, String province, String city, String district, String address) {
    }

    public record AuthResult(Long userId, String name, String phone, TokenPair token, com.aiquestion.security.AccountType accountType, long enterpriseId) {

        public AuthResult(Long id, String name, String phone, TokenPair token) {
            this(id, name, phone, token, com.aiquestion.security.AccountType.ADMIN, id);
        }
    }

    public record UserProfile(Long userId, String name, String phone, String unitName, String province, String city, String district, String address, String roleCode) {

        public com.aiquestion.security.AccountType getAccountType() {
            return com.aiquestion.security.AccountType.ADMIN;
        }

        public Long getEnterpriseId() {
            return userId;
        }
    }

    public record ProfileUpdateCommand(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 160) String unitName, @Size(max = 255) String address) {
    }

    public record PasswordChangeCommand(@NotBlank String currentPassword, @NotBlank @Size(min = 6, max = 64) String newPassword, @NotBlank String confirmPassword) {
    }

    @Operation(summary = "发送注册验证码")
    @PostMapping("/captcha")
    public ApiResponse<Map<String, String>> captcha(@RequestParam @Pattern(regexp = "^1\\d{10}$") String phone) {
        return service.captcha(phone);
    }

    @Operation(summary = "注册账号")
    @PostMapping("/register")
    public ApiResponse<AuthResult> register(@Valid @RequestBody AuthCommand command) {
        return service.register(command);
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResponse<AuthResult> login(@Valid @RequestBody AuthCommand command) {
        return service.login(command);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResponse<?> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.me(authorization);
    }

    @Operation(summary = "修改当前用户信息")
    @PutMapping("/me/profile")
    public ApiResponse<?> updateProfile(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody ProfileUpdateCommand command) {
        return service.updateProfile(authorization, command);
    }

    @Operation(summary = "修改当前用户密码")
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody PasswordChangeCommand command) {
        return service.changePassword(authorization, command);
    }

    private final com.aiquestion.service.AuthService service;

    public /**
     * 后端业务组件。
     *
     * @author aiquestion
     */
    AuthController(com.aiquestion.service.AuthService service) {
        this.service = service;
    }
}
