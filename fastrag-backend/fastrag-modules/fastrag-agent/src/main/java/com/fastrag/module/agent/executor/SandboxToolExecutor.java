package com.fastrag.module.agent.executor;

import com.fastrag.module.agent.sandbox.SandboxBackend;
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
 * <p>type = "sandbox"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxToolExecutor implements ToolExecutor {

    private final SandboxBackend sandboxBackend;

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
        String fileThreadId = ctx.getExtension("fileThreadId") != null
                ? (String) ctx.getExtension("fileThreadId") : uid;

        try {
            String output = switch (action) {
                case "read_file" -> {
                    String path = (String) args.get("file_path");
                    int offset = args.containsKey("offset")
                            ? ((Number) args.get("offset")).intValue() : 0;
                    int limit = args.containsKey("limit")
                            ? ((Number) args.get("limit")).intValue() : 5000;
                    yield sandboxBackend.readFile(uid, fileThreadId, path, offset, limit);
                }
                case "write_file" -> {
                    String path = (String) args.get("file_path");
                    String content = (String) args.get("content");
                    yield sandboxBackend.writeFile(uid, fileThreadId, path, content);
                }
                case "edit_file" -> {
                    String path = (String) args.get("file_path");
                    String oldStr = (String) args.get("old_string");
                    String newStr = (String) args.get("new_string");
                    boolean replaceAll = Boolean.TRUE.equals(args.get("replace_all"));
                    yield sandboxBackend.editFile(uid, fileThreadId, path, oldStr, newStr, replaceAll);
                }
                case "execute" -> {
                    String command = (String) args.get("command");
                    int timeout = args.containsKey("timeout")
                            ? ((Number) args.get("timeout")).intValue() : 120;
                    String cwd = (String) args.get("cwd");
                    yield sandboxBackend.execute(uid, fileThreadId, command, timeout, cwd);
                }
                case "list_directory" -> {
                    String path = (String) args.getOrDefault("dir_path", "/home/gem/user-data");
                    yield sandboxBackend.listDirectory(uid, fileThreadId, path);
                }
                case "grep" -> {
                    String pattern = (String) args.get("pattern");
                    String path = (String) args.getOrDefault("dir_path", "/home/gem/user-data");
                    String glob = (String) args.get("glob");
                    yield sandboxBackend.grep(uid, fileThreadId, pattern, path, glob);
                }
                case "glob" -> {
                    String pattern = (String) args.get("pattern");
                    String path = (String) args.getOrDefault("dir_path", "/home/gem/user-data");
                    yield sandboxBackend.glob(uid, fileThreadId, pattern, path);
                }
                default -> "Error: Unknown sandbox action: " + action;
            };

            return ToolResult.success(output, (int)(System.currentTimeMillis() - start));
        } catch (Exception e) {
            return ToolResult.error(e.getMessage(), (int)(System.currentTimeMillis() - start));
        }
    }
}
