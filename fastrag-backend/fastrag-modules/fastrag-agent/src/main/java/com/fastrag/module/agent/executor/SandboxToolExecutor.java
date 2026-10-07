package com.fastrag.module.agent.executor;

import com.fastrag.module.agent.middleware.SkillsMiddleware;
import com.fastrag.module.agent.sandbox.SandboxExecutionService;
import com.fastrag.module.agent.sandbox.SandboxWorkspaceLayout;
import com.fastrag.module.tools.executor.ToolContext;
import com.fastrag.module.tools.executor.ToolExecutor;
import com.fastrag.module.tools.executor.ToolResult;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 沙箱文件/命令执行工具执行器。
 * 支持 read_file / write_file / edit_file / execute / list_directory / grep / glob。
 *
 * <p>type = "sandbox"。本类保留 ToolExecutor SPI 形态（getType()="sandbox"），
 * 执行全部委派 {@link SandboxExecutionService}（执行门面：路径钳制、降级三分支文本、
 * 实例获取经 Lifecycle.acquire——REQ-H03/H12）；门面恒返回文本不抛异常，
 * Error 文本作为工具输出回灌 LLM 自行调整。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxToolExecutor implements ToolExecutor {

    private final SandboxExecutionService sandboxExecutionService;

    @Override
    public String getType() {
        return "sandbox";
    }

    @Override
    public ToolResult execute(ToolDefinition tool, Map<String, Object> args, ToolContext ctx) {
        long start = System.currentTimeMillis();
        String action = tool.getConfig() != null
                ? (String) tool.getConfig().get("action") : null;
        if (action == null) {
            return ToolResult.error("No action specified in tool config", 0);
        }

        String uid = ctx.getUserId();
        // 会话键：优先 fileThreadId 扩展（与旧约定一致），缺省回落 uid
        String fileThreadId = ctx.getExtension("fileThreadId") != null
                ? (String) ctx.getExtension("fileThreadId") : uid;
        // 应用/智能体引用的沙箱配置 ID（REQ-H03 分支②载体，FilesystemMiddleware 写入 runtimeState）
        String sandboxConfigId = ctx.getExtension("sandboxConfigId") != null
                ? (String) ctx.getExtension("sandboxConfigId") : null;

        try {
            String output = switch (action) {
                case "read_file" -> {
                    String path = (String) args.get("file_path");
                    Integer offset = args.containsKey("offset")
                            ? ((Number) args.get("offset")).intValue() : null;
                    Integer limit = args.containsKey("limit")
                            ? ((Number) args.get("limit")).intValue() : null;
                    yield sandboxExecutionService.readFile(uid, fileThreadId, sandboxConfigId, path, offset, limit);
                }
                case "write_file" -> {
                    String path = (String) args.get("file_path");
                    String content = (String) args.get("content");
                    yield sandboxExecutionService.writeFile(uid, fileThreadId, sandboxConfigId, path, content);
                }
                case "edit_file" -> {
                    String path = (String) args.get("file_path");
                    String oldStr = (String) args.get("old_string");
                    String newStr = (String) args.get("new_string");
                    boolean replaceAll = Boolean.TRUE.equals(args.get("replace_all"));
                    yield sandboxExecutionService.editFile(uid, fileThreadId, sandboxConfigId,
                            path, oldStr, newStr, replaceAll);
                }
                case "execute" -> {
                    String command = (String) args.get("command");
                    Integer timeout = args.containsKey("timeout")
                            ? ((Number) args.get("timeout")).intValue() : null;
                    String cwd = (String) args.get("cwd");
                    // F4 技能运行期凭据通道（skill-feature-batch.md F4）：runtimeState.skillEnv
                    // 经 AgentEngine 既有浅拷贝到达 extensions，随容器 exec env 机制注入（不拼命令行）。
                    // 无技能/非 Map 形态一律空 env——值绝不进输出/日志（门面恒文本化返回）。
                    Map<String, String> skillEnv = skillEnvOf(ctx);
                    yield sandboxExecutionService.execute(uid, fileThreadId, sandboxConfigId,
                            command, timeout, cwd, skillEnv);
                }
                case "list_directory" -> {
                    String path = (String) args.getOrDefault("dir_path", SandboxWorkspaceLayout.ROOT);
                    yield sandboxExecutionService.listDirectory(uid, fileThreadId, sandboxConfigId, path);
                }
                case "grep" -> {
                    String pattern = (String) args.get("pattern");
                    String path = (String) args.getOrDefault("dir_path", SandboxWorkspaceLayout.ROOT);
                    String glob = (String) args.get("glob");
                    yield sandboxExecutionService.grep(uid, fileThreadId, sandboxConfigId, pattern, path, glob);
                }
                case "glob" -> {
                    String pattern = (String) args.get("pattern");
                    String path = (String) args.getOrDefault("dir_path", SandboxWorkspaceLayout.ROOT);
                    yield sandboxExecutionService.glob(uid, fileThreadId, sandboxConfigId, pattern, path);
                }
                default -> "Error: Unknown sandbox action: " + action;
            };

            return ToolResult.success(output, (int)(System.currentTimeMillis() - start));
        } catch (Exception e) {
            return ToolResult.error(e.getMessage(), (int)(System.currentTimeMillis() - start));
        }
    }

    /**
     * F4：从 extensions 取技能运行期凭据 Map（键 {@link SkillsMiddleware#SKILL_ENV_KEY}，
     * SkillsMiddleware 装配阶段写入 runtimeState、AgentEngine 浅拷贝至此）。
     * 缺失/形态不符返回空 Map（无技能链路与旧行为完全一致）。
     */
    private static Map<String, String> skillEnvOf(ToolContext ctx) {
        Object raw = ctx.getExtension(SkillsMiddleware.SKILL_ENV_KEY);
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            Map<String, String> env = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && !key.isBlank()
                        && entry.getValue() instanceof String value) {
                    env.put(key, value);
                }
            }
            return env;
        }
        return Map.of();
    }
}
