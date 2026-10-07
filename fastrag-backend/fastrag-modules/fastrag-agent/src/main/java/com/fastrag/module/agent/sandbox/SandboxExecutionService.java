package com.fastrag.module.agent.sandbox;

import com.fastrag.common.util.OutputBudget;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    /** 输出截断上限 256KB（承接旧 SandboxBackend 语义；I3 起为头尾保留式截断） */
    private static final int MAX_OUTPUT_CHARS = 262_144;
    private static final int DEFAULT_EXEC_TIMEOUT_SECONDS = 120;

    // ---------- F2 附属文件物化限额（docs/plan/skill-feature-batch.md F2，对齐 WeKnora staging） ----------

    /** 单附属文件上限 2MB */
    private static final long MAX_ATTACHMENT_FILE_BYTES = 2L * 1024 * 1024;
    /** 单技能附属文件总量上限 20MB */
    private static final long MAX_ATTACHMENT_TOTAL_BYTES = 20L * 1024 * 1024;
    /** 单技能附属文件个数上限 200 */
    private static final int MAX_ATTACHMENT_FILES = 200;
    /** 排除目录名（VCS / 依赖 / 虚拟环境 / 字节码缓存） */
    private static final List<String> EXCLUDED_DIR_NAMES = List.of(".git", "node_modules", "venv", "__pycache__");

    /**
     * 技能磁盘目录根（{data-dir}/skills/{slug}/，与 tools 侧 SkillFileService 同款布局）。
     * 字段注入（对齐 SkillFileServiceImpl 口径），未配置/未注入（单测）时仅物化 SKILL.md。
     */
    @Value("${fastrag.data-dir:${user.home}/.fastrag/data}")
    private String dataDir;

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
     * 空环境变量（F4 前的既有七动作调用方兼容入口）。
     */
    public String execute(String userId, String sessionId, String sandboxConfigId,
                          String command, Integer timeoutSec, String cwd) {
        return execute(userId, sessionId, sandboxConfigId, command, timeoutSec, cwd, Map.of());
    }

    /**
     * 执行命令（F4 技能运行期凭据通道重载，skill-feature-batch.md F4，2026-10-07）：
     * {@code env} 以容器 exec 的 env 机制附加（docker ExecCreate withEnv），<b>绝不拼进命令行字符串</b>，
     * 值不进 fastrag 日志/事件/prompt。null/空 env 与原签名行为完全一致。
     */
    public String execute(String userId, String sessionId, String sandboxConfigId,
                          String command, Integer timeoutSec, String cwd, Map<String, String> env) {
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
                    .env(env == null ? Map.of() : env)
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
     * 技能物化到 {@code /workspace/skills/{slug}/}（只读子树，F2 全目录物化）：
     * <ul>
     *   <li>SKILL.md 以 DB content 渲染写入（正文事实源裁决不变）</li>
     *   <li>附属文件（scripts/references 等）自磁盘目录 {@code {data-dir}/skills/{slug}/}
     *       复制到沙箱同名相对路径，限额：排除 .git/node_modules/venv/__pycache__ 与隐藏文件，
     *       单文件 ≤2MB、总量 ≤20MB、文件数 ≤200，超限跳过并 warn</li>
     * </ul>
     * 沙箱不可用返回空 Map（降级不阻断）；磁盘目录缺失（纯 DB 技能）仅物化 SKILL.md。
     *
     * @return slug → SKILL.md 沙箱内路径
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
                materializeAttachments(r, slug);
            } catch (Exception e) {
                log.warn("[SandboxExecution] syncSkills '{}' failed, continue: {}", slug, e.getMessage());
            }
        }
        return synced;
    }

    /**
     * F2：附属文件全目录物化（磁盘 → 沙箱 {@code /workspace/skills/{slug}/}）。
     * 限额超限跳过 + warn（降级基调：恒不抛异常、恒不阻断）；逐文件失败同样 warn 跳过。
     */
    private void materializeAttachments(Resolution r, String slug) {
        if (dataDir == null || dataDir.isBlank()) {
            return; // 未注入数据目录（如单测）：仅 SKILL.md，DB 事实源不受影响
        }
        Path skillDir = Paths.get(dataDir, "skills", slug);
        if (!Files.isDirectory(skillDir)) {
            return; // 纯 DB 技能（如内置种子）无磁盘目录，静默跳过
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(skillDir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !isExcluded(skillDir, p))
                    .filter(p -> !isRootSkillMd(skillDir, p))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            log.warn("[SandboxExecution] materializeAttachments '{}' walk failed, skip: {}",
                    slug, e.getMessage());
            return;
        }
        int count = 0;
        long total = 0;
        for (Path file : files) {
            try {
                long size = Files.size(file);
                if (size > MAX_ATTACHMENT_FILE_BYTES) {
                    log.warn("[SandboxExecution] materializeAttachments '{}': file '{}' {} bytes > {} cap, skip",
                            slug, file.getFileName(), size, MAX_ATTACHMENT_FILE_BYTES);
                    continue;
                }
                if (count >= MAX_ATTACHMENT_FILES) {
                    log.warn("[SandboxExecution] materializeAttachments '{}': file count cap {} reached, skip rest",
                            slug, MAX_ATTACHMENT_FILES);
                    break;
                }
                if (total + size > MAX_ATTACHMENT_TOTAL_BYTES) {
                    log.warn("[SandboxExecution] materializeAttachments '{}': total bytes cap {} reached at '{}', skip",
                            slug, MAX_ATTACHMENT_TOTAL_BYTES, file.getFileName());
                    continue;
                }
                String relative = skillDir.relativize(file).toString().replace('\\', '/');
                String target = SandboxWorkspaceLayout.SKILLS_ROOT + "/" + slug + "/" + relative;
                r.client().writeFile(r.handle(), target,
                        new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                count++;
                total += size;
            } catch (Exception e) {
                log.warn("[SandboxExecution] materializeAttachments '{}': file '{}' failed, continue: {}",
                        slug, file, e.getMessage());
            }
        }
        if (count > 0) {
            log.info("[SandboxExecution] materializeAttachments '{}': {} files / {} bytes synced",
                    slug, count, total);
        }
    }

    /** 排除判定：相对路径上任一段为隐藏文件（. 开头）或命中排除目录名（.git/node_modules/venv/__pycache__）。 */
    private static boolean isExcluded(Path skillDir, Path file) {
        for (Path part : skillDir.relativize(file)) {
            String name = part.toString();
            if (name.startsWith(".") || EXCLUDED_DIR_NAMES.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** 根 SKILL.md 不走磁盘复制（DB content 为正文事实源，已单独渲染写入）。 */
    private static boolean isRootSkillMd(Path skillDir, Path file) {
        return skillDir.relativize(file).toString().equals("SKILL.md");
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
        // I1+I3：rune 安全 + 头尾保留截断（错误信息常在尾部，中间整体省略）；仅此方法区域改动
        return OutputBudget.truncateMiddle(output, MAX_OUTPUT_CHARS);
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
