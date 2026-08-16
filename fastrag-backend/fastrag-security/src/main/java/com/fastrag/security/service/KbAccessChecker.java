package com.fastrag.security.service;

import com.fastrag.common.enums.KBRole;
import com.fastrag.security.filter.LoginUser;

import java.util.List;

/**
 * 知识库访问判定器接口，定义知识库级别的访问控制判定契约。
 *
 * <p>核心职责：判定用户对特定知识库的访问权限，支持基于 ACL 和组织隔离的双重模型。
 * 接口定义在 security 模块（公共契约），实现在 knowledge 模块（依赖具体数据存储）。
 *
 * <p>组织隔离模型：
 * <ul>
 *   <li>ACL 记录优先：通过 Redis 读穿获取用户在特定知识库上的显式授权角色（跨组织分享、组织内角色提升）</li>
 *   <li>同组织兜底：同组织成员对组织内知识库默认可见（viewer 角色）</li>
 *   <li>超管 / API Token 在调用方（KbAuthAspect / service）放行，不经过此接口</li>
 * </ul>
 *
 * <p>提供的方法：
 * <ul>
 *   <li>{@code resolveRole} — 解析用户在某知识库上的有效角色，优先级：ACL 记录 > 同组织兜底 viewer > 无权限返回 null</li>
 *   <li>{@code getAccessibleKbIds} — 获取用户可访问的所有知识库 ID（本组织库 + ACL 授权库）</li>
 * </ul>
 *
 * <p>与其他模块的交互：被 {@link com.fastrag.security.aspect.KbAuthAspect} 调用以判定方法级权限；
 * 被知识库模块的 Service 层调用以过滤可见知识库列表。
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
