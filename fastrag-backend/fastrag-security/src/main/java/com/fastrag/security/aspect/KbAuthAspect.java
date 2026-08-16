package com.fastrag.security.aspect;

/**
 * 知识库权限校验切面，拦截所有标注了 {@link com.fastrag.security.annotation.KbAuth} 注解的 Controller 方法，
 * 在方法执行前进行知识库级别的访问控制校验。
 *
 * <p>核心职责：实现知识库的 RBAC 权限模型，确保用户对特定知识库的操作权限满足注解声明的要求。
 *
 * <p>切点与增强逻辑：
 * <ul>
 *   <li>切点：{@code @Around("@annotation(kbAuth)")}，拦截所有标注 @KbAuth 注解的方法</li>
 *   <li>增强类型：环绕通知（Around Advice），在目标方法执行前完成权限校验，校验失败则抛出异常阻止执行</li>
 * </ul>
 *
 * <p>权限判定流程：
 * <ol>
 *   <li>从 Spring Security 上下文获取当前登录用户（通过 {@link com.fastrag.security.util.SecurityUtil}）</li>
 *   <li>平台级 API Token（userId 以 "api-token:" 开头）全局放行，不经过知识库 ACL 校验</li>
 *   <li>从请求 URI 中提取知识库 ID（解析 "/api/kb/{kbId}/..." 路径）</li>
 *   <li>调用 {@link com.fastrag.security.service.KbAccessChecker#resolveRole} 获取用户在该知识库上的有效角色，
 *       判定优先级：ACL 记录 > 同组织兜底 viewer > 无权限返回 null</li>
 *   <li>比较用户实际角色层级与注解要求的最低角色层级，不足则抛出 403 Forbidden</li>
 * </ol>
 *
 * <p>角色层级定义：owner(3) > editor(2) > viewer(1)。
 *
 * <p>与其他模块的交互：依赖 {@link com.fastrag.security.service.KbAccessChecker}（接口实现在 knowledge 模块）
 * 执行实际的权限判定；与 fastrag-common 模块的 {@link com.fastrag.common.exception.BusinessException} 配合
 * 返回统一的错误响应。
 */

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
