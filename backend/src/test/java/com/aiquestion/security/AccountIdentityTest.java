package com.aiquestion.security;

import com.aiquestion.config.JwtProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountIdentityTest {
  @Test void equalIdsHaveDifferentSignedSubjects() {
    var tokens = new JwtTokenService(new JwtProperties("test-secret-012345678901234567890123456789", "1h", "refresh-secret-012345678901234567890123456789", "1h"));
    var admin = tokens.issue(new AccountIdentity(7, AccountType.ADMIN, 7));
    var employee = tokens.issue(new AccountIdentity(7, AccountType.EMPLOYEE, 3));
    assertEquals("7", tokens.subject(admin.accessToken(), false));
    assertEquals("employee:7", tokens.subject(employee.accessToken(), false));
    assertThrows(RuntimeException.class, () -> tokens.subject(employee.refreshToken(), false));
  }
}
