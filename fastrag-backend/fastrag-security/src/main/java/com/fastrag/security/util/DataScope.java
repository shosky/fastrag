package com.fastrag.security.util;

import com.fastrag.security.filter.LoginUser;

/**
 * 应用/工具/运营数据的所有权与组织隔离判定（与知识库组织模型对齐）。
 *
 * 可见性规则（数据对用户可见）：
 * - API Token（程序化访问）：全局
 * - 系统级数据（creator='system' 或 isBuiltin=1）：全员可见（存量/内置资源）
 * - 属主（creator = 我）
 * - 同组织（org_id = 我的组织）
 *
 * 管理权（数据可被修改/删除）：
 * - API Token：全局
 * - 属主（creator = 我）
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
        return creator != null && creator.equals(user.getUserId());
    }
}
