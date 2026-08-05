package com.fastrag.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP 地址工具类
 * <p>从 HttpServletRequest 中解析客户端真实 IP，支持代理转发。
 */
public class IpUtil {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR",
            "X-Real-IP"
    };

    /**
     * 从当前请求上下文中获取客户端 IP
     */
    public static String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return getClientIp(attrs.getRequest());
    }

    /**
     * 从 HttpServletRequest 中获取客户端 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个 IP，取第一个
                int commaIndex = ip.indexOf(',');
                return commaIndex > 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
