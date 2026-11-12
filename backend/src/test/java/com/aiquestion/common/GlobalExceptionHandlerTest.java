package com.aiquestion.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
  @Test void missingRouteReturnsNotFoundInsteadOfSystemBusy() throws Exception {
    var mvc = MockMvcBuilders.standaloneSetup(new MissingResourceController())
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    mvc.perform(post("/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("404000"))
        .andExpect(jsonPath("$.message").value("请求的接口或资源不存在"));
  }

  @RestController
  static class MissingResourceController {
    @PostMapping("/missing")
    void missing() throws NoResourceFoundException {
      throw new NoResourceFoundException(HttpMethod.POST, "missing");
    }
  }
}
