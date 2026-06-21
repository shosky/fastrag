package com.fastrag.security.util;

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
