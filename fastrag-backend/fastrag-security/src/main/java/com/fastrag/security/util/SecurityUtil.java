package com.fastrag.security.util;

/**
 * 安全上下文工具类，提供从 Spring Security 上下文中获取当前登录用户的便捷方法。
 *
 * <p>核心职责：封装 {@code SecurityContextHolder.getContext().getAuthentication()} 的调用，
 * 为业务代码提供类型安全的当前用户获取能力，避免各处重复编写认证上下文访问逻辑。
 *
 * <p>提供的工具方法：
 * <ul>
 *   <li>{@code getCurrentUser} — 获取当前登录用户（{@link LoginUser}），未登录时抛出 401 Unauthorized 异常</li>
 *   <li>{@code getCurrentUserId} — 获取当前登录用户的 userId，未登录时抛出 401 异常</li>
 *   <li>{@code hasPermission} — 判断当前用户是否拥有指定权限，未登录时静默返回 false（不抛异常）</li>
 * </ul>
 *
 * <p>使用场景：在 Controller 和 Service 层需要获取当前用户信息时统一调用此类方法，
 * 而非直接操作 SecurityContextHolder。
 *
 * <p>与其他模块的交互：被 Controller、Service、Aspect 等各层广泛调用，是获取当前用户的标准入口；
 * 依赖 {@link com.fastrag.common.exception.BusinessException} 返回统一的错误响应。
 */

import com.fastrag.common.exception.BusinessException;
import com.fastrag.security.filter.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {
    public static LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw BusinessException.unauthorized("未登录");
    }

    public static String getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    public static boolean hasPermission(String perm) {
        try {
            return getCurrentUser().hasPermission(perm);
        } catch (BusinessException e) {
            return false;
        }
    }
}
