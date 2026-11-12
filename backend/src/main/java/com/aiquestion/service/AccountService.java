package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.security.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.aiquestion.repository.AccountRepository;

/**
 * Resolves authenticated accounts; employee credentials never become management identities.
 */
@Service
public class AccountService {

    private final AccountRepository persistence;

    private final JwtTokenService tokens;

    private final PasswordEncoder encoder;

    public AccountService(AccountRepository persistence, JwtTokenService tokens, PasswordEncoder encoder) {
        this.persistence = persistence;
        this.tokens = tokens;
        this.encoder = encoder;
    }

    public record Profile(long userId, String name, String phone, String email, String unitName, String departmentName, AccountType accountType, long enterpriseId) {
    }

    public Profile loginAdministrator(String phone, String password) {
        record Administrator(long id, String name, String phone, String unitName, String hash) {
        }
        var rows = persistence.findActiveAdministratorsByPhone(phone).stream().map(rs -> new Administrator(com.aiquestion.repository.RepositoryRows.longValue(rs, "id"), com.aiquestion.repository.RepositoryRows.string(rs, "name"), com.aiquestion.repository.RepositoryRows.string(rs, "phone"), com.aiquestion.repository.RepositoryRows.string(rs, "unit_name"), com.aiquestion.repository.RepositoryRows.string(rs, "password_hash"))).toList();
        if (rows.size() != 1 || !encoder.matches(password, rows.get(0).hash()))
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        var user = rows.get(0);
        return new Profile(user.id(), user.name(), user.phone(), null, user.unitName(), null, AccountType.ADMIN, user.id());
    }

    public AccountIdentity requireAdministrator(String authorization) {
        var identity = current(authorization);
        if (identity.accountType() != AccountType.ADMIN)
            throw new BusinessException(ErrorCode.FORBIDDEN);
        return identity;
    }

    public AccountIdentity current(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        String subject;
        try {
            subject = tokens.subject(authorization.substring(7), false);
        } catch (RuntimeException error) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        try {
            if (subject.startsWith("employee:")) {
                var profile = employee(Long.parseLong(subject.substring(9)));
                return new AccountIdentity(profile.userId(), AccountType.EMPLOYEE, profile.enterpriseId());
            }
            long id = Long.parseLong(subject);
            if (com.aiquestion.repository.RepositoryRows.one(persistence.countActiveAdministrator(id)) != 1)
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            return new AccountIdentity(id, AccountType.ADMIN, id);
        } catch (NumberFormatException error) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public AccountIdentity requireEmployee(String authorization) {
        var identity = current(authorization);
        if (identity.accountType() != AccountType.EMPLOYEE)
            throw new BusinessException(ErrorCode.FORBIDDEN);
        return identity;
    }

    public Profile employee(long id) {
        var rows = persistence.findActiveEmployeeProfile(id).stream().map(rs -> new Profile(com.aiquestion.repository.RepositoryRows.longValue(rs, "id"), com.aiquestion.repository.RepositoryRows.string(rs, "name"), com.aiquestion.repository.RepositoryRows.string(rs, "phone"), com.aiquestion.repository.RepositoryRows.string(rs, "email"), com.aiquestion.repository.RepositoryRows.string(rs, "unit_name"), com.aiquestion.repository.RepositoryRows.string(rs, "department_name"), AccountType.EMPLOYEE, com.aiquestion.repository.RepositoryRows.longValue(rs, "created_by"))).toList();
        if (rows.size() != 1)
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return rows.get(0);
    }
}
