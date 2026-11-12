package com.aiquestion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class WebConfig implements WebMvcConfigurer {
  private final TraceIdInterceptor traceIdInterceptor;
  private final com.aiquestion.security.LearnerAccess learnerAccess;

  public WebConfig(TraceIdInterceptor traceIdInterceptor, com.aiquestion.security.LearnerAccess learnerAccess) {
    this.traceIdInterceptor = traceIdInterceptor;
    this.learnerAccess = learnerAccess;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns("*")
        .allowedMethods("*")
        .allowedHeaders("*")
        .exposedHeaders("Content-Disposition", "X-Trace-Id", "Accept-Ranges", "Content-Range")
        .maxAge(3600);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(traceIdInterceptor);
    registry.addInterceptor(learnerAccess).addPathPatterns("/public/exams/**", "/public/courses/**");
  }
}
