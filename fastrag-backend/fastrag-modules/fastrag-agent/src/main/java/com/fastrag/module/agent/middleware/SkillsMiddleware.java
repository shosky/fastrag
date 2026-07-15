package com.fastrag.module.agent.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.service.SkillService;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 技能运行时中间件（增强版）。
 * 
 * 三段式渐进加载：
 * Phase 1 — beforeModelCall: 填充运行时技能上下文，构建 skills prompt 注入 system message
 * Phase 2 — interceptToolCall: 拦截 read_file 读取 SKILL.md，激活技能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillsMiddleware implements AgentMiddleware {

    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    private static final Pattern SKILL_READ_PATH_PATTERN = 
        Pattern.compile("/home/gem/skills/([^/]+)/SKILL\\.md");

    @Override
    public int getOrder() { return 4; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        if (context.getSkills() == null || context.getSkills().isEmpty()) {
            return context;
        }

        // Phase 1: 填充运行时技能上下文
        fillRuntimeSkillContext(context);

        // Phase 2: 构建 skills prompt 注入
        String skillsPrompt = buildSkillsPrompt(context);
        if (!skillsPrompt.isEmpty()) {
            injectSystemPrompt(messages, skillsPrompt);
        }

        return context;
    }

    @Override
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                 ChatMessage.ToolCall toolCall) {
        String funcName = toolCall.getFunction().getName();
        if (!"read_file".equals(funcName) && !"read".equals(funcName)) {
            return null;
        }

        String filePath = extractFilePath(toolCall);
        if (filePath == null) return null;

        Matcher m = SKILL_READ_PATH_PATTERN.matcher(filePath);
        if (!m.find()) return null;

        String slug = m.group(1);
        if (context.getReadableSkills() != null &&
            context.getReadableSkills().contains(slug)) {
            log.info("[SkillsMiddleware] Skill activated via intercept: {}", slug);
            loadActivatedSkillDependencies(context, slug);

            // 返回技能内容作为工具结果
            String skillContent = getSkillContent(context, slug);
            ChatMessage result = new ChatMessage("tool", skillContent, toolCall.getId());
            return ToolCallInterceptor.skip(result);
        }

        return null;
    }

    /**
     * Phase 1: 填充运行时技能上下文
     */
    private void fillRuntimeSkillContext(BaseContext context) {
        List<String> selectedSlugs = context.getSkills();
        if (selectedSlugs == null || selectedSlugs.isEmpty()) return;

        // 从 DB 加载所有已启用的技能
        List<Skill> allSkills = skillService.listAccessible(null, null, null);
        Map<String, Skill> skillMap = allSkills.stream()
            .filter(s -> s.getSlug() != null)
            .collect(Collectors.toMap(Skill::getSlug, s -> s, (a, b) -> a));

        // 过滤出用户选中且在 DB 中存在的技能
        List<String> validSlugs = selectedSlugs.stream()
            .filter(slug -> skillMap.containsKey(slug))
            .toList();

        // 计算传递闭包（递归解析 skill_dependencies）
        Set<String> closure = expandSkillClosure(validSlugs, skillMap);

        // 构建 prompt 元数据
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> depMap = new LinkedHashMap<>();
        for (String slug : closure) {
            Skill s = skillMap.get(slug);
            if (s != null) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", s.getName());
                info.put("description", s.getDescription());
                info.put("path", "/home/gem/skills/" + slug + "/SKILL.md");
                metadata.put(slug, info);

                Map<String, Object> deps = new LinkedHashMap<>();
                deps.put("tools", s.getDependencies() != null ? s.getDependencies() : List.of());
                deps.put("mcps", List.of());
                depMap.put(slug, deps);
            }
        }

        context.setPromptSkills(new ArrayList<>(closure));
        context.setReadableSkills(new ArrayList<>(closure));
        context.setRuntimeSkillMetadata(metadata);
        context.setRuntimeSkillDependencyMap(depMap);
    }

    /**
     * 递归展开技能依赖传递闭包
     */
    private Set<String> expandSkillClosure(List<String> slugs, Map<String, Skill> skillMap) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> stack = new ArrayDeque<>(slugs);
        while (!stack.isEmpty()) {
            String slug = stack.poll();
            if (!visited.add(slug)) continue;
            Skill s = skillMap.get(slug);
            if (s != null && s.getDependencies() != null) {
                for (String dep : s.getDependencies()) {
                    if (dep != null && dep.startsWith("skill:")) {
                        String depSlug = dep.substring(6);
                        if (!visited.contains(depSlug) && skillMap.containsKey(depSlug)) {
                            stack.push(depSlug);
                        }
                    }
                }
            }
        }
        return visited;
    }

    /**
     * Phase 2: 构建 skills prompt 段落
     */
    @SuppressWarnings("unchecked")
    private String buildSkillsPrompt(BaseContext context) {
        Map<String, Object> metadata = (Map<String, Object>) context.getRuntimeSkillMetadata();
        if (metadata == null || metadata.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- 可用技能 ---\n");
        sb.append("你可以通过读取技能目录中的 SKILL.md 文件来激活并使用以下技能：\n\n");

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Map<String, Object> info = (Map<String, Object>) entry.getValue();
            sb.append("- **").append(info.getOrDefault("name", entry.getKey())).append("**");
            sb.append(" (`").append(info.getOrDefault("path", "")).append("`)");
            sb.append(": ").append(info.getOrDefault("description", "")).append("\n");
        }

        sb.append("\n读取 SKILL.md 后，该技能的工具和能力将被自动加载。\n");
        sb.append("---\n");

        return sb.toString();
    }

    /**
     * 将 skills prompt 注入到 system message 末尾
     */
    private void injectSystemPrompt(List<ChatMessage> messages, String prompt) {
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.getRole())) {
                String existing = msg.getContent();
                msg.setContent(existing != null ? existing + prompt : prompt);
                return;
            }
        }
        // 如果没有 system message，在开头添加一个
        messages.add(0, new ChatMessage("system", prompt));
    }

    /**
     * 从 tool_call 中提取文件路径
     */
    private String extractFilePath(ChatMessage.ToolCall tc) {
        try {
            String argsJson = tc.getFunction().getArguments();
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);
            String path = (String) args.get("file_path");
            if (path == null) path = (String) args.get("path");
            return path;
        } catch (Exception e) {
            log.debug("[SkillsMiddleware] Failed to parse tool call args: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建技能 SKILL.md 内容（从 metadata）
     */
    @SuppressWarnings("unchecked")
    private String getSkillContent(BaseContext context, String slug) {
        Map<String, Object> metadata = (Map<String, Object>) context.getRuntimeSkillMetadata();
        if (metadata == null || !metadata.containsKey(slug)) {
            return "Error: Skill metadata not found for " + slug;
        }
        Map<String, Object> info = (Map<String, Object>) metadata.get(slug);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(info.getOrDefault("name", slug)).append("\n\n");
        sb.append(info.getOrDefault("description", "")).append("\n");
        return sb.toString();
    }

    /**
     * 加载激活技能的依赖（tools/mcps）
     */
    @SuppressWarnings("unchecked")
    private void loadActivatedSkillDependencies(BaseContext context, String slug) {
        Map<String, Object> depMap = (Map<String, Object>) context.getRuntimeSkillDependencyMap();
        if (depMap == null || !depMap.containsKey(slug)) return;

        Map<String, Object> deps = (Map<String, Object>) depMap.get(slug);
        List<String> toolDeps = (List<String>) deps.getOrDefault("tools", List.of());

        // 将技能依赖的工具添加到 context 的 tools 列表中
        List<String> currentTools = context.getTools();
        if (currentTools == null) currentTools = new ArrayList<>();
        boolean changed = false;
        for (String toolName : toolDeps) {
            if (!currentTools.contains(toolName)) {
                currentTools.add(toolName);
                changed = true;
            }
        }
        if (changed) {
            context.setTools(currentTools);
            log.info("[SkillsMiddleware] 为技能 '{}' 加载了 {} 个工具依赖", slug, toolDeps.size());
        }
    }
}
