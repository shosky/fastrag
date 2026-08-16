package com.fastrag.security.util;

import com.fastrag.security.filter.LoginUser;

/**
 * 数据所有权与组织隔离判定工具类，提供应用/工具/运营数据的可见性和管理权判定。
 *
 * <p>核心职责：为非知识库类数据（如 Prompt 模板、应用配置、运营数据等）提供统一的访问控制判定，
 * 与知识库的组织隔离模型保持一致。
 *
 * <p>可见性规则（数据对用户可见）：
 * <ul>
 *   <li>API Token（程序化访问）：全局可见</li>
 *   <li>系统级数据（creator='system' 或 isBuiltin=1）：全员可见（存量回填或内置资源）</li>
 *   <li>属主（creator = 当前用户）：可见</li>
 *   <li>同组织（orgId = 当前用户所在组织）：可见</li>
 * </ul>
 *
 * <p>管理权规则（数据可被修改/删除）：
 * <ul>
 *   <li>API Token：全局可管理</li>
 *   <li>属主（creator = 当前用户）：可管理</li>
 * </ul>
 *
 * <p>提供的工具方法：
 * <ul>
 *   <li>{@code isApiToken} — 判断当前用户是否为 API Token 认证</li>
 *   <li>{@code isSystemData} — 判断是否为系统级内置数据</li>
 *   <li>{@code visible} — 综合判定数据是否对当前用户可见</li>
 *   <li>{@code manageable} — 判定数据是否可被当前用户管理（修改/删除）</li>
 * </ul>
 *
 * <p>使用场景：在 Service 层查询数据列表时，通过 DataScope.visible() 过滤出用户可见的数据；
 * 在更新/删除操作前，通过 DataScope.manageable() 校验操作权限。
 *
 * <p>与其他模块的交互：被各业务模块的 Service 层广泛调用，依赖 {@link LoginUser} 获取当前用户信息。
 */
public final class DataScope {

    private DataScope() {}

    public static boolean isApiToken(LoginUser user) {
        return user.getUserId().startsWith("api-token:");
    }

    /** 系统级数据（存量回填 / 内置资源）全员可见 */
    public static boolean isSystemData(String creator, Integer isBuiltin) {
        return "system".equals(creator) || (isBuiltin != null && isBuiltin == 1);
    }

    /** 数据是否对当前用户可见 */
    public static boolean visible(LoginUser user, String creator, String orgId, Integer isBuiltin) {
        if (isApiToken(user)) return true;
        if (isSystemData(creator, isBuiltin)) return true;
        if (creator != null && creator.equals(user.getUserId())) return true;
        return orgId != null && orgId.equals(user.getOrgId());
    }

    /** 数据是否可被当前用户管理（修改/删除） */
    public static boolean manageable(LoginUser user, String creator) {
        if (isApiToken(user)) return true;
        if (user.hasPermission("*")) return true;
        return creator != null && creator.equals(user.getUserId());
    }
}
