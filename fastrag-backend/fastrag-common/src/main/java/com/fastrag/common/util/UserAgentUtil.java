package com.fastrag.common.util;

/**
 * User-Agent 解析工具类
 * <p>简单解析浏览器和操作系统信息，用于登录日志记录。
 */
public class UserAgentUtil {

    /**
     * 解析结果
     */
    public record UaInfo(String browser, String os, String device) {}

    /**
     * 从 User-Agent 字符串中解析浏览器、操作系统和设备信息
     */
    public static UaInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return new UaInfo("Unknown", "Unknown", userAgent);
        }
        String ua = userAgent.toLowerCase();
        String browser = parseBrowser(ua, userAgent);
        String os = parseOs(ua);
        return new UaInfo(browser, os, userAgent);
    }

    private static String parseBrowser(String ua, String raw) {
        if (ua.contains("edge") || ua.contains("edg/")) return "Edge";
        if (ua.contains("chrome") && !ua.contains("edg/")) return "Chrome";
        if (ua.contains("firefox")) return "Firefox";
        if (ua.contains("safari") && !ua.contains("chrome")) return "Safari";
        if (ua.contains("opera") || ua.contains("opr/")) return "Opera";
        if (ua.contains("trident") || ua.contains("msie")) return "IE";
        return "Other";
    }

    private static String parseOs(String ua) {
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "macOS";
        if (ua.contains("linux") && !ua.contains("android")) return "Linux";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return "iOS";
        return "Other";
    }
}
