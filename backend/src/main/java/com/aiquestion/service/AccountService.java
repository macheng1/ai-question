package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Resolves authenticated accounts; employee credentials never become management identities. */
@Service
public class AccountService {
  private final JdbcTemplate jdbc;
  private final JwtTokenService tokens;
  private final PasswordEncoder encoder;

  public AccountService(JdbcTemplate jdbc, JwtTokenService tokens, PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.tokens = tokens;
    this.encoder = encoder;
  }

  public record Profile(long userId, String name, String phone, String email, String unitName,
                        String departmentName, AccountType accountType, long enterpriseId) {}
  private record Credentials(long id, String hash) {}

  public Profile loginAdministrator(String phone, String password) {
    record Administrator(long id, String name, String phone, String unitName, String hash) {}
    var rows = jdbc.query("SELECT id,name,phone,unit_name,password_hash FROM app_user WHERE phone=? AND status='ACTIVE'",
        (rs, row) -> new Administrator(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
            rs.getString("unit_name"), rs.getString("password_hash")), phone);
    if (rows.size() != 1 || !encoder.matches(password, rows.get(0).hash()))
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    var user = rows.get(0);
    return new Profile(user.id(), user.name(), user.phone(), null, user.unitName(), null, AccountType.ADMIN, user.id());
  }

  public AccountIdentity requireAdministrator(String authorization) {
    var identity = current(authorization);
    if (identity.accountType() != AccountType.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
    return identity;
  }

  public Profile loginEmployee(String phone, String password) {
    var rows = jdbc.query("SELECT id,password_hash FROM organization_employee WHERE phone=? AND status='ACTIVE'",
        (rs, row) -> new Credentials(rs.getLong("id"), rs.getString("password_hash")), phone);
    if (rows.size() != 1 || !encoder.matches(password, rows.get(0).hash()))
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    return employee(rows.get(0).id());
  }

  public AccountIdentity current(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer "))
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    String subject;
    try { subject = tokens.subject(authorization.substring(7), false); }
    catch (RuntimeException error) { throw new BusinessException(ErrorCode.INVALID_TOKEN); }
    try {
      if (subject.startsWith("employee:")) {
        var profile = employee(Long.parseLong(subject.substring(9)));
        return new AccountIdentity(profile.userId(), AccountType.EMPLOYEE, profile.enterpriseId());
      }
      long id = Long.parseLong(subject);
      if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND status='ACTIVE'", Integer.class, id) != 1)
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
      return new AccountIdentity(id, AccountType.ADMIN, id);
    } catch (NumberFormatException error) { throw new BusinessException(ErrorCode.INVALID_TOKEN); }
  }

  public AccountIdentity requireEmployee(String authorization) {
    var identity = current(authorization);
    if (identity.accountType() != AccountType.EMPLOYEE) throw new BusinessException(ErrorCode.FORBIDDEN);
    return identity;
  }

  public Profile employee(long id) {
    var rows = jdbc.query("""
        SELECT e.id,e.name,e.phone,e.email,e.created_by,u.unit_name,d.name department_name
        FROM organization_employee e JOIN app_user u ON u.id=e.created_by AND u.status='ACTIVE'
        JOIN organization_department d ON d.id=e.department_id
        WHERE e.id=? AND e.status='ACTIVE'
        """, (rs, row) -> new Profile(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
        rs.getString("email"), rs.getString("unit_name"), rs.getString("department_name"),
        AccountType.EMPLOYEE, rs.getLong("created_by")), id);
    if (rows.size() != 1) throw new BusinessException(ErrorCode.UNAUTHORIZED);
    return rows.get(0);
  }

  public void changeEmployeePassword(AccountIdentity identity, String currentPassword, String newPassword) {
    if (identity.accountType() != AccountType.EMPLOYEE) throw new BusinessException(ErrorCode.FORBIDDEN);
    String hash = jdbc.queryForObject("SELECT password_hash FROM organization_employee WHERE id=? AND status='ACTIVE'", String.class, identity.accountId());
    if (hash == null || !encoder.matches(currentPassword, hash)) throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    jdbc.update("UPDATE organization_employee SET password_hash=? WHERE id=? AND status='ACTIVE'", encoder.encode(newPassword), identity.accountId());
  }
}
