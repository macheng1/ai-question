package com.aiquestion.security;

import com.aiquestion.controller.PublicExamController;
import com.aiquestion.controller.PublicLearningController;
import com.aiquestion.service.AccountService;
import com.aiquestion.service.LearnerIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.lang.reflect.Type;
import java.util.Map;

@ControllerAdvice(assignableTypes = {PublicExamController.class, PublicLearningController.class})
public class LearnerRequestAdvice extends RequestBodyAdviceAdapter {
  private final HttpServletRequest request;
  private final LearnerIdentityService identities;
  public LearnerRequestAdvice(HttpServletRequest request, LearnerIdentityService identities) {
    this.request = request;
    this.identities = identities;
  }
  @Override
  public boolean supports(MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) { return true; }
  @Override
  public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
    var employee = (AccountService.Profile) request.getAttribute(LearnerAccess.PROFILE);
    if (employee == null) throw new com.aiquestion.common.BusinessException(com.aiquestion.common.ErrorCode.UNAUTHORIZED);
    if (body instanceof PublicExamController.CandidateCommand candidate) identities.requireCandidate(employee, candidate.name(), candidate.phone());
    String token = null;
    if (body instanceof PublicLearningController.ProgressCommand progress) token = progress.token();
    else if (body instanceof PublicLearningController.Reading reading) token = reading.token();
    else if (body instanceof Map<?, ?> map) token = map.get("token") == null ? null : map.get("token").toString();
    if (body instanceof PublicLearningController.ProgressCommand || body instanceof PublicLearningController.Reading || body instanceof Map<?, ?>) identities.requireLearning(employee, token);
    return body;
  }
}
