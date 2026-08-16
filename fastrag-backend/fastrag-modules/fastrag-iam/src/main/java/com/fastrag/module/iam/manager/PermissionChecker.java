package com.fastrag.module.iam.manager;

/**
 * 权限校验器，作为 Spring Security 表达式中的 Bean 供 SpEL 表达式调用。
 *
 * <p>注册为名为 {@code perm} 的 Spring Bean，可在 {@code @PreAuthorize} 注解中
 * 通过 SpEL 表达式引用，例如：{@code @PreAuthorize("@perm.has('admin:user')}")。
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@code has(String)} —— 判断当前用户是否拥有指定权限</li>
 *   <li>{@code hasAny(String...)} —— 判断当前用户是否拥有任意一个指定权限</li>
 * </ul>
 *
 * <p>底层实现委托给 {@code SecurityUtil} 工具类，从当前 SecurityContext 中
 * 获取用户信息并校验权限。此类的存在使得 Controller 层的权限注解更加简洁。
 *
 * @see SecurityUtil
 */
import com.fastrag.security.util.SecurityUtil; import org.springframework.stereotype.Component;
@Component("perm") public class PermissionChecker {
    public boolean has(String p) { return SecurityUtil.hasPermission(p); }
    public boolean hasAny(String... ps) { try { return SecurityUtil.getCurrentUser().hasAnyPermission(ps); } catch(Exception e){return false;} }
}
