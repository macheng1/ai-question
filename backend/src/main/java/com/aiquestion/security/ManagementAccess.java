package com.aiquestion.security;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** All management routes share the same live identity and account-status validation. */
@Component
public class ManagementAccess implements HandlerInterceptor {
  private final AccountService accounts;
  public ManagementAccess(AccountService accounts) { this.accounts = accounts; }

  public static Long userId() {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes request
        && request.getRequest().getAttribute("managementUserId") instanceof Long id) return id;
    throw new BusinessException(ErrorCode.UNAUTHORIZED);
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if ("OPTIONS".equals(request.getMethod())) return true;
    var identity = accounts.requireAdministrator(request.getHeader("Authorization"));
    request.setAttribute("managementUserId", identity.accountId());
    return true;
  }
}
