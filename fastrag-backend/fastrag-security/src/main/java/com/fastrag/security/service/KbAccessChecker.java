package com.fastrag.security.service;

import com.fastrag.common.enums.KBRole;
import com.fastrag.security.filter.LoginUser;

import java.util.List;

/**
 * 知识库访问判定器（接口下沉到 security 模块，实现在 knowledge 模块）。
 *
 * 组织隔离模型：
 * - 同组织成员对组织内知识库默认可见（viewer 兜底）
 * - ACL 记录优先（跨组织分享、组织内提升角色）
 * - 超管 / API Token 在调用方（KbAuthAspect / service）放行，不经由此接口
 */
public interface KbAccessChecker {

    /**
     * 解析用户在某知识库上的有效角色。
     * 优先级：ACL 记录（Redis 读穿） > 同组织兜底 viewer > 无权限（返回 null）。
     */
    KBRole resolveRole(String kbId, LoginUser user);

    /**
     * 用户可访问的所有知识库 ID：本组织库 ∪ ACL 授权库。
     * orgId 为空（无组织用户）时仅返回 ACL 授权库。
     */
    List<String> getAccessibleKbIds(String userId, String orgId);
}
