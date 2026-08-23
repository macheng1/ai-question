package com.aiquestion.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 统一记录所有 HTTP 请求，包括被安全过滤器拦截的 401/403 请求。
 * 只记录请求元数据，不记录密码、Token、文件内容等敏感信息。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            failure = ex;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String traceId = request.getHeader("X-Trace-Id");
            log.info("request method={} uri={} status={} durationMs={} traceId={} ip={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    durationMs, traceId, request.getRemoteAddr());
            if (failure != null) {
                log.error("request failed traceId={}", traceId, failure);
            }
        }
    }
}
