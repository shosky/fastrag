package com.fastrag.module.application.config;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.application.entity.App;
import com.fastrag.module.application.mapper.AppMapper;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.DataScope;
import com.fastrag.security.util.SecurityUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应用数据权限拦截器：统一校验 /api/apps/{appId}/... 的可见性与管理权。
 *
 * 规则：
 * - GET           → 可见（属主 / 同组织 / 系统级 / API Token）
 * - PUT / DELETE  → 管理权（属主 / API Token）
 * - POST          → /chat/ 路径（聊天/反馈=使用）→ 可见；其余（配置/绑定/发布）→ 管理权
 * - POST /api/apps（创建）与 GET /api/apps（列表）→ 放行（service 层处理）
 */
@Component
@RequiredArgsConstructor
public class AppDataScopeInterceptor implements HandlerInterceptor {

    private static final Pattern APP_ID_PATTERN = Pattern.compile("^/api/apps/([^/]+)(/.*)?$");

    private final AppMapper appMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE/异步请求完成或超时后的 ASYNC 二次分发不再鉴权（此时 SecurityContext 为空，原请求已校验过）
        if (request.getDispatcherType() == DispatcherType.ASYNC) return true;
        if (!(handler instanceof HandlerMethod)) return true;

        String path = request.getRequestURI();
        Matcher m = APP_ID_PATTERN.matcher(path);
        if (!m.matches()) return true; // 无 appId（列表/创建/模板），由 service 处理

        String appId = m.group(1);
        if ("templates".equals(appId)) return true; // 应用模板为系统数据

        App app = appMapper.selectById(appId);
        LoginUser user = SecurityUtil.getCurrentUser();
        String method = request.getMethod();

        boolean isUsePost = method.equals("POST") && path.contains("/chat/");
        if (method.equals("GET") || isUsePost) {
            if (app == null) throw BusinessException.notFound("应用不存在");
            if (!DataScope.visible(user, app.getOwner(), app.getOrgId(), null)) {
                throw BusinessException.forbidden("无权访问该应用");
            }
        } else {
            // PUT / DELETE / 管理类 POST
            if (app == null) throw BusinessException.notFound("应用不存在");
            if (!DataScope.manageable(user, app.getOwner())) {
                throw BusinessException.forbidden("无权管理该应用");
            }
        }
        return true;
    }
}
