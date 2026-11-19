package com.aiquestion.ai;

import com.aiquestion.common.BusinessException;
import com.aiquestion.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChatServiceTest {
    @Test
    void sendsCurrentOwnersModelAndSecretAndHandlesProviderFailure() throws Exception {
        var db = mock(AiSettingsRepository.class);
        var cipher = new AiKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));
        var chat = new AiChatService(db, cipher);
        assertThrows(BusinessException.class, () -> chat.chat(5, "system", "input"));
        var key = cipher.encrypt(5, "sample-only");
        assertThrows(BusinessException.class, () -> cipher.decrypt(6, key));
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        var requestBody = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                boolean success = calls.incrementAndGet() == 1;
                var response = success ? """
                    {"id":"test","object":"chat.completion","model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}
                    """ : "untrusted upstream error";
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(success ? 200 : 401, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            when(db.connection(5)).thenReturn(new AiSettingsRepository.Connection(url, "test-model", key));
            var result = chat.testConnection(5);
            assertEquals("test-model", result.model());
            assertTrue(result.elapsedMs() >= 0);
            assertEquals("Bearer sample-only", auth.get());
            var body = new ObjectMapper().readTree(requestBody.get());
            assertEquals("test-model", body.path("model").asText());
            assertEquals("只回复 OK", body.at("/messages/1/content").asText());
            var failure = assertThrows(BusinessException.class, () -> chat.chat(5, "system", "input"));
            assertEquals(ErrorCode.AI_PROVIDER_ERROR, failure.getErrorCode());
            assertTrue(failure.getMessage().contains("401"), failure.getMessage());
            assertEquals(2, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
