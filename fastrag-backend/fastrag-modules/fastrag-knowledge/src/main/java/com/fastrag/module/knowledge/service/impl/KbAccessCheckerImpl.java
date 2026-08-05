package com.fastrag.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.enums.KBRole;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import com.fastrag.security.service.KbAclService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 组织隔离模型下的知识库访问判定。
 * 有效角色 = max(ACL 显式角色, 同组织兜底角色)——ACL 只能提升、不能降级组织默认权限。
 * 同组织兜底：private（指定人共享）库无兜底（仅 ACL 授权者可见）；
 * 其他库管理员（超管/kb_admin）→ owner、普通成员 → editor。
 */
@Service
@RequiredArgsConstructor
public class KbAccessCheckerImpl implements KbAccessChecker {
    private final KnowledgeBaseMapper kbMapper;
    private final KbAclService aclService;

    @Override
    public KBRole resolveRole(String kbId, LoginUser user) {
        // ACL 显式角色（跨组织分享、指定人共享、组织内授权）
        KBRole aclRole = aclService.getKbRole(user.getUserId(), kbId);
        // 同组织兜底角色（private 库返回 null）
        KBRole orgRole = orgFallbackRole(kbId, user);
        return maxRole(aclRole, orgRole);
    }

    /** 同组织兜底角色：private 库无兜底；管理员 owner、普通成员 editor */
    private KBRole orgFallbackRole(String kbId, LoginUser user) {
        if (!StringUtils.hasText(user.getOrgId())) return null;
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null || !user.getOrgId().equals(kb.getOrgId())) return null;
        if ("private".equals(kb.getPermission())) return null;
        if (user.hasPermission("*") || user.getRoles().contains("kb_admin")) {
            return KBRole.owner;
        }
        return KBRole.editor;
    }

    /** 取两者中权限更高者（owner > editor > viewer；null 视为无） */
    private KBRole maxRole(KBRole a, KBRole b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    @Override
    public List<String> getAccessibleKbIds(String userId, String orgId) {
        // ACL 授权库 ∪ 本组织中非"指定人共享"（private）的库
        Set<String> ids = new HashSet<>(aclService.getAccessibleKbIds(userId));
        if (StringUtils.hasText(orgId)) {
            kbMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                            .eq(KnowledgeBase::getOrgId, orgId)
                            .ne(KnowledgeBase::getPermission, "private")
                            .select(KnowledgeBase::getId))
                    .forEach(kb -> ids.add(kb.getId()));
        }
        return new ArrayList<>(ids);
    }
}
