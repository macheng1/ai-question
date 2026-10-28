package com.aiquestion.config;

import com.aiquestion.security.ManagementAccess;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ManagementWebConfig implements WebMvcConfigurer {
  private final ManagementAccess access;
  public ManagementWebConfig(ManagementAccess access) { this.access = access; }
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(access).addPathPatterns("/management/**", "/common/upload", "/common/upload/**");
  }
}
