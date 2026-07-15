package com.fastrag.module.tools.skill;

import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.mapper.SkillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 技能访问权限管理器。
 * 
 * 三级权限模型（通过 share_config 控制）：
 * - global: 所有用户可访问
 * - department: 指定部门的用户可访问（需配合 UserService）
 * - user: 仅创建者和指定用户可访问
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillAccessManager {

    private final SkillMapper skillMapper;

    /**
     * 判断用户是否有权访问技能
     * @param userId 当前用户 ID
     * @param userRole 当前用户角色（admin/superadmin/user 等）
     * @param skill 目标技能
     * @return true 表示可访问
     */
    public boolean canAccess(String userId, String userRole, Skill skill) {
        // 超级管理员/管理员始终可访问
        if ("admin".equals(userRole) || "superadmin".equals(userRole)) {
            return true;
        }

        if (userId == null) {
            return false;
        }

        // 创建者始终可访问
        String creatorId = getCreatorId(skill);
        if (userId.equals(creatorId)) {
            return true;
        }

        // 内置技能全局可见
        if (Integer.valueOf(1).equals(skill.getIsBuiltin())) {
            return true;
        }

        // 检查 share_config
        Map<String, Object> sc = skill.getShareConfig();
        if (sc == null || sc.isEmpty()) {
            return false; // 无配置则仅创建者可访问
        }

        String accessLevel = (String) sc.getOrDefault("accessLevel", "user");
        return switch (accessLevel) {
            case "global" -> true;
            case "department" -> checkDepartmentAccess(userId, sc);
            case "user" -> checkUserAccess(userId, sc);
            default -> false;
        };
    }

    /**
     * 判断用户是否有权管理技能（编辑/删除）
     */
    public boolean canManage(String userId, String userRole, Skill skill) {
        if ("admin".equals(userRole) || "superadmin".equals(userRole)) {
            return true;
        }
        if (userId == null) return false;

        // 创建者可管理
        String creatorId = getCreatorId(skill);
        return userId.equals(creatorId);
    }

    /**
     * 从技能中获取创建者 ID
     * Skill 实体可能没有 createdBy 字段，通过 SkillMapper 查询
     */
    private String getCreatorId(Skill skill) {
        // 如果 Skill 实体有 createdBy 字段，直接返回
        // 否则从 mapper 查询
        try {
            String creatorId = skillMapper.selectCreatedBy(skill.getId());
            return creatorId != null ? creatorId : "";
        } catch (Exception e) {
            log.debug("Failed to get creatorId for skill {}: {}", skill.getId(), e.getMessage());
            return "";
        }
    }

    /**
     * 检查部门访问权限
     */
    private boolean checkDepartmentAccess(String userId, Map<String, Object> sc) {
        @SuppressWarnings("unchecked")
        List<String> deptIds = (List<String>) sc.getOrDefault("departmentIds", List.of());
        if (deptIds.isEmpty()) return false;

        // 实际项目中，这里应该调用 UserService.getUserDepartmentIds(userId)
        // 由于 fastrag-tools 模块可能没有 UserService 依赖，先记录日志
        log.debug("Department access check for user {} against departments {} - 需要集成 UserService", userId, deptIds);
        
        // 占位：如果用户 ID 在 deptIds 中（简化版），返回 true
        // 正式实现时需替换为真正的部门查询
        return false;
    }

    /**
     * 检查用户级访问权限
     */
    private boolean checkUserAccess(String userId, Map<String, Object> sc) {
        @SuppressWarnings("unchecked")
        List<String> uids = (List<String>) sc.getOrDefault("userUids", List.of());
        return uids.contains(userId);
    }

    /**
     * 标准化分享配置
     */
    public Map<String, Object> normalizeShareConfig(Map<String, Object> config, String userRole) {
        // 创建可变的副本
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (config != null) {
            result.putAll(config);
        }

        // 非管理员只能设置 user 级别
        if (!"admin".equals(userRole) && !"superadmin".equals(userRole)) {
            result.put("accessLevel", "user");
            result.put("departmentIds", List.of());
        }

        // 确保所有字段存在
        result.putIfAbsent("accessLevel", "user");
        result.putIfAbsent("departmentIds", List.of());
        result.putIfAbsent("userUids", List.of());

        return result;
    }
}
