package com.aiquestion.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证真实 MVC 与安全过滤器组合下的跨域行为，无需连接数据库。 */
@SpringJUnitWebConfig(CorsConfigurationTest.TestConfig.class)
class CorsConfigurationTest {
  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  @Import({WebConfig.class, SecurityConfig.class})
  static class TestConfig {
    @Bean com.aiquestion.security.LearnerAccess learnerAccess() { return mock(com.aiquestion.security.LearnerAccess.class); }
    @Bean TraceIdInterceptor traceIdInterceptor() { return mock(TraceIdInterceptor.class); }
    @Bean TestEndpoints testEndpoints() { return new TestEndpoints(); }
  }

  @RestController
  static class TestEndpoints {
    @PostMapping({"/auth/login", "/management/trainings/1/videos"})
    String post() { return "ok"; }
    @GetMapping("/download")
    ResponseEntity<String> download() {
      return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=report.csv").body("data");
    }
    @GetMapping("/private")
    ResponseEntity<Void> unauthorized() { return ResponseEntity.status(401).build(); }
  }

  @Autowired WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach void setup() {
    org.mockito.Mockito.when(context.getBean(TraceIdInterceptor.class)
        .preHandle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(true);
    mvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
  }

  @Test void loginPreflightAllowsHttpsOrigin() throws Exception {
    mvc.perform(options("/auth/login").header("Origin", "https://shopai.org.cn")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "content-type,x-client"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://shopai.org.cn"))
        .andExpect(header().string("Access-Control-Max-Age", "3600"));
  }

  @Test void uploadPreflightAllowsAuthorizationAndRangeHeaders() throws Exception {
    mvc.perform(options("/management/trainings/1/videos").header("Origin", "http://localhost:13000")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "authorization,content-type,x-client,range"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:13000"));
  }

  @Test void arbitraryOriginsAndPortsAreAllowedWithoutConfiguration() throws Exception {
    for (String origin : new String[] {"https://new-frontend.example", "http://192.168.1.50:13000", "http://127.0.0.1:3001"}) {
      mvc.perform(options("/management/trainings/1/videos").header("Origin", origin)
          .header("Access-Control-Request-Method", "PATCH")
          .header("Access-Control-Request-Headers", "authorization,x-client,content-type"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }
  }

  @Test void actualLoginRequestFromNewOriginIsAllowed() throws Exception {
    mvc.perform(post("/auth/login").header("Origin", "https://new-frontend.example"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://new-frontend.example"));
  }

  @Test void authenticationErrorRemainsReadableByAllowedFrontend() throws Exception {
    mvc.perform(get("/private").header("Origin", "https://shopai.org.cn"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://shopai.org.cn"));
  }

  @Test void downloadsExposeFilenameAndTraceHeaders() throws Exception {
    mvc.perform(get("/download").header("Origin", "http://localhost:13000"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Expose-Headers",
            "Content-Disposition, X-Trace-Id, Accept-Ranges, Content-Range"));
  }
}
