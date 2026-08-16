package com.fastrag.security.filter;

/**
 * 登录用户信息载体类，封装当前认证用户的身份和权限信息。
 *
 * <p>核心职责：作为 Spring Security 认证上下文中的 principal 对象，
 * 承载从 JWT Token 或 API Token 解析出的用户身份信息，供业务代码通过
 * {@link com.fastrag.security.util.SecurityUtil} 获取当前用户。
 *
 * <p>包含的字段：
 * <ul>
 *   <li>{@code userId} — 用户唯一标识（API Token 认证时为 "api-token:{tokenId}" 格式）</li>
 *   <li>{@code username} — 用户名</li>
 *   <li>{@code orgId} — 所属组织 ID（API Token 认证时为空）</li>
 *   <li>{@code roles} — 角色列表（如 ["admin", "user"]）</li>
 *   <li>{@code permissions} — 权限列表（如 ["kb:manage", "kb:read"]，通配符 "*" 表示全部权限）</li>
 * </ul>
 *
 * <p>提供的工具方法：
 * <ul>
 *   <li>{@code hasPermission} — 判断是否拥有指定权限（支持通配符 "*" 匹配）</li>
 *   <li>{@code hasAnyPermission} — 判断是否拥有任意一个指定权限</li>
 * </ul>
 *
 * <p>与其他模块的交互：由 {@link JwtAuthFilter} 和 {@link ApiTokenAuthFilter} 在认证成功后创建，
 * 被 {@link com.fastrag.security.util.SecurityUtil}、{@link com.fastrag.security.aspect.KbAuthAspect}
 * 和 {@link com.fastrag.security.util.DataScope} 等读取使用。
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {
    private String userId;
    private String username;
    private String orgId;
    private List<String> roles;
    private List<String> permissions;

    public boolean hasPermission(String perm) {
        return permissions != null && (permissions.contains("*") || permissions.contains(perm));
    }

    public boolean hasAnyPermission(String... perms) {
        if (permissions == null) return false;
        if (permissions.contains("*")) return true;
        for (String p : perms) {
            if (permissions.contains(p)) return true;
        }
        return false;
    }
}
