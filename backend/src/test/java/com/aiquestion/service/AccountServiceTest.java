package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.security.*;
import org.junit.jupiter.api.Test;
import com.aiquestion.repository.AccountRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {
  @Test void employeeCannotFallBackToAdminWithSameId() {
    var jdbc = mock(AccountRepository.class);
    var tokens = mock(JwtTokenService.class);
    when(tokens.subject("employee-token", false)).thenReturn("employee:7");
    when(jdbc.findActiveEmployeeProfile(7L)).thenReturn(List.of());
    var accounts = new AccountService(jdbc, tokens, mock(PasswordEncoder.class));
    assertThrows(BusinessException.class, () -> accounts.current("Bearer employee-token"));
    verify(jdbc, never()).countActiveAdministrator(anyLong());
  }
  @Test void adminCannotUseEmployeeEndpoints() {
    var jdbc = mock(AccountRepository.class);
    var tokens = mock(JwtTokenService.class);
    when(tokens.subject("admin-token", false)).thenReturn("7");
    when(jdbc.countActiveAdministrator(7L)).thenReturn(List.of(1));
    var accounts = new AccountService(jdbc, tokens, mock(PasswordEncoder.class));
    assertThrows(BusinessException.class, () -> accounts.requireEmployee("Bearer admin-token"));
  }
}
