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

    /**
     * F1+：展开直注块单 run 只处理一次的哨兵键（INJECTED_FLAG 模式，工具循环不重复注入/告警）。
     * 沿用 F1 must_use 时代的键名（runtimeState 存量兼容），语义已从"must_use 提示已处理"
     * 升级为"匹配技能全文直注已处理"（直注守卫，2026-10-07）。
     */
    public static final String MUST_USE_INJECTED_FLAG = "pinnedSkillsMustUseHandled";

    /**
     * runtimeState 中技能运行期凭据 Map 的键（F4 技能运行期凭据通道，skill-feature-batch.md F4）。
     * 值为 name → value 的 Map：由当轮挂载技能声明的 envs 并集从 fastrag 后端进程环境变量解析而来，
     * 经 AgentEngine 既有 runtimeState → ToolContext.extensions 浅拷贝供 SandboxToolExecutor 消费。
     * <b>值绝不进 prompt/事件/日志</b>（经沙箱 exec env 机制注入，不拼命令行）。
     */
    public static final String SKILL_ENV_KEY = "skillEnv";

    /** 环境变量解析函数（缺省 System.getenv；包内可注入供单测替换，生产不改） */
    transient java.util.function.Function<String, String> envResolver = System::getenv;

    @Override
    public int getOrder() { return 4; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        if (context.getSkills() == null || context.getSkills().isEmpty()) {
            // 无绑定技能：点名全为集外，pinned/匹配不新增能力——仅 warn 静默忽略（单 run 一次）
            expandMatchedSkills(context, messages);
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

        // Phase 3: F1+ 匹配技能全文直注——pinned ∪ keywords/name 命中并集，全文直注 system（单 run 一次）
        expandMatchedSkills(context, messages);

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

        // F4 技能运行期凭据通道：汇总闭包内技能 envs 并集并解析（run 级缓存——本方法 skillsLoaded 门控单 run 一次）
        resolveAndStoreSkillEnvs(context, entityCache.values());
    }

    /**
     * F4（skill-feature-batch.md F4，2026-10-07）：解析挂载技能声明的 envs 并集（当轮闭包），
     * 逐名经 {@link #envResolver}（生产 = System.getenv，fastrag 后端进程环境变量）解析，
     * 结果 Map 写入 runtimeState.{@link #SKILL_ENV_KEY}。
     * 缺失项不注入，收集清单 warn 一次（文案可操作「请在 fastrag-app 环境配置」，仅含变量名——
     * 值绝不进日志/prompt/事件）。声明为空则写空 Map（下游等同默认空 env）。
     */
    private void resolveAndStoreSkillEnvs(BaseContext context, Collection<Skill> skills) {
        SkillEnvResolution resolution = resolveSkillEnvs(skills, envResolver);
        if (!resolution.missing().isEmpty()) {
            log.warn("[SkillsMiddleware] Skill env vars missing ({}), 不会注入: {}。请在 fastrag-app 环境配置",
                    resolution.missing().size(), resolution.missing());
        }
        context.getRuntimeState().put(SKILL_ENV_KEY, resolution.resolved());
        log.debug("[SkillsMiddleware] Skill env resolved: {} vars injected", resolution.resolved().size());
    }

    /** env 解析结果：resolved = 名值对（仅已配置项），missing = 缺失变量名清单（声明顺序去重）。 */
    public record SkillEnvResolution(Map<String, String> resolved, List<String> missing) {
    }

    /**
     * F4 纯函数：汇总技能集合 envs 并集（去重、保声明顺序）并逐名解析。
     * null/空白变量名跳过；解析为 null/空白视为缺失；缺失不阻断、只收集。
     *
     * @param getenv 环境变量解析函数（生产 System::getenv，测试可注入替身）
     */
    static SkillEnvResolution resolveSkillEnvs(Collection<Skill> skills,
            java.util.function.Function<String, String> getenv) {
        Map<String, String> resolved = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        if (skills == null) {
            return new SkillEnvResolution(resolved, missing);
        }
        for (Skill skill : skills) {
            if (skill == null || skill.getEnvs() == null) continue;
            for (String name : skill.getEnvs()) {
                if (name == null || name.isBlank() || !seen.add(name)) continue;
                String value = getenv != null ? getenv.apply(name) : null;
                if (value == null || value.isBlank()) {
                    missing.add(name);
                } else {
                    resolved.put(name, value);
                }
            }
        }
        return new SkillEnvResolution(resolved, missing);
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
     * F1+（skill-feature-batch.md F1 增强，2026-10-07）：匹配技能全文直注——两路并集
     * （pinned ∪ keyword/name 命中）非空则把命中技能的 SKILL.md 全文以
     * {@code <skill_instruction>} 块直注 system message，引擎直接展开、不再赌模型自觉
     * read_file（Qwen3-8B 真实日志：must_use 已注入仍跳过 read_file）。只直注加强、
     * 不屏蔽其他工具，零 LLM 调用；展开集为空 → 维持现状（仅可用技能列表）。
 * 每 token 成本：单技能 1-2KB，逐轮随 system 重复可接受（与既有技能列表同机制）。
     * <ul>
     *   <li>a 点名直注：{@link #PINNED_SKILL_NAMES_KEY}（AppServiceImpl 会话执行核写入）
     *       与可用集合（闭包实体，slug/name 均可命中）求交集；集外点名静默忽略并 warn。</li>
     *   <li>b 描述自动匹配：当轮最后一条用户消息（{@link #extractLastUserQuery}）与技能
     *       frontmatter {@code keywords}（{@link #keywordsOf}，不区分大小写包含命中）或
     *       技能 name（被 query 包含）匹配。</li>
     * </ul>
     * 单 run 只处理一次（{@link #MUST_USE_INJECTED_FLAG} 直注守卫，工具循环多轮模型调用
     * 不重复注入、不重复告警）。匹配逻辑纯函数化在 {@link #expandMatched}（参照 resolveSkillEnvs 先例）。
     */
    private void expandMatchedSkills(BaseContext context, List<ChatMessage> messages) {
        Map<String, Object> state = context.getRuntimeState();
        if (state.get(MUST_USE_INJECTED_FLAG) != null) return;

        List<String> pinned = normalizedPinnedNames(state.get(PINNED_SKILL_NAMES_KEY));

        // 可用技能 = run 级闭包缓存实体（M2-5，零查库）
        Map<String, Skill> entities = context.getRuntimeSkillEntities();
        Collection<Skill> available = entities != null ? entities.values() : List.of();

        String query = extractLastUserQuery(messages);
        SkillExpansion expansion = expandMatched(available, pinned, query);

        if (!expansion.ignoredPinned().isEmpty()) {
            log.warn("[SkillsMiddleware] Pinned skills outside available set ignored: {}",
                    expansion.ignoredPinned());
        }

        // 无论是否命中都置哨兵：本轮展开已处理，工具循环不再重复注入/告警
        state.put(MUST_USE_INJECTED_FLAG, Boolean.TRUE);
        if (expansion.matched().isEmpty() || messages == null || messages.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (Skill s : expansion.matched()) {
            sb.append(skillInstructionBlock(s));
        }
        injectSystemPrompt(messages, sb.toString());
        log.debug("[SkillsMiddleware] Injected expanded skill instructions: {}",
                expansion.matched().stream().map(Skill::getSlug).toList());
    }

    /** 匹配展开结果：matched = 直注集（pinned 优先、按 slug 去重），ignoredPinned = 集外点名清单。 */
    record SkillExpansion(List<Skill> matched, List<String> ignoredPinned) {
    }

    /**
     * F1+ 纯函数：pinned ∪ keyword/name 匹配（a+b 并集）。
     * pinned 按 slug/name 两键命中（先到先得），未命中收集 ignoredPinned；
     * query 非空白时对未匹配技能做 keywords/name 包含命中（均不区分大小写）。
     * null 输入安全：available/pinned/query 皆可为 null。
     */
    static SkillExpansion expandMatched(Collection<Skill> available, List<String> pinnedNames, String query) {
        List<Skill> matched = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        Set<String> seenSlugs = new HashSet<>();

        // a) 点名：slug 与 name 均作命中键（两键同指一个实体时先到先得）
        Map<String, Skill> byKey = new LinkedHashMap<>();
        if (available != null) {
            for (Skill s : available) {
                if (s == null) continue;
                if (s.getSlug() != null) byKey.putIfAbsent(s.getSlug(), s);
                if (s.getName() != null) byKey.putIfAbsent(s.getName(), s);
            }
        }
        if (pinnedNames != null) {
            for (String name : pinnedNames) {
                Skill s = byKey.get(name);
                if (s == null) {
                    ignored.add(name);
                } else if (seenSlugs.add(s.getSlug())) {
                    matched.add(s); // 同一技能被 slug 与 name 重复点名只直注一次
                }
            }
        }

        // b) 描述自动匹配：query 包含 keywords 或 name（不区分大小写）
        if (query != null && !query.isBlank() && available != null) {
            String lowerQuery = query.toLowerCase();
            for (Skill s : available) {
                if (s == null || s.getSlug() == null || !seenSlugs.add(s.getSlug())) continue;
                if (matchesQuery(s, lowerQuery)) matched.add(s);
            }
        }
        return new SkillExpansion(matched, ignored);
    }

    /** b 匹配谓词：keywords 任一命中，或技能 name 被（已小写的）query 包含。 */
    private static boolean matchesQuery(Skill s, String lowerQuery) {
        for (String kw : keywordsOf(s)) {
            if (lowerQuery.contains(kw.toLowerCase())) return true;
        }
        String name = s.getName();
        return name != null && !name.isBlank() && lowerQuery.contains(name.toLowerCase());
    }

    /**
     * 从 {@code skill.metadata}（SKILL.md frontmatter 全量 JSON，parser 已保证）的
     * {@code keywords} 键读关键词：list 逐项、逗号分隔 string 拆分（兼容全角逗号），
     * 逐项 trim、去空白；声明缺省/形态异常返回空列表（不阻断）。
     */
    static List<String> keywordsOf(Skill skill) {
        if (skill == null || skill.getMetadata() == null) return List.of();
        Object v = skill.getMetadata().get("keywords");
        if (v == null) return List.of();
        List<?> items = v instanceof List<?> list ? list : List.of(v);
        List<String> out = new ArrayList<>();
        for (Object o : items) {
            if (o == null) continue;
            for (String part : o.toString().split("[,，]")) {
                String p = part.strip();
                if (!p.isEmpty()) out.add(p);
            }
        }
        return out;
    }

    /**
     * query 提取：messages 中最后一条 role=user 的 content（无则 null，含空白由调用方过滤）。
     */
    static String extractLastUserQuery(List<ChatMessage> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m != null && "user".equals(m.getRole())) return m.getContent();
        }
        return null;
    }

    /**
     * 单技能直注块：{@code <skill_instruction skill="{name}">} + 展开说明 + SKILL.md 全文
     * （取 runtimeSkillEntities 缓存 content，DB 为正文事实源）+ 闭合标签。
     */
    static String skillInstructionBlock(Skill s) {
        String name = s.getName() != null && !s.getName().isBlank() ? s.getName() : s.getSlug();
        String content = s.getContent() != null ? s.getContent() : "";
        return "\n\n<skill_instruction skill=\"" + name + "\">\n"
                + "（用户点名或查询匹配到的技能指引，已展开，直接遵循，无需再 read_file）\n"
                + content + "\n</skill_instruction>\n";
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
