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
  @Test void mediaFailureBeforeCommitReturnsJsonWithoutStaleRangeHeaders() throws Exception {
    var response = new org.springframework.mock.web.MockHttpServletResponse();
    response.setContentType("video/mp4");
    response.setHeader("Content-Length", "1000");
    response.setHeader("Content-Range", "bytes 0-999/1000");
    var result = new GlobalExceptionHandler().system(new java.io.IOException("read failed"), response);
    org.junit.jupiter.api.Assertions.assertEquals(500, result.getStatusCode().value());
    org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.MediaType.APPLICATION_JSON, result.getHeaders().getContentType());
    org.junit.jupiter.api.Assertions.assertNull(response.getHeader("Content-Length"));
    org.junit.jupiter.api.Assertions.assertNull(response.getHeader("Content-Range"));
  }

  @Test void committedVideoFailureDoesNotAppendJson() throws Exception {
    var response = new org.springframework.mock.web.MockHttpServletResponse();
    response.setContentType("video/mp4");
    response.getOutputStream().write(new byte[]{1, 2, 3});
    response.flushBuffer();
    org.junit.jupiter.api.Assertions.assertNull(new GlobalExceptionHandler().system(new java.io.IOException("connection closed"), response));
    org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[]{1, 2, 3}, response.getContentAsByteArray());
    org.junit.jupiter.api.Assertions.assertEquals("video/mp4", response.getContentType());
  }

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
