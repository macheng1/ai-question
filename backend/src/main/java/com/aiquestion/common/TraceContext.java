package com.aiquestion.common;

import org.slf4j.MDC;

/** 请求链路 ID 上下文，日志和接口响应共用同一个 traceId。 */
public final class TraceContext {
    public static final String KEY = "traceId";
    private TraceContext() {}
    public static String get() { return MDC.get(KEY); }
}
