package com.fastrag.security.aspect;

import com.fastrag.common.enums.KBRole;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import com.fastrag.security.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class KbAuthAspect {
    private final KbAccessChecker accessChecker;

    private static final Map<KBRole, Integer> ROLE_HIERARCHY = Map.of(
            KBRole.owner, 3, KBRole.editor, 2, KBRole.viewer, 1);

    public KbAuthAspect(KbAccessChecker accessChecker) {
        this.accessChecker = accessChecker;
    }

    @Around("@annotation(kbAuth)")
    public Object checkKbPermission(ProceedingJoinPoint joinPoint, KbAuth kbAuth) throws Throwable {
        LoginUser user = SecurityUtil.getCurrentUser();
        // 仅平台级 API Token（程序化访问）全局放行；所有登录用户（含超管/kb_admin）按组织+ACL 判定
        if (user.getUserId().startsWith("api-token:")) return joinPoint.proceed();

        String kbId = extractKbId();
        if (kbId == null) throw BusinessException.badRequest("缺少知识库ID");

        // ACL 优先，同组织兜底 viewer（实现见 KbAccessCheckerImpl）
        KBRole userRole = accessChecker.resolveRole(kbId, user);
        if (userRole == null) throw BusinessException.forbidden("无知识库访问权限");

        int required = ROLE_HIERARCHY.getOrDefault(kbAuth.value(), 0);
        int actual = ROLE_HIERARCHY.getOrDefault(userRole, 0);
        if (actual < required) throw BusinessException.forbidden("知识库权限不足");

        return joinPoint.proceed();
    }

    private String extractKbId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest req = attrs.getRequest();
        String uri = req.getRequestURI();
        String prefix = "/api/kb/";
        int idx = uri.indexOf(prefix);
        if (idx >= 0) {
            String rest = uri.substring(idx + prefix.length());
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(0, slash) : rest;
        }
        return null;
    }
}
