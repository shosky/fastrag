package com.fastrag.common.response;

import lombok.Data;
import java.io.Serializable;

/**
 * 统一API响应体封装。
 *
 * <p>所有REST API接口的统一返回格式，包含code（HTTP状态码）、data（业务数据）
 * 和message（描述信息）。提供泛型支持，确保类型安全。</p>
 *
 * <p>提供成功和错误响应的快捷工厂方法：success()、error()、badRequest()、
 * unauthorized()、forbidden()、notFound()、serverError()。
 * 被所有Controller层方法统一使用，配合 {@code GlobalExceptionHandler} 实现异常到响应的自动转换。</p>
 *
 * @param <T> 响应数据的泛型类型
 */
@Data
public class ApiResponse<T> implements Serializable {
    private int code;
    private T data;
    private String message;
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(200); r.setData(data); r.setMessage("success");
        return r;
    }
    public static <T> ApiResponse<T> success() { return success(null); }
    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(code); r.setMessage(message);
        return r;
    }
    public static <T> ApiResponse<T> badRequest(String msg) { return error(400, msg); }
    public static <T> ApiResponse<T> unauthorized(String msg) { return error(401, msg != null ? msg : "未认证"); }
    public static <T> ApiResponse<T> forbidden(String msg) { return error(403, msg != null ? msg : "权限不足"); }
    public static <T> ApiResponse<T> notFound(String msg) { return error(404, msg != null ? msg : "资源不存在"); }
    public static <T> ApiResponse<T> serverError(String msg) { return error(500, msg != null ? msg : "服务异常"); }
}
