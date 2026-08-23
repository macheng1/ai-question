package com.aiquestion.config;

import org.springframework.beans.factory.annotation.Value;
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

  public WebConfig(TraceIdInterceptor traceIdInterceptor) {
    this.traceIdInterceptor = traceIdInterceptor;
  }

  @Value("${app.cors-origins}")
  private String origins;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(origins.split(","))
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*");
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(traceIdInterceptor);
  }
}
