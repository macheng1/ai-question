package com.aiquestion.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
    return ResponseEntity.status(ex.getErrorCode().getStatus())
        .body(ApiResponse.fail(ex.getErrorCode(), ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> invalid(MethodArgumentNotValidException ex) {
    return ApiResponse.fail(ErrorCode.VALIDATION_ERROR, "请求参数不完整或格式错误");
  }

  @ExceptionHandler(BindException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> bindInvalid(BindException ex) {
    return ApiResponse.fail(ErrorCode.VALIDATION_ERROR);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> system(Exception ex, HttpServletResponse response) {
    log.error("请求处理失败", ex);
    // A started media response cannot be replaced by a JSON error document.
    if (response.isCommitted()) return null;
    response.reset();
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> notFound(NoResourceFoundException ex) {
    return ApiResponse.fail(ErrorCode.NOT_FOUND, "请求的接口或资源不存在");
  }
}
