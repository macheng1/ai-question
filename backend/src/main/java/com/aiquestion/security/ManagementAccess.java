package com.aiquestion.security;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Authenticates management requests and exposes their validated user identity. */
@Component
public class ManagementAccess implements HandlerInterceptor {
  private final JwtTokenService tokens;
  private final JdbcTemplate jdbc;
  public ManagementAccess(JwtTokenService tokens, JdbcTemplate jdbc) {
    this.tokens = tokens;
    this.jdbc = jdbc;
  }
  public static Long userId() {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes request
        && request.getRequest().getAttribute("managementUserId") instanceof Long id) return id;
    throw new BusinessException(ErrorCode.UNAUTHORIZED);
  }
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if ("OPTIONS".equals(request.getMethod())) return true;
    String header = request.getHeader("Authorization");
    Long id;
    try {
      if (header == null || !header.startsWith("Bearer ")) throw new IllegalArgumentException();
      id = Long.valueOf(tokens.subject(header.substring(7), false));
    } catch (RuntimeException exception) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND status='ACTIVE'", Integer.class, id) != 1)
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    request.setAttribute("managementUserId", id);
    return true;
  }
}
