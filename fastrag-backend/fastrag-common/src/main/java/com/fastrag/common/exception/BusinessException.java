package com.fastrag.common.exception;

import lombok.Getter;

/**
 * 业务异常类。
 *
 * <p>系统中业务逻辑错误的统一异常，包含HTTP状态码和错误信息。
 * 由 {@code GlobalExceptionHandler} 统一捕获并转换为 {@code ApiResponse} 错误响应返回给前端。</p>
 *
 * <p>提供快捷工厂方法：badRequest(400)、unauthorized(401)、forbidden(403)、
 * notFound(404)、serverError(500)，用于快速构造对应HTTP状态码的业务异常。</p>
 */
@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String message) { super(message); this.code = code; }
    public static BusinessException badRequest(String msg) { return new BusinessException(400, msg); }
    public static BusinessException unauthorized(String msg) { return new BusinessException(401, msg); }
    public static BusinessException forbidden(String msg) { return new BusinessException(403, msg); }
    public static BusinessException notFound(String msg) { return new BusinessException(404, msg); }
    public static BusinessException serverError(String msg) { return new BusinessException(500, msg); }
}
