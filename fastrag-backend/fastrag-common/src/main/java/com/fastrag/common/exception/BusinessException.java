package com.fastrag.common.exception;
import lombok.Getter;
@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String message) { super(message); this.code = code; }
    /** 通用工厂,支持自定义错误码(由各模块枚举提供),GlobalExceptionHandler 统一处理 */
    public static BusinessException of(int code, String message) { return new BusinessException(code, message); }
    public static BusinessException badRequest(String msg) { return new BusinessException(400, msg); }
    public static BusinessException unauthorized(String msg) { return new BusinessException(401, msg); }
    public static BusinessException forbidden(String msg) { return new BusinessException(403, msg); }
    public static BusinessException notFound(String msg) { return new BusinessException(404, msg); }
    public static BusinessException serverError(String msg) { return new BusinessException(500, msg); }
}
