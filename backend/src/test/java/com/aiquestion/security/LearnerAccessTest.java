package com.aiquestion.security;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.service.AccountService;
import com.aiquestion.service.LearnerIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearnerAccessTest {
  private final AccountService accounts = mock(AccountService.class);
  private final LearnerIdentityService identities = mock(LearnerIdentityService.class);
  private final LearnerAccess access = new LearnerAccess(accounts, identities);
  private final AccountService.Profile employee = new AccountService.Profile(7, "张明", "13900000001", null, "企业", "部门", AccountType.EMPLOYEE, 3);

  private MockHttpServletRequest request(String method, String pattern) {
    var request = new MockHttpServletRequest(method, pattern);
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
    return request;
  }
  private void authenticate() {
    when(accounts.requireEmployee("Bearer learner")).thenReturn(new AccountIdentity(7, AccountType.EMPLOYEE, 3));
    when(accounts.employee(7)).thenReturn(employee);
  }
  @Test void examIntroductionIsPublic() {
    assertTrue(access.preHandle(request("GET", "/public/exams/{id}"), new MockHttpServletResponse(), null));
    verifyNoInteractions(accounts, identities);
  }
  @Test void attemptCannotBeReadAnonymously() {
    var request = request("GET", "/public/exams/{id}");
    request.setParameter("attemptId", "21");
    when(accounts.requireEmployee(null)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
    assertThrows(BusinessException.class, () -> access.preHandle(request, new MockHttpServletResponse(), null));
  }
  @Test void attemptInRouteMustBelongToLearner() {
    authenticate();
    var request = request("POST", "/public/exams/{id}/attempts/{attemptId}/submit");
    request.addHeader("Authorization", "Bearer learner");
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "1", "attemptId", "21"));
    doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(identities).requireAttempt(employee, 21);
    assertThrows(BusinessException.class, () -> access.preHandle(request, new MockHttpServletResponse(), null));
  }
  @Test void videoUsesLearningCredentialWithoutAuthorizationHeader() {
    var request = request("GET", "/public/courses/{id}/learning/videos/{video}/content");
    request.setParameter("token", "learning-token");
    when(identities.learningOwner("learning-token")).thenReturn(employee);
    assertTrue(access.preHandle(request, new MockHttpServletResponse(), null));
    verify(identities).requireLearning(employee, "learning-token");
    verifyNoInteractions(accounts);
  }
  @Test void progressStillRequiresLearnerIdentity() {
    var request = request("PUT", "/public/courses/{id}/learning/videos/{video}/progress");
    when(accounts.requireEmployee(null)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));
    assertThrows(BusinessException.class, () -> access.preHandle(request, new MockHttpServletResponse(), null));
  }
  @Test void recordsCannotUseAnotherPhone() {
    authenticate();
    var request = request("GET", "/public/exams/{id}/records");
    request.addHeader("Authorization", "Bearer learner");
    request.setParameter("phone", "13900000002");
    doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(identities).requireCandidate(employee, "张明", "13900000002");
    assertThrows(BusinessException.class, () -> access.preHandle(request, new MockHttpServletResponse(), null));
  }
}
