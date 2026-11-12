package com.aiquestion.security;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.service.AccountService;
import com.aiquestion.service.LearnerIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;

/** 公开入口只展示考试说明；学习、答题和记录请求必须属于当前学员。 */
@Component
public class LearnerAccess implements HandlerInterceptor {
  public static final String PROFILE = "learnerProfile";
  private final AccountService accounts;
  private final LearnerIdentityService identities;

  public LearnerAccess(AccountService accounts, LearnerIdentityService identities) {
    this.accounts = accounts;
    this.identities = identities;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if ("OPTIONS".equals(request.getMethod())) return true;
    String pattern = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
    if ("GET".equals(request.getMethod()) && (pattern.endsWith("/trainings") || pattern.endsWith("/exam") || (pattern.equals("/public/exams/{id}") && request.getParameter("attemptId") == null))) return true;
    // 原生视频播放器不能设置 Authorization，内容读取沿用不可猜测的学习凭证。
    boolean media = "GET".equals(request.getMethod()) &&
        (pattern.endsWith("/videos/{video}/content") || pattern.endsWith("/materials/{material}/pages/{page}"));
    var employee = media
        ? identities.learningOwner(request.getParameter("token"))
        : accounts.employee(accounts.requireEmployee(request.getHeader("Authorization")).accountId());
    request.setAttribute(PROFILE, employee);
    Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    String attempt = request.getParameter("attemptId");
    if (variables instanceof Map<?, ?> map && map.get("attemptId") != null) attempt = map.get("attemptId").toString();
    if (attempt != null) {
      try { identities.requireAttempt(employee, Long.parseLong(attempt)); }
      catch (NumberFormatException e) { throw new BusinessException(ErrorCode.BAD_REQUEST); }
    }
    String phone = request.getParameter("phone");
    if (phone != null) identities.requireCandidate(employee, request.getParameter("name") == null ? employee.name() : request.getParameter("name"), phone);
    String token = request.getParameter("token");
    if (token != null) identities.requireLearning(employee, token);
    return true;
  }
}
