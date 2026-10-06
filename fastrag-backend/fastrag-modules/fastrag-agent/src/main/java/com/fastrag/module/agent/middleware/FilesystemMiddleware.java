package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.context.ChatBotContext;
import com.fastrag.module.agent.sandbox.SandboxExecutionService;
import com.fastrag.module.agent.sandbox.SandboxWorkspaceLayout;
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
 * 文件系统中间件。注册文件系统相关工具并路由到 SandboxExecutionService（执行门面）。
 *
 * <p>执行顺序: Order=1。</p>
 *
 * <p>REQ-H03（降级三分支）：工具定义<b>恒注册</b>——沙箱不可用不阻断运行，实际文件与命令
 * 操作由执行门面返回区分三因（总开关关 / 应用未引用或配置禁用 / provider 连接失败）的
 * 「Error: …」文本回灌 LLM；工具描述中的路径提示由 {@link SandboxWorkspaceLayout#hint()} 生成。</p>
 *
 * <p>本中间件同时把沙箱运行面坐标写入 runtimeState（fileThreadId / sandboxConfigId），
 * 引擎构建 ToolContext 时透传为扩展属性，供 {@code SandboxToolExecutor} 委派门面使用。</p>
 */
@Slf4j
@Component
public class FilesystemMiddleware implements AgentMiddleware {

    @Autowired(required = false)
    private SandboxExecutionService sandboxExecutionService;

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
        // 沙箱运行面坐标（会话键 + 应用引用的沙箱配置 ID）写入 runtimeState，
        // AgentEngine 构建 ToolContext 时整表透传为扩展属性
        context.getRuntimeState().put("fileThreadId", sessionIdOf(context));
        context.getRuntimeState().put("sandboxConfigId", sandboxConfigIdOf(context));

        String pathHint = SandboxWorkspaceLayout.hint();
        log.info("[FilesystemMiddleware] Registering filesystem tools, pathHint={}", pathHint);

        // read_file
        tools.add(ToolDefinition.builder()
                .toolId("builtin_read_file")
                .name("read_file")
                .description("读取文件内容。返回文件的完整文本内容。" + pathHint)
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
                .description("写入文件内容。如果文件已存在则覆盖，不存在则创建。" + pathHint)
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
                .description("编辑文件内容（str_replace 模式）。将文件中的 old_string 替换为 new_string。" + pathHint)
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
                .description("在沙箱环境中执行 Shell 命令并返回标准输出。可用于运行代码：先用 write_file 把代码写入沙箱"
                        + "（路径须在 /workspace 下），再执行如 `python3 /workspace/main.py`（Node.js 等其他语言同理）。" + pathHint)
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
                .description("列出目录中的文件和子目录。" + pathHint)
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
                .description("在文件中搜索匹配指定模式的内容。" + pathHint)
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
                .description("按文件模式匹配查找文件。" + pathHint)
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
        // 恒不拦截：工具定义已注册，可用性由执行门面在动作执行时判定（REQ-H03 降级文本）
        String funcName = toolCall.getFunction().getName();
        if (FS_TOOL_NAMES.contains(funcName)) {
            log.debug("[FilesystemMiddleware] Filesystem tool call: {} (routing to sandbox executor)", funcName);
        }
        return null;
    }

    @Override
    public BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        return context;
    }

    /** 会话键：fileThreadId 扩展优先，回落 threadId → runId → uid（与执行器旧约定一致）。 */
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

    /** 沙箱配置 ID：runtimeState 已有值优先；ChatBotContext 携带 @ConfigField sandboxConfigId（REQ-H03 分支②载体）。 */
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
}
