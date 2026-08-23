package com.aiquestion.common;

/** 可预期的业务异常，禁止用通用 RuntimeException 代替业务错误码。 */
/**
 * 后端业务组件。
 *
 * @author aiquestion
 */
public class BusinessException extends RuntimeException {
  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
