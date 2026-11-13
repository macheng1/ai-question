package com.aiquestion.service;

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
import static com.aiquestion.controller.AuthController.*;
import com.aiquestion.repository.AuthRepository;

@org.springframework.stereotype.Service()
public class AuthService {

    private final AuthRepository persistence;

    private final PasswordEncoder encoder;

    private final JwtTokenService tokens;

    private final RedisService redis;

    private final com.aiquestion.service.AccountService accounts;

    public AuthService(AuthRepository persistence, PasswordEncoder encoder, JwtTokenService tokens, RedisService redis, com.aiquestion.service.AccountService accounts) {
        this.persistence = persistence;
        this.encoder = encoder;
        this.tokens = tokens;
        this.redis = redis;
        this.accounts = accounts;
    }

    public ApiResponse<Map<String, String>> captcha(String phone) {
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        redis.set("auth:captcha:" + phone, code, Duration.ofMinutes(1));
        return ApiResponse.ok(Map.of("message", "验证码已发送", "testCode", code));
    }

    public ApiResponse<AuthResult> register(AuthCommand command) {
        if (command.confirmPassword() == null || !command.password().equals(command.confirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }
        String cachedCaptcha = redis.get("auth:captcha:" + command.phone());
        if (cachedCaptcha != null)
            cachedCaptcha = cachedCaptcha.replace("\"", "");
        if (cachedCaptcha == null || !cachedCaptcha.equals(command.captcha())) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID);
        }
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countPhone(command.phone())) > 0)
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        String name = command.name() == null || command.name().isBlank() ? "用户" + command.phone().substring(7) : command.name();
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countName(name)) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        persistence.insertAdministrator(name, command.unitName(), command.province(), command.city(), command.district(), command.address(), command.phone(), encoder.encode(command.password()), "CANDIDATE", "ACTIVE", com.aiquestion.common.BusinessTime.now(), com.aiquestion.common.BusinessTime.now());
        redis.delete("auth:captcha:" + command.phone());
        Long id = com.aiquestion.repository.RepositoryRows.one(persistence.findAdministratorIdByPhone(command.phone()));
        return ApiResponse.ok(new AuthResult(id, name, command.phone(), tokens.issue(String.valueOf(id))));
    }

    public ApiResponse<AuthResult> login(AuthCommand command) {
        var user = accounts.loginAdministrator(command.phone(), command.password());
        return ApiResponse.ok(new AuthResult(user.userId(), user.name(), user.phone(), tokens.issue(new com.aiquestion.security.AccountIdentity(user.userId(), user.accountType(), user.enterpriseId())), user.accountType(), user.enterpriseId()));
    }

    public ApiResponse<?> me(String authorization) {
        var identity = accounts.current(authorization);
        if (identity.accountType() == com.aiquestion.security.AccountType.EMPLOYEE)
            return ApiResponse.ok(accounts.employee(identity.accountId()));
        String userId = String.valueOf(identity.accountId());
        var users = persistence.findActiveAdministratorProfile(userId).stream().map(rs -> new UserProfile(com.aiquestion.repository.RepositoryRows.longValue(rs, "id"), com.aiquestion.repository.RepositoryRows.string(rs, "name"), com.aiquestion.repository.RepositoryRows.string(rs, "phone"), com.aiquestion.repository.RepositoryRows.string(rs, "unit_name"), com.aiquestion.repository.RepositoryRows.string(rs, "province"), com.aiquestion.repository.RepositoryRows.string(rs, "city"), com.aiquestion.repository.RepositoryRows.string(rs, "district"), com.aiquestion.repository.RepositoryRows.string(rs, "address"), com.aiquestion.repository.RepositoryRows.string(rs, "role_code"))).toList();
        if (users.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(users.get(0));
    }

    public ApiResponse<?> updateProfile(String authorization, ProfileUpdateCommand command) {
        String userId = currentUserId(authorization);
        String name = command.name().trim();
        String unitName = command.unitName().trim();
        if (com.aiquestion.repository.RepositoryRows.one(persistence.countConflictingNames(name, userId)) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        persistence.updateAdministratorProfile(name, unitName, blankToNull(command.address()), com.aiquestion.common.BusinessTime.now(), userId);
        return me(authorization);
    }

    public ApiResponse<Void> changePassword(String authorization, PasswordChangeCommand command) {
        var identity = accounts.current(authorization);
        if (!command.newPassword().equals(command.confirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }
        if (identity.accountType() != com.aiquestion.security.AccountType.ADMIN)
            throw new BusinessException(ErrorCode.FORBIDDEN);
        String userId = String.valueOf(identity.accountId());
        String passwordHash = com.aiquestion.repository.RepositoryRows.one(persistence.findActivePasswordHash(userId));
        if (passwordHash == null || !encoder.matches(command.currentPassword(), passwordHash)) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        persistence.updateAdministratorPassword(encoder.encode(command.newPassword()), com.aiquestion.common.BusinessTime.now(), userId);
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
