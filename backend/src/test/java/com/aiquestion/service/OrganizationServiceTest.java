package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiquestion.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OrganizationServiceTest {
  @org.junit.jupiter.api.AfterEach
  void clearRequest() { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }

  private JdbcTemplate employeeDatabase() {
    var request = new org.springframework.mock.web.MockHttpServletRequest();
    request.setAttribute("managementUserId", 1L);
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
        new org.springframework.web.context.request.ServletRequestAttributes(request));
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM organization_department"), eq(Integer.class), any(), any())).thenReturn(1);
    when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM organization_employee WHERE phone"), eq(Integer.class), any(), any())).thenReturn(0);
    when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(2L);
    when(jdbc.queryForList(startsWith("SELECT e.id"), anyLong())).thenReturn(java.util.List.of(java.util.Map.of("id", 2L)));
    return jdbc;
  }

  @Test
  void newEmployeeStoresTrimmedEmailAndHashedDefaultPassword() {
    var jdbc = employeeDatabase();
    var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    new OrganizationService(jdbc, encoder).saveEmployee(null, "Alice", "13912345678", " alice@example.com ", null, 1L, "ACTIVE");
    var hash = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(startsWith("INSERT INTO"), eq(1L), eq(1L), eq("Alice"), eq("13912345678"), eq("alice@example.com"), hash.capture(), isNull(), eq("ACTIVE"));
    assertNotEquals("12345678", hash.getValue());
    assertTrue(encoder.matches("12345678", hash.getValue()));
  }

  @Test
  void editingEmployeeDoesNotResetPassword() {
    var jdbc = employeeDatabase();
    when(jdbc.queryForList(startsWith("SELECT department_id"), anyLong(), anyLong())).thenReturn(java.util.List.of(java.util.Map.of("department_id", 1L, "status", "ACTIVE")));
    when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM organization_employee WHERE id"), eq(Integer.class), any(), any())).thenReturn(1);
    var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
    new OrganizationService(jdbc, encoder).saveEmployee(2L, "Alice", "13912345678", "new@example.com", null, 1L, "ACTIVE");
    verify(jdbc).update(eq("UPDATE organization_employee SET department_id=?,name=?,phone=?,email=?,job_title=?,status=? WHERE id=? AND created_by=?"), eq(1L), eq("Alice"), eq("13912345678"), eq("new@example.com"), isNull(), eq("ACTIVE"), eq(2L), eq(1L));
    verifyNoInteractions(encoder);
  }

  @Test
  void invalidEmailRejectedBeforeDatabaseAccess() {
    var jdbc = mock(JdbcTemplate.class);
    var service = new OrganizationService(jdbc, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder());
    assertThrows(BusinessException.class, () -> service.saveEmployee(null, "Alice", "13912345678", "bad-email", null, 1L, "ACTIVE"));
    verifyNoInteractions(jdbc);
  }

  @Test
  void statusContractAcceptsActiveAndInactiveOnly() {
    assertDoesNotThrow(() -> OrganizationService.validateStatus("ACTIVE"));
    assertDoesNotThrow(() -> OrganizationService.validateStatus("INACTIVE"));
    for (String status : new String[] {null, "DISABLED", "active", ""})
      assertThrows(BusinessException.class, () -> OrganizationService.validateStatus(status));
  }

  @Test
  void invalidPhoneRejectedBeforeDatabaseAccess() {
    var jdbc = mock(JdbcTemplate.class);
    assertThrows(
        BusinessException.class,
        () -> new OrganizationService(jdbc, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()).saveEmployee(null, "Alice", "123", null, null, 1L, "ACTIVE"));
    verifyNoInteractions(jdbc);
  }

  @Test
  void blankDepartmentNameRejectedBeforeDatabaseAccess() {
    var jdbc = mock(JdbcTemplate.class);
    assertThrows(
        BusinessException.class,
        () -> new OrganizationService(jdbc, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()).saveDepartment(null, "  ", "ACTIVE"));
    verifyNoInteractions(jdbc);
  }
}
