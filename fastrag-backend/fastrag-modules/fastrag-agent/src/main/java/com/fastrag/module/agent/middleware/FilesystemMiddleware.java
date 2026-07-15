package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.sandbox.SandboxBackend;
import com.fastrag.module.agent.sandbox.SandboxService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件系统中间件。注册文件系统相关工具并路由到 SandboxBackend。
 *
 * <p>执行顺序: Order=1。</p>
 *
 * <p>当 SandboxService 不可用时，仅注册工具定义但实际文件操作返回错误。</p>
 */
@Slf4j
@Component
public class FilesystemMiddleware implements AgentMiddleware {

    @Autowired(required = false)
    private SandboxService sandboxService;

    @Autowired(required = false)
    private SandboxBackend sandboxBackend;

    /** 文件操作工具名称集合 */
    private static final Set<String> FS_TOOL_NAMES = Set.of(
            "read_file", "write_file", "edit_file", "execute",
            "list_directory", "grep", "glob", "delete_file",
            "create_directory", "search_files", "get_file_info");

    @Override
    public int getOrder() { return 1; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        boolean sandboxAvailable = sandboxService != null && sandboxService.isSandboxEnabled();

        log.info("[FilesystemMiddleware] Registering filesystem tools, sandboxAvailable={}", sandboxAvailable);

        // read_file
        tools.add(ToolDefinition.builder()
                .toolId("builtin_read_file")
                .name("read_file")
                .description("读取文件内容。返回文件的完整文本内容。")
                .type("sandbox")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file_path", Map.of("type", "string", "description", "文件的绝对路径")
                        ),
                        "required", List.of("file_path")
                ))
                .config(Map.of("action", "read_file"))
                .build());

        // write_file
        tools.add(ToolDefinition.builder()
                .toolId("builtin_write_file")
                .name("write_file")
                .description("写入文件内容。如果文件已存在则覆盖，不存在则创建。")
                .type("sandbox")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file_path", Map.of("type", "string", "description", "文件的绝对路径"),
                                "content", Map.of("type", "string", "description", "要写入的内容")
                        ),
                        "required", List.of("file_path", "content")
                ))
                .config(Map.of("action", "write_file"))
                .build());

        // edit_file
        tools.add(ToolDefinition.builder()
                .toolId("builtin_edit_file")
                .name("edit_file")
                .description("编辑文件内容（str_replace 模式）。将文件中的 old_string 替换为 new_string。")
                .type("sandbox")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file_path", Map.of("type", "string", "description", "文件的绝对路径"),
                                "old_string", Map.of("type", "string", "description", "要被替换的旧文本"),
                                "new_string", Map.of("type", "string", "description", "替换后的新文本"),
                                "replace_all", Map.of("type", "boolean", "description", "是否替换所有匹配", "default", false)
                        ),
                        "required", List.of("file_path", "old_string", "new_string")
                ))
                .config(Map.of("action", "edit_file"))
                .build());

        // execute
        tools.add(ToolDefinition.builder()
                .toolId("builtin_execute")
                .name("execute")
                .description("在沙箱环境中执行 Shell 命令。返回命令的标准输出。")
                .type("sandbox")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of("type", "string", "description", "要执行的 Shell 命令"),
                                "timeout", Map.of("type", "integer", "description", "超时时间（秒）", "default", 120),
                                "cwd", Map.of("type", "string", "description", "工作目录（可选）")
                        ),
                        "required", List.of("command")
                ))
                .config(Map.of("action", "execute"))
                .build());

        // list_directory
        tools.add(ToolDefinition.builder()
                .toolId("builtin_list_directory")
                .name("list_directory")
                .description("列出目录中的文件和子目录。")
                .type("sandbox")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "dir_path", Map.of("type", "string", "description", "目录的绝对路径")
                        ),
                        "required", List.of("dir_path")
                ))
                .config(Map.of("action", "list_directory"))
                .build());

        // grep
        tools.add(ToolDefinition.builder()
                .toolId("builtin_grep")
                .name("grep")
                .description("在文件中搜索匹配指定模式的内容。")
                .type("sandbox")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "pattern", Map.of("type", "string", "description", "要搜索的模式（支持正则）"),
                                "dir_path", Map.of("type", "string", "description", "搜索的目录路径"),
                                "glob", Map.of("type", "string", "description", "文件匹配模式（可选，如 *.py）")
                        ),
                        "required", List.of("pattern")
                ))
                .config(Map.of("action", "grep"))
                .build());

        // glob
        tools.add(ToolDefinition.builder()
                .toolId("builtin_glob")
                .name("glob")
                .description("按文件模式匹配查找文件。")
                .type("sandbox")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "pattern", Map.of("type", "string", "description", "文件匹配模式（如 *.txt, **/*.md）"),
                                "dir_path", Map.of("type", "string", "description", "搜索的目录路径")
                        ),
                        "required", List.of("pattern")
                ))
                .config(Map.of("action", "glob"))
                .build());

        log.info("[FilesystemMiddleware] Registered 7 filesystem tools");
        return context;
    }

    @Override
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                   ChatMessage.ToolCall toolCall) {
        String funcName = toolCall.getFunction().getName();
        if (!FS_TOOL_NAMES.contains(funcName)) {
            return null;
        }

        // 沙箱不可用，让工具执行器返回错误
        if (sandboxBackend == null || sandboxService == null || !sandboxService.isSandboxEnabled()) {
            log.warn("[FilesystemMiddleware] Sandbox not available, cannot handle: {}", funcName);
            return null;
        }

        // 拦截文件操作工具，设置 fileThreadId 到 ToolContext 的 extensions
        // 实际执行由 SandboxToolExecutor 完成
        log.debug("[FilesystemMiddleware] Intercepted filesystem tool: {} (routing to sandbox)", funcName);
        return null; // 返回 null 表示不阻断，让默认的 SandboxToolExecutor 处理
    }

    @Override
    public BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        return context;
    }
}
