package com.aiquestion.security;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.aiquestion.controller.PublicExamController;
import com.aiquestion.controller.PublicLearningController;
import com.aiquestion.service.AccountService;
import com.aiquestion.service.LearnerIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearnerRequestAdviceTest {
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final LearnerIdentityService identities = mock(LearnerIdentityService.class);
  private final LearnerRequestAdvice advice = new LearnerRequestAdvice(request, identities);
  private final AccountService.Profile employee = new AccountService.Profile(7, "张明", "13900000001", null, "企业", "部门", AccountType.EMPLOYEE, 3);

  @Test void candidateBodyCannotSwitchLearner() {
    request.setAttribute(LearnerAccess.PROFILE, employee);
    var body = new PublicExamController.CandidateCommand("李明", "13900000002");
    doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(identities).requireCandidate(employee, "李明", "13900000002");
    assertThrows(BusinessException.class, () -> advice.afterBodyRead(body, null, null, null, null));
  }
  @Test void heartbeatRequiresOwnedLearningSession() {
    request.setAttribute(LearnerAccess.PROFILE, employee);
    var body = new PublicLearningController.Reading("other-token", "heartbeat", 5);
    doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(identities).requireLearning(employee, "other-token");
    assertThrows(BusinessException.class, () -> advice.afterBodyRead(body, null, null, null, null));
  }
  @Test void missingIdentityIsRejected() {
    var body = new PublicLearningController.ProgressCommand("token", 1, 10, false);
    assertThrows(BusinessException.class, () -> advice.afterBodyRead(body, null, null, null, null));
    verifyNoInteractions(identities);
  }
  @Test void validProgressIsPassedToController() {
    request.setAttribute(LearnerAccess.PROFILE, employee);
    var body = new PublicLearningController.ProgressCommand("token", 1, 10, false);
    assertSame(body, advice.afterBodyRead(body, null, null, null, null));
    verify(identities).requireLearning(employee, "token");
  }
}
