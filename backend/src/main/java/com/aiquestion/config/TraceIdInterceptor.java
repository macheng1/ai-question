package com.aiquestion.config;

import cn.hutool.core.util.IdUtil;
import com.aiquestion.common.TraceContext;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 统一生成和回传 traceId，便于前后端联调与线上排障。 */
@Component
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class TraceIdInterceptor implements HandlerInterceptor {
  @Value("${app.trace-header:X-Trace-Id}")
  private String headerName;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String trace = request.getHeader(headerName);
    if (trace == null || trace.isBlank()) trace = IdUtil.fastSimpleUUID();
    MDC.put(TraceContext.KEY, trace);
    response.setHeader(headerName, trace);
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    MDC.remove(TraceContext.KEY);
  }
}
