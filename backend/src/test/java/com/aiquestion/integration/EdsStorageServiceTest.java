package com.aiquestion.integration;

import com.aiquestion.config.EdsProperties;
import com.aiquestion.common.BusinessException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EdsStorageServiceTest {
  @Test void uploadFailureLogsStorageDetailsAndRedactsCredentials() {
    var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(EdsStorageService.class);
    var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    AmazonS3 client = mock(AmazonS3.class);
    var failure = new com.amazonaws.services.s3.model.AmazonS3Exception("Denied credential-id credential-secret");
    failure.setStatusCode(403);
    failure.setErrorCode("AccessDenied");
    failure.setRequestId("request-123");
    failure.initCause(new java.io.IOException("connection detail"));
    when(client.putObject(any(com.amazonaws.services.s3.model.PutObjectRequest.class))).thenThrow(failure);
    var storage = new EdsStorageService(new EdsProperties("http://localhost", "bucket", "credential-id", "credential-secret", "us-east-1")) {
      @Override protected AmazonS3 createClient() { return client; }
    };
    try {
      var error = assertThrows(BusinessException.class, () -> storage.put(
          new MockMultipartFile("file", new byte[]{1}), "application/pdf", "training-materials/test.pdf"));
      assertEquals("EDS 文件上传失败，请检查存储连接和权限", error.getMessage());
      String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
          .collect(java.util.stream.Collectors.joining("\n"));
      assertTrue(logs.contains("AccessDenied"));
      assertTrue(logs.contains("403"));
      assertTrue(logs.contains("request-123"));
      assertTrue(logs.contains("connection detail"));
      assertTrue(logs.contains("training-materials/test.pdf"));
      assertFalse(logs.contains("credential-id"));
      assertFalse(logs.contains("credential-secret"));
      verify(client).shutdown();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test void sdkUploadsReadsAndDeletesThroughPathStyleEndpoint() throws Exception {
    var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    byte[] data = {1, 2, 3};
    var calls = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var bodies = new java.util.concurrent.CopyOnWriteArrayList<byte[]>();
    server.createContext("/bucket/training-materials/test.pdf", exchange -> {
      calls.add(exchange.getRequestMethod());
      bodies.add(exchange.getRequestBody().readAllBytes());
      if ("GET".equals(exchange.getRequestMethod())) {
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
      } else {
        exchange.getResponseHeaders().set("ETag", "\"5289df737df57326fcdd22597afb1fac\"");
        exchange.sendResponseHeaders(200, -1);
      }
      exchange.close();
    });
    server.start();
    try {
      var storage = new EdsStorageService(new EdsProperties("http://127.0.0.1:" + server.getAddress().getPort(),
          "bucket", "test-key", "test-secret", "us-east-1"));
      storage.put(new MockMultipartFile("file", "test.pdf", "application/pdf", data),
          "application/pdf", "training-materials/test.pdf");
      var object = storage.open("training-materials/test.pdf");
      try (var input = object.stream()) {
        assertArrayEquals(data, input.readAllBytes());
        assertEquals(3, object.length());
        assertEquals("application/pdf", object.contentType());
      }
      storage.delete("training-materials/test.pdf");
      assertEquals(java.util.List.of("PUT", "GET", "DELETE"), calls);
      assertArrayEquals(data, bodies.get(0));
    } finally { server.stop(0); }
  }

  @Test void missingConfigFailsAndClientIsReleasedOnReadFailure() {
    assertThrows(BusinessException.class, () -> new EdsStorageService(
        new EdsProperties("", "", "", "", "us-east-1")).validateConfiguration());
    AmazonS3 client = mock(AmazonS3.class);
    var storage = new EdsStorageService(new EdsProperties("http://localhost", "bucket", "key", "secret", "us-east-1")) {
      @Override protected AmazonS3 createClient() { return client; }
    };
    when(client.getObject("bucket", "missing")).thenThrow(new RuntimeException());
    assertThrows(BusinessException.class, () -> storage.open("missing"));
    verify(client).shutdown();
  }
}
