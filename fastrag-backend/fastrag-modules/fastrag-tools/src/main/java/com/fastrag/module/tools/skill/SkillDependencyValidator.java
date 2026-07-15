package com.fastrag.module.tools.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.entity.SkillDependencyInfo;
import com.fastrag.module.tools.entity.Tool;
import com.fastrag.module.tools.mapper.McpServiceMapper;
import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.mapper.ToolMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 技能依赖验证器。
 * 验证三种依赖类型：
 * - tool: 必须存在于已注册的工具中
 * - mcp: 必须是已启用的 MCP 服务
 * - skill: 必须存在、不能自引用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillDependencyValidator {

    private final ToolMapper toolMapper;
    private final McpServiceMapper mcpServiceMapper;
    private final SkillMapper skillMapper;

    /**
     * 验证依赖列表
     */
    public ValidationResult validate(String skillId, List<SkillDependencyInfo> deps) {
        List<String> errors = new ArrayList<>();
        List<SkillDependencyInfo> valid = new ArrayList<>();

        if (deps == null || deps.isEmpty()) {
            return new ValidationResult(valid, errors);
        }

        Set<String> seen = new HashSet<>();
        for (SkillDependencyInfo dep : deps) {
            String key = dep.getType() + ":" + dep.getName();
            if (!seen.add(key)) {
                errors.add("重复依赖: " + key);
                continue;
            }
            try {
                validateSingle(dep, skillId);
                valid.add(dep);
            } catch (IllegalArgumentException e) {
                errors.add(dep.getType() + ":" + dep.getName() + " - " + e.getMessage());
            }
        }

        return new ValidationResult(valid, errors);
    }

    private void validateSingle(SkillDependencyInfo dep, String skillId) {
        if (dep.getName() == null || dep.getName().isBlank()) {
            throw new IllegalArgumentException("依赖名称不能为空");
        }

        switch (dep.getType()) {
            case "tool" -> {
                long count = toolMapper.selectCount(
                    new LambdaQueryWrapper<Tool>()
                        .eq(Tool::getName, dep.getName())
                        .eq(Tool::getEnabled, 1));
                if (count == 0) {
                    throw new IllegalArgumentException("工具不存在或未启用: " + dep.getName());
                }
            }
            case "mcp" -> {
                List<McpService> services = mcpServiceMapper.selectList(
                    new LambdaQueryWrapper<McpService>()
                        .eq(McpService::getName, dep.getName())
                        .eq(McpService::getEnabled, 1));
                if (services.isEmpty()) {
                    throw new IllegalArgumentException("MCP 服务不存在或未启用: " + dep.getName());
                }
            }
            case "skill" -> {
                if (skillId != null) {
                    Skill self = skillMapper.selectById(skillId);
                    if (self != null && dep.getName().equals(self.getName())) {
                        throw new IllegalArgumentException("不能依赖自身");
                    }
                }
                List<Skill> skills = skillMapper.selectList(
                    new LambdaQueryWrapper<Skill>()
                        .eq(Skill::getName, dep.getName())
                        .eq(Skill::getEnabled, 1));
                if (skills.isEmpty()) {
                    throw new IllegalArgumentException("依赖技能不存在或未启用: " + dep.getName());
                }
            }
            case "model" -> {
                // model 类型暂不验证详细，仅记录
                log.debug("Model dependency '{}' - validation deferred", dep.getName());
            }
            default -> throw new IllegalArgumentException("未知依赖类型: " + dep.getType());
        }
    }

    /**
     * 获取依赖选项（用于前端下拉框）
     */
    public DependencyOptions getDependencyOptions(String excludeSkillId) {
        DependencyOptions opts = new DependencyOptions();

        // 工具
        opts.setTools(toolMapper.selectList(
            new LambdaQueryWrapper<Tool>()
                .eq(Tool::getEnabled, 1))
            .stream().map(Tool::getName)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));

        // MCP 服务
        opts.setMcpServices(mcpServiceMapper.selectList(
            new LambdaQueryWrapper<McpService>()
                .eq(McpService::getEnabled, 1))
            .stream().map(McpService::getName)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));

        // 技能（排除自身）
        LambdaQueryWrapper<Skill> skillQuery = new LambdaQueryWrapper<Skill>()
            .eq(Skill::getEnabled, 1);
        if (excludeSkillId != null) {
            skillQuery.ne(Skill::getId, excludeSkillId);
        }
        opts.setSkills(skillMapper.selectList(skillQuery)
            .stream().map(Skill::getName)
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));

        return opts;
    }

    @Data
    @AllArgsConstructor
    public static class ValidationResult {
        private List<SkillDependencyInfo> validDeps;
        private List<String> errors;
    }

    @Data
    public static class DependencyOptions {
        private List<String> tools = List.of();
        private List<String> mcpServices = List.of();
        private List<String> skills = List.of();
    }
}
