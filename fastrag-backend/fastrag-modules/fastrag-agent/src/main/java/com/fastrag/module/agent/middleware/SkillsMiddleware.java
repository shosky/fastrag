package com.fastrag.module.agent.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.context.ChatBotContext;
import com.fastrag.module.agent.sandbox.SandboxExecutionService;
import com.fastrag.module.agent.sandbox.SandboxWorkspaceLayout;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.service.SkillService;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 技能运行时中间件（增强版）。
 * 
 * 三段式渐进加载：
 * Phase 1 — beforeModelCall: 填充运行时技能上下文，构建 skills prompt 注入 system message；
 *           沙箱可用时经 {@link SandboxExecutionService#syncSkills} 把激活技能物化到
 *           {@code /workspace/skills/{slug}}（REQ-H12，降级不阻断）
 * Phase 2 — interceptToolCall: 拦截 read_file 读取 SKILL.md，激活技能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillsMiddleware implements AgentMiddleware {

    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private SandboxExecutionService sandboxExecutionService;

    private static final Pattern SKILL_READ_PATH_PATTERN =
        Pattern.compile(Pattern.quote(SandboxWorkspaceLayout.SKILLS_ROOT) + "/([^/]+)/SKILL\\.md");

    /**
     * runtimeState 中用户 @ 点名技能列表的键（F1 @提及技能；AppServiceImpl 会话执行核写入，
     * 条目为技能名称或 slug——@ 下拉数据源为 accessible 列表，二者皆是合法点名形态）。
     */
    public static final String PINNED_SKILL_NAMES_KEY = "pinnedSkillNames";

    /** F1：pinned must_use 块单 run 只处理一次的哨兵键（INJECTED_FLAG 模式，工具循环不重复追加/告警） */
    public static final String MUST_USE_INJECTED_FLAG = "pinnedSkillsMustUseHandled";

    @Override
    public int getOrder() { return 4; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        if (context.getSkills() == null || context.getSkills().isEmpty()) {
            // 无绑定技能：点名全为集外，pinned 不新增能力——仅 warn 静默忽略（单 run 一次）
            applyPinnedSkills(context, messages);
            return context;
        }

        // Phase 1: 填充运行时技能上下文（M2-5 run 级缓存：技能集合 run 内不变，仅首轮装配）
        if (!context.isSkillsLoaded()) {
            fillRuntimeSkillContext(context);
            context.setSkillsLoaded(true);
        }

        // Phase 1.5: 沙箱可用时物化激活技能到 /workspace/skills/{slug}（REQ-H12，降级不阻断）
        syncSkillsToSandbox(context);

        // Phase 2: 构建 skills prompt 注入
        String skillsPrompt = buildSkillsPrompt(context);
        if (!skillsPrompt.isEmpty()) {
            injectSystemPrompt(messages, skillsPrompt);
        }

        // Phase 3: F1 @提及技能——pinned 与当轮可用集合求交集，命中追加 must_use 强提示（单 run 一次）
        applyPinnedSkills(context, messages);

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
     * Phase 1: 填充运行时技能上下文（M2-2，裁决 2A：绑定统一到技能主键 id）。
     * 链路：ids → {@link SkillService#listByIds} → 过滤 enabled=1 → 与 listAccessible
     * （可见+启用，用户域）按 id 交集得 seed slugs（被丢绑定 warn 含 id）→ 依赖闭包展开。
     */
    private void fillRuntimeSkillContext(BaseContext context) {
        List<String> ids = context.getSkills();
        if (ids == null || ids.isEmpty()) return;

        // 绑定按主键解析，过滤禁用技能
        List<Skill> boundSkills = skillService.listByIds(ids).stream()
            .filter(s -> s.getEnabled() != null && s.getEnabled() == 1)
            .toList();

        // 用户域可见 + 启用技能（slug → 实体，供闭包展开）
        List<Skill> accessibleSkills = skillService.listAccessible(null, null, null);
        Map<String, Skill> skillMap = accessibleSkills.stream()
            .filter(s -> s.getSlug() != null)
            .collect(Collectors.toMap(Skill::getSlug, s -> s, (a, b) -> a));
        Set<String> visibleIds = accessibleSkills.stream()
            .map(Skill::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // 求交集得 seed slugs；被丢绑定 warn（含 id，可诊断）
        Set<String> boundIds = boundSkills.stream()
            .map(Skill::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (String id : ids) {
            if (id != null && !boundIds.contains(id)) {
                log.warn("[SkillsMiddleware] Skill binding dropped (not found or disabled): skillId={}", id);
            }
        }
        List<String> seedSlugs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Skill bound : boundSkills) {
            if (bound.getSlug() == null || !seen.add(bound.getId())) continue;
            if (visibleIds.contains(bound.getId())) {
                seedSlugs.add(bound.getSlug());
            } else {
                log.warn("[SkillsMiddleware] Skill binding dropped (not visible to user): skillId={}, slug={}",
                        bound.getId(), bound.getSlug());
            }
        }
        if (seedSlugs.isEmpty()) return;

        // 计算传递闭包（递归解析 skill_dependencies）
        Set<String> closure = expandSkillClosure(seedSlugs, skillMap);

        // 缓存闭包内技能实体（M2-1/M2-5：SKILL.md 全文与 prompt 元数据零查库）
        Map<String, Skill> entityCache = new LinkedHashMap<>();
        for (String slug : closure) {
            Skill s = skillMap.get(slug);
            if (s != null) entityCache.put(slug, s);
        }
        context.setRuntimeSkillEntities(entityCache);

        // 构建 prompt 元数据
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> depMap = new LinkedHashMap<>();
        for (String slug : closure) {
            Skill s = skillMap.get(slug);
            if (s != null) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", s.getName());
                info.put("description", s.getDescription());
                info.put("path", SandboxWorkspaceLayout.SKILLS_ROOT + "/" + slug + "/SKILL.md");
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
     * 沙箱可用时把激活技能（传递闭包）物化到 /workspace/skills/{slug}/SKILL.md（REQ-H12）。
     * 门面内部判降级（不可用返回空 Map）——本方法零感知后端差异，失败不阻断。
     * M2-5 run 级缓存：闭包 slug 集与上次成功同步集合一致则跳过；同步失败不记录，下一轮重试。
     */
    private void syncSkillsToSandbox(BaseContext context) {
        if (sandboxExecutionService == null
                || context.getPromptSkills() == null || context.getPromptSkills().isEmpty()) {
            return;
        }
        Set<String> currentSlugs = new LinkedHashSet<>(context.getPromptSkills());
        if (currentSlugs.equals(context.getSyncedSkillSlugs())) {
            return;
        }
        try {
            sandboxExecutionService.syncSkills(
                    context.getUid(), sessionIdOf(context), sandboxConfigIdOf(context),
                    context.getPromptSkills());
            context.setSyncedSkillSlugs(currentSlugs);
        } catch (Exception e) {
            log.warn("[SkillsMiddleware] Skill sync to sandbox failed, fall back to virtual fs: {}",
                    e.getMessage());
        }
    }

    /** 会话键：fileThreadId 扩展优先，回落 threadId → runId → uid（与 FilesystemMiddleware 一致）。 */
    private String sessionIdOf(BaseContext context) {
        Object fileThreadId = context.getRuntimeState().get("fileThreadId");
        if (fileThreadId instanceof String s && !s.isBlank()) {
            return s;
        }
        if (context.getThreadId() != null && !context.getThreadId().isBlank()) {
            return context.getThreadId();
        }
        if (context.getRunId() != null && !context.getRunId().isBlank()) {
            return context.getRunId();
        }
        return context.getUid();
    }

    /** 沙箱配置 ID：runtimeState 优先（FilesystemMiddleware order=1 已写入）；回落 ChatBotContext 字段。 */
    private String sandboxConfigIdOf(BaseContext context) {
        Object existing = context.getRuntimeState().get("sandboxConfigId");
        if (existing instanceof String s && !s.isBlank()) {
            return s;
        }
        if (context instanceof ChatBotContext chatBot) {
            return chatBot.getSandboxConfigId();
        }
        return null;
    }

    /**
     * F1 @提及技能（skill-feature-batch.md F1，2026-10-07 裁决）：用户 @ 点名的技能
     * （{@link #PINNED_SKILL_NAMES_KEY}，AppServiceImpl 会话执行核写入）与当轮可用技能集合
     * （promptSkills 传递闭包，slug 或名称均可命中）求交集；交集非空则追加 {@code <must_use>}
     * 强提示块——pinned 不收窄白名单、不新增能力（对齐 WeKnora），集外点名静默忽略并 warn
     * （含被忽略的 name）。单 run 只处理一次（{@link #MUST_USE_INJECTED_FLAG}，工具循环多轮
     * 模型调用不重复追加、不重复告警）。
     */
    private void applyPinnedSkills(BaseContext context, List<ChatMessage> messages) {
        Map<String, Object> state = context.getRuntimeState();
        if (state.get(MUST_USE_INJECTED_FLAG) != null) return;

        List<String> pinned = normalizedPinnedNames(state.get(PINNED_SKILL_NAMES_KEY));
        if (pinned.isEmpty()) return;

        // 可用技能索引：闭包内技能的 slug 与 name 均作命中键（两键同指一个实体时先到先得）
        Map<String, Skill> entities = context.getRuntimeSkillEntities();
        Map<String, Skill> available = new LinkedHashMap<>();
        if (entities != null) {
            for (Skill s : entities.values()) {
                if (s.getSlug() != null) available.putIfAbsent(s.getSlug(), s);
                if (s.getName() != null) available.putIfAbsent(s.getName(), s);
            }
        }

        List<Skill> matched = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        Set<String> seenSlugs = new HashSet<>();
        for (String name : pinned) {
            Skill s = available.get(name);
            if (s == null) {
                ignored.add(name);
            } else if (seenSlugs.add(s.getSlug())) {
                matched.add(s); // 同一技能被 slug 与 name 重复点名只注入一次
            }
        }
        if (!ignored.isEmpty()) {
            log.warn("[SkillsMiddleware] Pinned skills outside available set ignored: {}", ignored);
        }

        // 无论是否命中都置哨兵：本轮点名已处理，工具循环不再重复告警
        state.put(MUST_USE_INJECTED_FLAG, Boolean.TRUE);
        if (matched.isEmpty() || messages == null || messages.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n<must_use>\n本轮用户点名了以下技能，必须优先调用 read_file 读取其 SKILL.md 并遵循指引：\n");
        for (Skill s : matched) {
            sb.append("- ").append(s.getName() != null ? s.getName() : s.getSlug())
              .append(" (").append(SandboxWorkspaceLayout.SKILLS_ROOT).append("/")
              .append(s.getSlug()).append("/SKILL.md)\n");
        }
        sb.append("</must_use>\n");
        injectSystemPrompt(messages, sb.toString());
        log.debug("[SkillsMiddleware] Injected pinned-skills must_use block: {}", matched.stream().map(Skill::getSlug).toList());
    }

    /** 点名列表规范化：List 投影 + trim + 去空白 + 去重，保持请求顺序。 */
    private static List<String> normalizedPinnedNames(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Object o : list) {
            if (o == null) continue;
            String n = o.toString().strip();
            if (!n.isEmpty()) out.add(n);
        }
        return new ArrayList<>(out);
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

    /** 技能描述注入预算（M2-6，D8）：渲染前截断，对齐 WeKnora prompts.go:253 */
    static final int MAX_DESCRIPTION_LENGTH = 600;

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
            sb.append(": ").append(truncateDescription(info.getOrDefault("description", ""))).append("\n");
        }

        sb.append("\n读取 SKILL.md 后，该技能的工具和能力将被自动加载。\n");
        sb.append("---\n");

        return sb.toString();
    }

    /** M2-6：description 注入前截断到 {@value #MAX_DESCRIPTION_LENGTH} 字符。 */
    private static String truncateDescription(Object description) {
        if (description == null) return "";
        String d = description.toString();
        return d.length() > MAX_DESCRIPTION_LENGTH ? d.substring(0, MAX_DESCRIPTION_LENGTH) : d;
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
     * 构建技能 SKILL.md 全文（M2-1，裁决 1A：拦截保留、DB content 为源）。
     * 格式：{@code # {name}\n\n{description}\n\n---\n\n{content}}，content 即技能正文指令。
     * 实体从 run 级缓存取（M2-5），不查库。
     */
    private String getSkillContent(BaseContext context, String slug) {
        Map<String, Skill> entities = context.getRuntimeSkillEntities();
        Skill s = entities != null ? entities.get(slug) : null;
        if (s == null) {
            return "Error: Skill metadata not found for " + slug;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(s.getName() != null ? s.getName() : slug).append("\n\n");
        sb.append(s.getDescription() != null ? s.getDescription() : "").append("\n\n");
        sb.append("---\n\n");
        sb.append(s.getContent() != null ? s.getContent() : "");
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

        // 将技能依赖的工具添加到 context 的 tools 列表（M3-2：追加前规范化为 ToolRegistry 可解析形态，
        // 仅追加工具型引用——skill: 型已由闭包展开进 readableSkills，mcp: 型走 depMap.mcps 通道）
        List<String> currentTools = context.getTools();
        if (currentTools == null) currentTools = new ArrayList<>();
        boolean changed = false;
        int added = 0;
        for (String toolName : toolDeps) {
            if (!isToolTypeRef(toolName)) continue;
            String normalized = normalizeDependencyRef(toolName);
            if (!currentTools.contains(normalized)) {
                currentTools.add(normalized);
                changed = true;
                added++;
            }
        }
        if (changed) {
            context.setTools(currentTools);
            // M2-3（D7）：置脏标志，AgentEngine 在下一次模型调用前重算 openaiTools
            context.setToolsDirty(true);
            log.info("[SkillsMiddleware] 为技能 '{}' 加载了 {} 个工具依赖", slug, added);
        }
    }

    /**
     * 依赖串规范化（M3-2，以 ToolRegistry.resolveSingleTool 实际识别规则为准）：
     * <ul>
     *   <li>type=tool：{@code tool:xxx} / 裸 id → 裸工具 id（registry 默认分支按 HTTP/builtin 工具解析）</li>
     *   <li>type=mcp：{@code mcp:xxx} → {@code mcp:{name}}（规范形态保持）</li>
     *   <li>type=skill：{@code skill:xxx} → {@code skill:{slug}}（规范形态保持，闭包展开消费）</li>
     * </ul>
     * 未知前缀原样保留；null/空白返回空串。
     */
    static String normalizeDependencyRef(String ref) {
        if (ref == null) return "";
        String r = ref.strip();
        if (r.isEmpty()) return "";
        if (r.startsWith("tool:")) return r.substring(5).strip();
        return r;
    }

    /** 是否工具型依赖引用（裸 id 或 tool: 前缀）——仅工具型追加进 context.tools */
    private static boolean isToolTypeRef(String ref) {
        if (ref == null) return false;
        String r = ref.strip();
        if (r.isEmpty()) return false;
        return !r.startsWith("skill:") && !r.startsWith("skill_")
                && !r.startsWith("mcp:") && !r.startsWith("mcp_");
    }
}
