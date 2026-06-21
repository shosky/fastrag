package com.fastrag.common.response;
import lombok.Data;
import java.io.Serializable;
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
