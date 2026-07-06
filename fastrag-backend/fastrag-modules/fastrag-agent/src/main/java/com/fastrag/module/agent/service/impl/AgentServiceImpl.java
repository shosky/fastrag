package com.fastrag.module.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.AgentCreateDTO;
import com.fastrag.module.agent.dto.AgentSerializeVO;
import com.fastrag.module.agent.dto.AgentUpdateDTO;
import com.fastrag.module.agent.entity.Agent;
import com.fastrag.module.agent.mapper.AgentMapper;
import com.fastrag.module.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;

    private static final Set<String> BUILTIN_SLUGS = Set.of(
            "default-chatbot", "general-purpose", "web-search",
            "deep-research", "research-explorer", "fact-verifier"
    );

    @Override
    public Agent ensureDefaultAgent() {
        Agent existing = agentMapper.selectOne(
                new LambdaQueryWrapper<Agent>().eq(Agent::getIsDefault, true).last("LIMIT 1")
        );
        if (existing != null) return existing;

        ensureBuiltinAgent("default-chatbot", "ChatbotAgent", "智能助手",
                "系统内置的默认对话机器人", "global", true, false, new HashMap<>());
        return agentMapper.selectOne(
                new LambdaQueryWrapper<Agent>().eq(Agent::getIsDefault, true).last("LIMIT 1")
        );
    }

    @Override
    public List<Agent> listVisible(User user, boolean includeSubagents) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        if (!includeSubagents) {
            wrapper.eq(Agent::getIsSubagent, false);
        }
        wrapper.orderByDesc(Agent::getIsDefault).orderByDesc(Agent::getCreatedAt);
        List<Agent> all = agentMapper.selectList(wrapper);
        return all.stream().filter(a -> userCanAccess(user, a)).toList();
    }

    @Override
    public Agent getVisibleBySlug(String slug, User user, boolean withBackend) {
        Agent agent = getBySlug(slug);
        if (agent == null) throw new BusinessException(404, "智能体不存在: " + slug);
        if (!userCanAccess(user, agent)) throw new BusinessException(403, "无权访问该智能体: " + slug);
        return agent;
    }

    @Override
    public Agent getBySlug(String slug) {
        return agentMapper.selectBySlug(slug);
    }

    @Override
    @Transactional
    public Agent create(AgentCreateDTO dto, User user) {
        String slug = dto.getSlug();
        if (StrUtil.isBlank(slug)) {
            slug = StrUtil.toCamelCase(dto.getName()).toLowerCase();
        }
        slug = ensureUniqueSlug(slug);

        Agent agent = new Agent();
        agent.setSlug(slug);
        agent.setBackendId(dto.getBackendId());
        agent.setName(dto.getName());
        agent.setDescription(dto.getDescription());
        agent.setIcon(dto.getIcon());
        agent.setConfigJson(dto.getConfigJson() != null ? dto.getConfigJson() : new HashMap<>());
        agent.setShareConfig(normalizeShareConfig(dto.getShareConfig(), user));
        agent.setIsDefault(false);
        agent.setIsSubagent(false);
        agent.setCreatedBy(user != null ? user.getUid() : "anonymous");
        agent.setUpdatedBy(user != null ? user.getUid() : "anonymous");
        agent.setCreatedAt(LocalDateTime.now());
        agent.setUpdatedAt(LocalDateTime.now());

        agentMapper.insert(agent);
        log.info("创建智能体: slug={}, name={}", slug, dto.getName());
        return agent;
    }

    @Override
    @Transactional
    public Agent update(Agent agent, AgentUpdateDTO dto, User user) {
        if (dto.getName() != null) agent.setName(dto.getName());
        if (dto.getDescription() != null) agent.setDescription(dto.getDescription());
        if (dto.getIcon() != null) agent.setIcon(dto.getIcon());
        if (dto.getConfigJson() != null) agent.setConfigJson(dto.getConfigJson());
        if (dto.getShareConfig() != null) {
            agent.setShareConfig(normalizeShareConfig(dto.getShareConfig(), user));
        }
        agent.setUpdatedBy(user != null ? user.getUid() : "anonymous");
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);
        return agent;
    }

    @Override
    @Transactional
    public void delete(Agent agent) {
        if (isBuiltin(agent)) throw new BusinessException(409, "内置智能体不允许删除");
        agentMapper.deleteById(agent.getId());
    }

    @Override
    public boolean userCanAccess(User user, Agent agent) {
        if (agent == null) return false;
        Map<String, Object> shareConfig = agent.getShareConfig();
        String accessLevel = "private";
        if (shareConfig != null && shareConfig.containsKey("access_level")) {
            accessLevel = (String) shareConfig.get("access_level");
        }
        if (user == null) return "global".equals(accessLevel);

        return switch (accessLevel) {
            case "global" -> true;
            case "department" -> matchDepartment(user, shareConfig);
            default -> matchCreator(user, agent);
        };
    }

    @Override
    public boolean userCanManage(User user, Agent agent) {
        if (agent == null || user == null) return false;
        String createdBy = agent.getCreatedBy();
        return StrUtil.equals(user.getUid(), createdBy);
    }

    @Override
    public boolean isBuiltin(Agent agent) {
        return agent != null && StrUtil.isNotBlank(agent.getSlug()) && BUILTIN_SLUGS.contains(agent.getSlug());
    }

    @Override
    @Transactional
    public Agent setDefault(Agent agent) {
        List<Agent> previousDefaults = agentMapper.selectList(
                new LambdaQueryWrapper<Agent>().eq(Agent::getIsDefault, true));
        for (Agent prev : previousDefaults) {
            prev.setIsDefault(false);
            prev.setUpdatedAt(LocalDateTime.now());
            agentMapper.updateById(prev);
        }
        agent.setIsDefault(true);
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);
        return agent;
    }

    @Override
    public AgentSerializeVO serialize(Agent agent, User user, boolean full) {
        AgentSerializeVO vo = new AgentSerializeVO();
        vo.setId(agent.getId());
        vo.setBackendId(agent.getBackendId());
        vo.setName(agent.getName());
        vo.setDescription(agent.getDescription());
        vo.setSlug(agent.getSlug());
        vo.setIcon(agent.getIcon());
        vo.setConfigJson(agent.getConfigJson());
        vo.setShareConfig(agent.getShareConfig());
        vo.setDefault(agent.getIsDefault() != null && agent.getIsDefault());
        vo.setSubagentFlag(agent.getIsSubagent() != null && agent.getIsSubagent());
        vo.setCreatedBy(agent.getCreatedBy());
        vo.setUpdatedBy(agent.getUpdatedBy());
        vo.setCreatedAt(agent.getCreatedAt());
        vo.setUpdatedAt(agent.getUpdatedAt());

        vo.setCanManage(userCanManage(user, agent));
        vo.setBuiltin(isBuiltin(agent));
        vo.setPermissionLocked(isBuiltin(agent));

        // Extract accessLevel from shareConfig
        Map<String, Object> sc = agent.getShareConfig();
        vo.setAccessLevel(sc != null ? (String) sc.getOrDefault("access_level", "private") : "private");

        // capabilities/metadata/configurableItems filled when full=true
        vo.setCapabilities(new ArrayList<>());
        vo.setMetadata(new HashMap<>());
        vo.setConfigurableItems(new HashMap<>());

        return vo;
    }

    @Override
    @Transactional
    public void ensureBuiltinAgent(String slug, String backendId, String name, String description,
                                   String accessLevel, boolean isDefault, boolean isSubagent,
                                   Map<String, Object> config) {
        Agent existing = agentMapper.selectBySlug(slug);
        if (existing != null) return;

        Agent agent = new Agent();
        agent.setSlug(slug);
        agent.setBackendId(backendId);
        agent.setName(name);
        agent.setDescription(description);
        agent.setIsDefault(isDefault);
        agent.setIsSubagent(isSubagent);
        agent.setConfigJson(config != null ? config : new HashMap<>());

        Map<String, Object> shareConfig = new HashMap<>();
        shareConfig.put("access_level", accessLevel);
        shareConfig.put("department_ids", List.of());
        shareConfig.put("user_uids", List.of());
        agent.setShareConfig(shareConfig);

        agent.setCreatedBy("system");
        agent.setUpdatedBy("system");
        agent.setCreatedAt(LocalDateTime.now());
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.insert(agent);
        log.info("创建内置智能体: slug={}, backendId={}", slug, backendId);
    }

    @Override
    public String ensureUniqueSlug(String baseSlug) {
        if (StrUtil.isBlank(baseSlug)) baseSlug = "agent-" + IdUtil.fastSimpleUUID().substring(0, 6);
        String slug = baseSlug;
        int counter = 1;
        while (agentMapper.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    @Override
    public Map<String, Object> normalizeShareConfig(Map<String, Object> shareConfig, User user) {
        Map<String, Object> result = shareConfig != null ? new HashMap<>(shareConfig) : new HashMap<>();
        String level = (String) result.getOrDefault("access_level", "private");

        // 非管理员强制私有
        if (user != null && !"admin".equals(user.getRole()) && !"superadmin".equals(user.getRole())) {
            result.put("access_level", "private");
            result.put("department_ids", List.of());
            result.put("user_uids", List.of());
            return result;
        }

        result.put("access_level", level);
        if (!result.containsKey("department_ids")) result.put("department_ids", List.of());
        if (!result.containsKey("user_uids")) result.put("user_uids", List.of());
        return result;
    }

    @Override
    public List<Agent> listVisibleSubagents(User user) {
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .eq(Agent::getIsSubagent, true)
                .orderByDesc(Agent::getCreatedAt);
        return agentMapper.selectList(wrapper).stream()
                .filter(a -> userCanAccess(user, a))
                .toList();
    }

    @Override
    public Agent getVisibleSubagentBySlug(String slug, User user) {
        Agent agent = getBySlug(slug);
        if (agent == null) throw new BusinessException(404, "子智能体不存在: " + slug);
        if (!Boolean.TRUE.equals(agent.getIsSubagent()))
            throw new BusinessException(400, "该智能体不是子智能体: " + slug);
        if (!userCanAccess(user, agent)) throw new BusinessException(403, "无权访问子智能体: " + slug);
        return agent;
    }

    // --- Helper methods ---

    private boolean matchCreator(User user, Agent agent) {
        if (user == null) return false;
        return StrUtil.equals(user.getUid(), agent.getCreatedBy());
    }

    private boolean matchDepartment(User user, Map<String, Object> shareConfig) {
        if (user == null || shareConfig == null) return false;
        Object deptIds = shareConfig.get("department_ids");
        if (deptIds instanceof List) {
            List<?> ids = (List<?>) deptIds;
            for (String userDept : user.getDepartmentIds()) {
                if (ids.stream().anyMatch(d -> String.valueOf(d).equals(userDept))) {
                    return true;
                }
            }
        }
        return false;
    }
}
