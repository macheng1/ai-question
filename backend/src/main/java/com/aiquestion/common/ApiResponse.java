package com.aiquestion.common;

/** 统一的接口响应结构，避免前端针对不同模块适配不同返回格式。 */
public record ApiResponse<T>(boolean success, String code, T data, String message, String traceId) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, ErrorCode.SUCCESS.getCode(), data, ErrorCode.SUCCESS.getMessage(), TraceContext.get()); }
    public static <T> ApiResponse<T> fail(ErrorCode code) { return fail(code, code.getMessage()); }
    public static <T> ApiResponse<T> fail(ErrorCode code, String message) { return new ApiResponse<>(false, code.getCode(), null, message, TraceContext.get()); }
    public static <T> ApiResponse<T> fail(String message) { return new ApiResponse<>(false, ErrorCode.SYSTEM_ERROR.getCode(), null, message, TraceContext.get()); }
}
