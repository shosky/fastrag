package com.fastrag.module.agent.context;

import com.fastrag.module.agent.backend.AgentBackend;
import com.fastrag.module.agent.backend.AgentBackendManager;
import com.fastrag.module.agent.entity.Agent;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.mapper.AgentMapper;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 运行时上下文构建器。
 * 从 Agent 配置 + 运行时数据构建完整的 BaseContext。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final AgentMapper agentMapper;
    private final AgentBackendManager backendManager;
    private final SkillService skillService;

    /**
     * 构建 Agent 运行时上下文
     */
    public BaseContext buildContext(AgentRun run) throws Exception {
        // 1. 加载 Agent 配置
        Agent agent = agentMapper.selectById(run.getAgentId());
        if (agent == null) {
            throw new RuntimeException("Agent not found: " + run.getAgentId());
        }

        // 2. 获取 AgentBackend
        AgentBackend backend = backendManager.getBackend(agent.getBackendId());
        if (backend == null) {
            throw new RuntimeException("Backend not found: " + agent.getBackendId());
        }

        // 3. 创建上下文实例
        BaseContext context = backend.getContextSchema().getDeclaredConstructor().newInstance();

        // 4. 从 agent configJson 加载配置
        Map<String, Object> config = agent.getConfigJson();
        if (config != null) {
            context.updateFromMap(config);
        }

        // 5. 设置运行时字段
        context.setRunId(run.getId());
        context.setUid(run.getUid());

        // 6. 执行运行时上下文准备
        prepareRuntimeContext(context);

        log.info("[ContextBuilder] Context built: runId={}, skills={}, tools={}, mcps={}",
            run.getId(), context.getSkills(), context.getTools(), context.getMcps());

        return context;
    }

    /**
     * 准备运行时上下文（技能填充）
     */
    private void prepareRuntimeContext(BaseContext context) {
        if (context.getSkills() == null || context.getSkills().isEmpty()) {
            return;
        }

        // 加载已启用的技能
        List<Skill> accessibleSkills = skillService.listAccessible(null, null, null);
        Map<String, Skill> skillMap = accessibleSkills.stream()
            .filter(s -> s.getSlug() != null)
            .collect(Collectors.toMap(Skill::getSlug, s -> s, (a, b) -> a));

        // 过滤有效技能
        List<String> validSlugs = context.getSkills().stream()
            .filter(slug -> skillMap.containsKey(slug))
            .toList();

        // 构建依赖映射
        Map<String, Object> metaMap = new LinkedHashMap<>();
        Map<String, Object> depMap = new LinkedHashMap<>();
        Set<String> closure = new LinkedHashSet<>(validSlugs);

        for (String slug : validSlugs) {
            Skill s = skillMap.get(slug);
            if (s != null) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", s.getName());
                info.put("description", s.getDescription());
                info.put("path", "/home/gem/skills/" + slug + "/SKILL.md");
                metaMap.put(slug, info);

                Map<String, Object> deps = new LinkedHashMap<>();
                deps.put("tools", s.getDependencies() != null ? s.getDependencies() : List.of());
                deps.put("mcps", List.of());
                depMap.put(slug, deps);
            }
        }

        context.setPromptSkills(new ArrayList<>(closure));
        context.setReadableSkills(new ArrayList<>(closure));
        context.setRuntimeSkillMetadata(metaMap);
        context.setRuntimeSkillDependencyMap(depMap);

        log.debug("[ContextBuilder] Prepared runtime skills: {}", closure);
    }
}
