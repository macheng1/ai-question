package com.aiquestion.common;

import org.springframework.http.HttpStatus;

/** 业务错误码统一维护处，新增业务错误时优先在这里登记。 */
public enum ErrorCode {
    SUCCESS("200", "操作成功", HttpStatus.OK),
    USER_ALREADY_EXISTS("100001", "手机号已注册", HttpStatus.CONFLICT),
    USERNAME_ALREADY_EXISTS("100005", "用户名已存在", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("100002", "手机号或密码错误", HttpStatus.UNAUTHORIZED),
    CAPTCHA_INVALID("100003", "验证码错误或已过期", HttpStatus.BAD_REQUEST),
    PASSWORD_NOT_MATCH("100004", "两次输入的密码不一致", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("400000", "请求参数错误", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR("400001", "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("401000", "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("403000", "没有权限执行此操作", HttpStatus.FORBIDDEN),
    INVALID_TOKEN("401001", "登录凭证无效", HttpStatus.UNAUTHORIZED),
    TOO_MANY_REQUESTS("429000", "请求过于频繁，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    NOT_FOUND("404000", "资源不存在", HttpStatus.NOT_FOUND),
    QUESTION_BANK_NOT_FOUND("BANK404", "题库不存在", HttpStatus.NOT_FOUND),
    UPLOAD_FILE_EMPTY("UPLOAD400", "请选择要上传的文件", HttpStatus.BAD_REQUEST),
    UPLOAD_FILE_COUNT_EXCEEDED("UPLOAD401", "上传文件数量超过限制", HttpStatus.BAD_REQUEST),
    UPLOAD_FILE_TOO_LARGE("UPLOAD402", "上传文件大小超过限制", HttpStatus.BAD_REQUEST),
    UPLOAD_FILE_TYPE_NOT_ALLOWED("UPLOAD403", "文件类型不被允许", HttpStatus.BAD_REQUEST),
    SYSTEM_ERROR("500000", "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);
    private final String code; private final String message; private final HttpStatus status;
    ErrorCode(String code,String message,HttpStatus status){this.code=code;this.message=message;this.status=status;}
    public String getCode(){return code;} public String getMessage(){return message;} public HttpStatus getStatus(){return status;}
}
