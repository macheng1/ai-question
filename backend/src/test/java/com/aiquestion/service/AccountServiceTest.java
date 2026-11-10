package com.aiquestion.service;

import com.aiquestion.common.BusinessException;
import com.aiquestion.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {
  @Test void employeeCannotFallBackToAdminWithSameId() {
    var jdbc = mock(JdbcTemplate.class);
    var tokens = mock(JwtTokenService.class);
    when(tokens.subject("employee-token", false)).thenReturn("employee:7");
    when(jdbc.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of());
    var accounts = new AccountService(jdbc, tokens, mock(PasswordEncoder.class));
    assertThrows(BusinessException.class, () -> accounts.current("Bearer employee-token"));
    verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), anyLong());
  }
  @Test void adminCannotUseEmployeeEndpoints() {
    var jdbc = mock(JdbcTemplate.class);
    var tokens = mock(JwtTokenService.class);
    when(tokens.subject("admin-token", false)).thenReturn("7");
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L))).thenReturn(1);
    var accounts = new AccountService(jdbc, tokens, mock(PasswordEncoder.class));
    assertThrows(BusinessException.class, () -> accounts.requireEmployee("Bearer admin-token"));
  }
  @Test void wrongEmployeePasswordIsRejected() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    var encoder = mock(PasswordEncoder.class);
    when(jdbc.query(anyString(), any(RowMapper.class), eq("13900000001"))).thenAnswer(call -> {
      var rs = mock(java.sql.ResultSet.class);
      when(rs.getLong("id")).thenReturn(7L);
      when(rs.getString("password_hash")).thenReturn("encoded");
      RowMapper<?> mapper = call.getArgument(1);
      return List.of(mapper.mapRow(rs, 0));
    });
    var accounts = new AccountService(jdbc, mock(JwtTokenService.class), encoder);
    assertThrows(BusinessException.class, () -> accounts.loginEmployee("13900000001", "wrong"));
    verify(encoder).matches("wrong", "encoded");
  }
}
