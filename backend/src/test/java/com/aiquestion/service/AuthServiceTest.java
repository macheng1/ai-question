package com.aiquestion.service;

import com.aiquestion.common.ApiResponse;
import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.common.RedisService;
import com.aiquestion.controller.AuthController.UserProfile;
import com.aiquestion.repository.AuthRepository;
import com.aiquestion.security.AccountIdentity;
import com.aiquestion.security.AccountType;
import com.aiquestion.security.JwtTokenService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit coverage for the administrator-only phone change flow. */
class AuthServiceTest {

  private static final String AUTH = "Bearer admin-token";
  private static final String NEW_PHONE = "13900000000";

  private final AuthRepository persistence = mock(AuthRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final JwtTokenService tokens = mock(JwtTokenService.class);
  private final RedisService redis = mock(RedisService.class);
  private final AccountService accounts = mock(AccountService.class);
  private final AuthService service = new AuthService(persistence, encoder, tokens, redis, accounts);

  private static AccountIdentity admin() {
    return new AccountIdentity(1L, AccountType.ADMIN, 1L);
  }

  private static Map<String, Object> profileRow(String phone) {
    Map<String, Object> row = new HashMap<>();
    row.put("id", 1L);
    row.put("name", "管理员");
    row.put("phone", phone);
    row.put("unit_name", "测试企业");
    row.put("province", null);
    row.put("city", null);
    row.put("district", null);
    row.put("address", null);
    row.put("role_code", "CANDIDATE");
    return row;
  }

  private void allowSuccessfulLookup(String phone) {
    when(accounts.current(AUTH)).thenReturn(admin());
    when(persistence.findActiveAdministratorProfile("1")).thenReturn(List.of(profileRow(phone)));
  }

  @Test
  void administratorCanChangePhoneAndProfileReflectsNewNumber() {
    when(accounts.requireAdministrator(AUTH)).thenReturn(admin());
    when(persistence.countConflictingPhones(NEW_PHONE, "1")).thenReturn(List.of(0));
    when(persistence.updateAdministratorPhone(eq(NEW_PHONE), any(LocalDateTime.class), eq("1"))).thenReturn(1);
    allowSuccessfulLookup(NEW_PHONE);

    ApiResponse<?> response = service.changePhone(AUTH, NEW_PHONE);

    assertTrue(response.success());
    assertEquals(NEW_PHONE, ((UserProfile) response.data()).phone());
    verify(persistence).updateAdministratorPhone(eq(NEW_PHONE), any(LocalDateTime.class), eq("1"));
  }

  @Test
  void employeeTokenIsForbiddenAndNeverTouchesPersistence() {
    when(accounts.requireAdministrator(AUTH)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

    BusinessException error = assertThrows(BusinessException.class, () -> service.changePhone(AUTH, NEW_PHONE));

    assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    verifyNoInteractions(persistence);
  }

  @Test
  void occupiedPhoneIsRejectedWithoutUpdate() {
    when(accounts.requireAdministrator(AUTH)).thenReturn(admin());
    when(persistence.countConflictingPhones(NEW_PHONE, "1")).thenReturn(List.of(1));

    BusinessException error = assertThrows(BusinessException.class, () -> service.changePhone(AUTH, NEW_PHONE));

    assertEquals(ErrorCode.USER_ALREADY_EXISTS, error.getErrorCode());
    assertEquals("手机号已被其他账号使用", error.getMessage());
    verify(persistence, never()).updateAdministratorPhone(anyString(), any(LocalDateTime.class), anyString());
  }

  @Test
  void concurrentDuplicateKeyIsMappedToConflict() {
    when(accounts.requireAdministrator(AUTH)).thenReturn(admin());
    when(persistence.countConflictingPhones(NEW_PHONE, "1")).thenReturn(List.of(0));
    when(persistence.updateAdministratorPhone(eq(NEW_PHONE), any(LocalDateTime.class), eq("1")))
        .thenThrow(new DuplicateKeyException("duplicate phone"));

    BusinessException error = assertThrows(BusinessException.class, () -> service.changePhone(AUTH, NEW_PHONE));

    assertEquals(ErrorCode.USER_ALREADY_EXISTS, error.getErrorCode());
  }

  @Test
  void missingTokenIsRejected() {
    when(accounts.requireAdministrator(AUTH)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

    BusinessException error = assertThrows(BusinessException.class, () -> service.changePhone(AUTH, NEW_PHONE));

    assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
    verifyNoInteractions(persistence);
  }

  @Test
  void phoneIsTrimmedBeforeValidationAndUpdate() {
    when(accounts.requireAdministrator(AUTH)).thenReturn(admin());
    when(persistence.countConflictingPhones(NEW_PHONE, "1")).thenReturn(List.of(0));
    when(persistence.updateAdministratorPhone(eq(NEW_PHONE), any(LocalDateTime.class), eq("1"))).thenReturn(1);
    allowSuccessfulLookup(NEW_PHONE);

    ApiResponse<?> response = service.changePhone(AUTH, "  " + NEW_PHONE + "  ");

    assertEquals(NEW_PHONE, ((UserProfile) response.data()).phone());
    verify(persistence).countConflictingPhones(NEW_PHONE, "1");
  }
}
