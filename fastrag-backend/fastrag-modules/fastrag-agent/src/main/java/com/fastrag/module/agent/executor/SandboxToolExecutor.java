package com.fastrag.module.agent.executor;

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
                    yield sandboxExecutionService.execute(uid, fileThreadId, sandboxConfigId, command, timeout, cwd);
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
}
