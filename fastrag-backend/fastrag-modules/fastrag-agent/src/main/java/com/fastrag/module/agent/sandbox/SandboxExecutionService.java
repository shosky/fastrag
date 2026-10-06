package com.fastrag.module.agent.sandbox;

import com.fastrag.module.agent.sandbox.config.EffectiveConfig;
import com.fastrag.module.agent.sandbox.config.SandboxConfigService;
import com.fastrag.module.agent.sandbox.provider.DirEntry;
import com.fastrag.module.agent.sandbox.provider.ExecRequest;
import com.fastrag.module.agent.sandbox.provider.ExecResult;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderRegistry;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 沙箱执行门面（contract agent.SandboxExecutionFacade 目标态，design SandboxExecutionModule，切片 S7）。
 *
 * <p>工具/中间件侧的唯一入口：7 工具动作 + 附件暂存 + 技能物化，统一应用路径策略
 * （{@link SandboxWorkspaceLayout} 谓词：可读 = /workspace 全部；可写 = 除 input 与 skills 子树）
 * 与降级语义（REQ-H03 三分支文本互异），把 provider 差异与不可用转写为 LLM 可消费的文本——
 * <b>恒不抛异常、恒不阻断运行</b>。</p>
 *
 * <p>实例获取恒经 {@link SandboxLifecycleService#acquire}——本门面不直连 provider 创建实例
 * （契约 Post 不变量）。暂存/物化只写 input 与 skills 子树（路径由布局常量构造，永不越界），
 * 不经工具面 isWritable 门（该门只约束 LLM 工具调用，REQ-H12/H13）。</p>
 */
@Slf4j
@Service
public class SandboxExecutionService {

    // ---------- 降级三分支文本（REQ-H03，三文本互不相同，tdd 用例断言） ----------

    /** 分支①：平台总开关关闭 */
    public static final String TEXT_GLOBAL_DISABLED = "Error: Sandbox is disabled (global switch)";
    /** 分支②：应用未引用沙箱配置 / 配置不存在 / 配置 disabled / 配置级禁用 */
    public static final String TEXT_NOT_CONFIGURED = "Error: Sandbox is not configured for this app";
    /** 分支③前缀：provider 连接期失败（附分类原因） */
    public static final String TEXT_UNAVAILABLE_PREFIX = "Error: Sandbox unavailable: ";

    /** 输出截断上限 256KB（承接旧 SandboxBackend 语义） */
    private static final int MAX_OUTPUT_CHARS = 262_144;
    private static final String TRUNCATION_MARKER = "\n...(truncated)";
    private static final int DEFAULT_EXEC_TIMEOUT_SECONDS = 120;

    private final SandboxConfigService configService;
    private final SandboxLifecycleService lifecycleService;
    private final SandboxProviderRegistry registry;
    private final SkillService skillService;

    public SandboxExecutionService(SandboxConfigService configService,
                                   SandboxLifecycleService lifecycleService,
                                   SandboxProviderRegistry registry,
                                   SkillService skillService) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.registry = registry;
        this.skillService = skillService;
    }

    // ---------- 降级判定 ----------

    /**
     * 三分支判定（供中间件与动作复用）：总开关关 / 应用未引用或配置禁用 / provider 连接失败。
     */
    public DegradationResult resolveDegradation(String userId, String sessionId, String sandboxConfigId) {
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        return r.errorText() != null
                ? new DegradationResult(false, r.errorText())
                : DegradationResult.AVAILABLE;
    }

    /** 降级判定结果（available=true 时 errorText 为 null）。 */
    public record DegradationResult(boolean available, String errorText) {
        static final DegradationResult AVAILABLE = new DegradationResult(true, null);
    }

    // ---------- 7 工具动作 ----------

    /**
     * 读文件：成功 = 文件内容原文（offset/limit 按行切片，可空）；失败 = 区分原因的 Error 文本。
     */
    public String readFile(String userId, String sessionId, String sandboxConfigId,
                           String path, Integer offset, Integer limit) {
        String clean;
        try {
            clean = SandboxWorkspaceLayout.clamp(path);
        } catch (SecurityException e) {
            return errorText(e);
        }
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        if (r.errorText() != null) {
            return r.errorText();
        }
        try {
            return sliceLines(r.client().readFile(r.handle(), clean), offset, limit);
        } catch (Exception e) {
            return errorText(e);
        }
    }

    /**
     * 写文件：成功 = OK 确认；input 与 skills 子树拒写（REQ-H12）。
     */
    public String writeFile(String userId, String sessionId, String sandboxConfigId,
                            String path, String content) {
        String clean;
        try {
            clean = SandboxWorkspaceLayout.clamp(path);
            if (!SandboxWorkspaceLayout.isWritable(clean)) {
                return "Error: Path not writable: " + path;
            }
        } catch (SecurityException e) {
            return errorText(e);
        }
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        if (r.errorText() != null) {
            return r.errorText();
        }
        try {
            r.client().writeFile(r.handle(), clean, content == null ? "" : content);
            return "OK";
        } catch (Exception e) {
            return errorText(e);
        }
    }

    /**
     * str_replace 编辑：成功 = OK 确认；写根限制同 writeFile。
     */
    public String editFile(String userId, String sessionId, String sandboxConfigId,
                           String path, String oldString, String newString, boolean replaceAll) {
        String clean;
        try {
            clean = SandboxWorkspaceLayout.clamp(path);
            if (!SandboxWorkspaceLayout.isWritable(clean)) {
                return "Error: Path not writable: " + path;
            }
        } catch (SecurityException e) {
            return errorText(e);
        }
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        if (r.errorText() != null) {
            return r.errorText();
        }
        try {
            r.client().editFile(r.handle(), clean,
                    oldString == null ? "" : oldString, newString == null ? "" : newString, replaceAll);
            return "OK";
        } catch (Exception e) {
            return errorText(e);
        }
    }

    /**
     * 执行命令：超时 = 工具参数 &gt; 配置 defaultTimeoutSec &gt; 120s；输出截断 256KB + 标记。
     */
    public String execute(String userId, String sessionId, String sandboxConfigId,
                          String command, Integer timeoutSec, String cwd) {
        String cleanCwd = null;
        try {
            if (cwd != null && !cwd.isBlank()) {
                cleanCwd = SandboxWorkspaceLayout.clamp(cwd);
            }
        } catch (SecurityException e) {
            return errorText(e);
        }
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        if (r.errorText() != null) {
            return r.errorText();
        }
        int effective = effectiveTimeout(timeoutSec, r.config());
        try {
            ExecRequest request = ExecRequest.builder()
                    .command(command)
                    .timeout(Duration.ofSeconds(effective))
                    .workDir(cleanCwd)
                    .build();
            ExecResult result = r.client().exec(r.handle(), request);
            StringBuilder sb = new StringBuilder(result.getStdout() == null ? "" : result.getStdout());
            if (result.getStderr() != null && !result.getStderr().isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(result.getStderr());
            }
            if (result.isKilled()) {
                sb.append("\n[command timed out after ").append(effective).append("s and was killed]");
            } else if (sb.length() == 0 && result.getExitCode() != 0) {
                // 静默失败（如脚本文件为空/不存在且 stderr 也空）时给模型最小信号，避免空结果误读为成功
                sb.append("[exit code ").append(result.getExitCode()).append(" with no output]");
            }
            return truncate(sb.toString());
        } catch (Exception e) {
            return errorText(e);
        }
    }

    /**
     * 列目录：成功 = 条目行（目录带 / 后缀）；空目录 = "(empty directory)"。
     */
    public String listDirectory(String userId, String sessionId, String sandboxConfigId, String path) {
        String clean;
        try {
            clean = SandboxWorkspaceLayout.clamp(path);
        } catch (SecurityException e) {
            return errorText(e);
        }
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        if (r.errorText() != null) {
            return r.errorText();
        }
        try {
            List<DirEntry> entries = r.client().listDir(r.handle(), clean);
            if (entries == null || entries.isEmpty()) {
                return "(empty directory)";
            }
            StringBuilder sb = new StringBuilder();
            for (DirEntry entry : entries) {
                sb.append(entry.getName()).append(entry.isDirectory() ? "/" : "").append('\n');
            }
            return truncate(sb.toString());
        } catch (Exception e) {
            return errorText(e);
        }
    }

    /**
     * 内容搜索：provider.exec 包装 {@code grep -rn [--include glob] pattern path}，输出截断。
     */
    public String grep(String userId, String sessionId, String sandboxConfigId,
                       String pattern, String path, String glob) {
        String clean;
        try {
            clean = SandboxWorkspaceLayout.clamp(path);
        } catch (SecurityException e) {
            return errorText(e);
        }
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        if (r.errorText() != null) {
            return r.errorText();
        }
        try {
            StringBuilder cmd = new StringBuilder("grep -rn ");
            if (glob != null && !glob.isBlank()) {
                cmd.append("--include=").append(shellQuote(glob)).append(' ');
            }
            cmd.append(shellQuote(pattern == null ? "" : pattern)).append(' ').append(shellQuote(clean));
            return execViaProvider(r, cmd.toString(), effectiveTimeout(null, r.config()));
        } catch (Exception e) {
            return errorText(e);
        }
    }

    /**
     * 文件名匹配：provider.exec 包装 {@code find}（pattern 含 / 时按 -path 匹配），输出截断。
     */
    public String glob(String userId, String sessionId, String sandboxConfigId,
                       String pattern, String path) {
        String clean;
        try {
            clean = SandboxWorkspaceLayout.clamp(path);
        } catch (SecurityException e) {
            return errorText(e);
        }
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        if (r.errorText() != null) {
            return r.errorText();
        }
        try {
            String p = pattern == null ? "*" : pattern;
            String cmd = p.contains("/")
                    ? "find " + shellQuote(clean) + " -path " + shellQuote(clean + "/" + p)
                    : "find " + shellQuote(clean) + " -type f -name " + shellQuote(p);
            return execViaProvider(r, cmd, effectiveTimeout(null, r.config()));
        } catch (Exception e) {
            return errorText(e);
        }
    }

    // ---------- 附件暂存（REQ-H13） ----------

    /**
     * 附件实体拷入 {@code /workspace/input/{file_name}}（同名覆盖幂等，REQ-H13）。
     *
     * @param uploads runtimeState.uploads 结构：每项 {@code {file_name, path}}（path = 服务端路径）
     * @return file_name → 沙箱内路径（/workspace/input/...）；沙箱不可用/单个失败 warn 跳过（降级基调）
     */
    public Map<String, String> stageAttachments(String userId, String sessionId, String sandboxConfigId,
                                                List<Map<String, Object>> uploads) {
        Map<String, String> staged = new LinkedHashMap<>();
        if (uploads == null || uploads.isEmpty()) {
            return staged;
        }
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        if (r.errorText() != null) {
            log.info("[SandboxExecution] stageAttachments skipped: {}", r.errorText());
            return staged;
        }
        for (Map<String, Object> upload : uploads) {
            if (upload == null) {
                continue;
            }
            String fileName = stringValue(upload.get("file_name"));
            String serverPath = stringValue(upload.get("path"));
            if (fileName == null || serverPath == null) {
                continue;
            }
            try {
                String content = new String(Files.readAllBytes(Paths.get(serverPath)), StandardCharsets.UTF_8);
                String target = SandboxWorkspaceLayout.INPUT_DIR + "/" + sanitizeFileName(fileName);
                r.client().writeFile(r.handle(), target, content);
                staged.put(fileName, target);
            } catch (Exception e) {
                log.warn("[SandboxExecution] stage attachment '{}' failed, continue: {}",
                        fileName, e.getMessage());
            }
        }
        return staged;
    }

    // ---------- 技能物化（REQ-H12） ----------

    /**
     * 技能物化到 {@code /workspace/skills/{slug}/SKILL.md}（只读子树）；
     * 沙箱不可用返回空 Map（M3-6 已删虚拟文件系统后端，技能正文以 DB content 为准，降级不阻断）。
     *
     * @return slug → 沙箱内路径
     */
    public Map<String, String> syncSkills(String userId, String sessionId, String sandboxConfigId,
                                          List<String> slugs) {
        Map<String, String> synced = new LinkedHashMap<>();
        if (slugs == null || slugs.isEmpty()) {
            return synced;
        }
        Resolution r = resolveForAction(userId, sessionId, sandboxConfigId);
        if (r.errorText() != null) {
            log.info("[SandboxExecution] syncSkills skipped: {}", r.errorText());
            return synced;
        }
        for (String slug : slugs) {
            if (slug == null || slug.isBlank()) {
                continue;
            }
            try {
                Skill skill = skillService.getBySlug(slug);
                if (skill == null) {
                    log.warn("[SandboxExecution] syncSkills: skill '{}' not found, skip", slug);
                    continue;
                }
                String target = SandboxWorkspaceLayout.SKILLS_ROOT + "/" + slug + "/SKILL.md";
                r.client().writeFile(r.handle(), target, renderSkillMd(skill));
                synced.put(slug, target);
            } catch (Exception e) {
                log.warn("[SandboxExecution] syncSkills '{}' failed, continue: {}", slug, e.getMessage());
            }
        }
        return synced;
    }

    // ---------- 内部 ----------

    /** 动作级解析结果：errorText 非 null 即降级（handle/client/config 无效）。 */
    private record Resolution(SandboxHandle handle, SandboxProviderClient client,
                              EffectiveConfig config, String errorText) {
    }

    private Resolution resolveForAction(String userId, String sessionId, String sandboxConfigId) {
        // 分支①：平台总开关
        if (!configService.isGloballyEnabled()) {
            return new Resolution(null, null, null, TEXT_GLOBAL_DISABLED);
        }
        // 分支②：应用未引用配置 / 配置不存在（含越权读取按不可见处理）/ 配置禁用
        if (sandboxConfigId == null || sandboxConfigId.isBlank()) {
            return new Resolution(null, null, null, TEXT_NOT_CONFIGURED);
        }
        com.fastrag.module.agent.entity.SandboxConfig cfg;
        try {
            com.fastrag.module.agent.context.User user = new com.fastrag.module.agent.context.User();
            user.setUid(userId);
            cfg = configService.get(sandboxConfigId, user);
        } catch (Exception e) {
            log.debug("[SandboxExecution] sandbox config {} unreadable for {}: {}",
                    sandboxConfigId, userId, e.getMessage());
            return new Resolution(null, null, null, TEXT_NOT_CONFIGURED);
        }
        if (cfg == null || "disabled".equals(cfg.getSandboxType())
                || !Boolean.TRUE.equals(cfg.getEnabled())) {
            return new Resolution(null, null, null, TEXT_NOT_CONFIGURED);
        }
        // 分支③：凭据解析/acquire 连接期失败（连接失败一律降级文本，不 fail-fast）
        try {
            EffectiveConfig effective = configService.resolveCredentials(cfg);
            SandboxHandle handle = lifecycleService.acquire(userId, sessionId, effective);
            SandboxProviderClient client = registry.require(effective.getType());
            return new Resolution(handle, client, effective, null);
        } catch (SandboxProviderException e) {
            return new Resolution(null, null, null, TEXT_UNAVAILABLE_PREFIX + describe(e));
        } catch (Exception e) {
            return new Resolution(null, null, null, TEXT_UNAVAILABLE_PREFIX + e.getMessage());
        }
    }

    /** provider.exec 包装（grep/glob 用）：无匹配/超时/截断的统一文本化。 */
    private String execViaProvider(Resolution r, String command, int timeoutSeconds) {
        ExecRequest request = ExecRequest.builder()
                .command(command)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        ExecResult result = r.client().exec(r.handle(), request);
        if (result.isKilled()) {
            return truncate(orDefault(result.getStdout(),
                    "\n[search timed out after " + timeoutSeconds + "s and was killed]"));
        }
        String stdout = result.getStdout() == null ? "" : result.getStdout();
        if (stdout.isBlank() && result.getExitCode() != 0) {
            return "No matches found";
        }
        return truncate(stdout.isBlank() ? "No matches found" : stdout);
    }

    private int effectiveTimeout(Integer paramSeconds, EffectiveConfig config) {
        if (paramSeconds != null && paramSeconds > 0) {
            return paramSeconds;
        }
        if (config != null && config.getDefaultTimeoutSec() > 0) {
            return config.getDefaultTimeoutSec();
        }
        return DEFAULT_EXEC_TIMEOUT_SECONDS;
    }

    private String truncate(String output) {
        if (output == null) {
            return "";
        }
        if (output.length() > MAX_OUTPUT_CHARS) {
            return output.substring(0, MAX_OUTPUT_CHARS) + TRUNCATION_MARKER;
        }
        return output;
    }

    private String sliceLines(String content, Integer offset, Integer limit) {
        if (content == null) {
            return "";
        }
        if (offset == null && limit == null) {
            return content;
        }
        String[] lines = content.split("\n", -1);
        int start = offset != null && offset > 0 ? Math.min(offset, lines.length) : 0;
        int end = limit != null && limit > 0 ? Math.min(start + limit, lines.length) : lines.length;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(lines[i]);
            if (i < end - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String errorText(Exception e) {
        if (e instanceof SandboxProviderException spe) {
            return TEXT_UNAVAILABLE_PREFIX + describe(spe);
        }
        return "Error: " + e.getMessage();
    }

    /** 分支③原因描述：归一分类 + provider 报文（三文本互异的区分依据）。 */
    private String describe(SandboxProviderException e) {
        return "[" + e.getKind() + "] " + e.getMessage();
    }

    /** 单引号安全包装（对齐 DockerSandboxAdapter.shellQuote），禁止裸拼容器命令。 */
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** 附件名清洗：取 basename、拒穿越片段（写入路径由布局常量构造，永不越界）。 */
    private static String sanitizeFileName(String fileName) {
        String clean = fileName.replace('\\', '/');
        int idx = clean.lastIndexOf('/');
        clean = idx >= 0 ? clean.substring(idx + 1) : clean;
        if (clean.isBlank() || clean.contains("..")) {
            return "unnamed";
        }
        return clean;
    }

    private static String stringValue(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * SKILL.md 渲染（front matter 格式与 SkillMarkdownParser 解析口径一致）。
     */
    private String renderSkillMd(Skill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(skill.getName() != null ? skill.getName() : "").append('\n');
        sb.append("slug: ").append(skill.getSlug() != null ? skill.getSlug() : "").append('\n');
        sb.append("description: ").append(skill.getDescription() != null ? skill.getDescription() : "").append('\n');
        if (skill.getVersion() != null) {
            sb.append("version: ").append(skill.getVersion()).append('\n');
        }
        if (skill.getCategory() != null) {
            sb.append("category: ").append(skill.getCategory()).append('\n');
        }
        if (skill.getAuthor() != null) {
            sb.append("author: ").append(skill.getAuthor()).append('\n');
        }
        sb.append("---\n\n");
        if (skill.getContent() != null) {
            sb.append(skill.getContent()).append('\n');
        } else {
            sb.append(skill.getDescription() != null ? skill.getDescription() : "").append('\n');
        }
        return sb.toString();
    }
}
