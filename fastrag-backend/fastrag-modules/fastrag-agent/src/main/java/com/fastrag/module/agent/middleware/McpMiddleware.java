package com.fastrag.module.agent.middleware;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.entity.McpTool;
import com.fastrag.module.tools.mapper.McpServiceMapper;
import com.fastrag.module.tools.mapper.McpToolMapper;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 中间件。将应用绑定的 MCP Server 中已发现的工具注册到 LLM 工具列表。
 *
 * <p>执行顺序: Order=11（在 DatabaseMiddleware 之后执行）。</p>
 *
 * <p>当 {@link BaseContext#getMcps()} 非空时，查询 {@code mcp_tool} 表
 * 获取每个 MCP Server 下所有已启用（enabled=1）的工具，构建
 * {@link ToolDefinition}{@code type="mcp"} 注册到工具列表中，
 * 供 LLM 在 function calling 中自主选择调用。</p>
 *
 * <p>前置条件：MCP Server 需要先执行过刷新（{@code tools/list}）
 * 或手动添加过工具，确保 {@code mcp_tool} 表中有记录。</p>
 *
 * <p>与 {@link ToolRegistry} 的协作：本中间件注册的 ToolDefinition 使用
 * 与 {@code ToolRegistry.resolveMcpTool()} 一致的 config 格式，
 * 确保 {@link com.fastrag.module.tools.executor.McpToolExecutor}
 * 能正确读取连接参数并执行调用。</p>
 *
 * @see KnowledgeBaseMiddleware 类似模式：按 Server 绑定注册工具
 * @see DatabaseMiddleware 类似模式：按 Server 绑定注册工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpMiddleware implements AgentMiddleware {

    private final McpToolMapper mcpToolMapper;
    private final McpServiceMapper mcpServiceMapper;

    @Override
    public int getOrder() {
        return 11;
    }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                       List<ChatMessage> messages,
                                       List<ToolDefinition> tools) {
        // 没有绑定 MCP Server → 跳过
        List<String> serviceIds = context.getMcps();
        if (serviceIds == null || serviceIds.isEmpty()) {
            return context;
        }

        try {
            log.info("[McpMiddleware] Registering MCP tools from {} MCP services", serviceIds.size());

            // 查询所有启用的 McpTool（属于绑定的 MCP Server）
            List<McpTool> mcpTools = mcpToolMapper.selectList(
                    new LambdaQueryWrapper<McpTool>()
                            .in(McpTool::getServiceId, serviceIds)
                            .eq(McpTool::getEnabled, 1));

            if (mcpTools.isEmpty()) {
                log.warn("[McpMiddleware] No enabled tools found for MCP services: {}." +
                        " Please refresh the MCP server(s) or add tools manually.", serviceIds);
                return context;
            }

            // 缓存 McpService 避免重复查询
            Map<String, McpService> serviceCache = new HashMap<>();

            int registeredCount = 0;
            for (McpTool mcpTool : mcpTools) {
                // 加载所属 McpService（带缓存）
                McpService service = serviceCache.get(mcpTool.getServiceId());
                if (service == null) {
                    service = mcpServiceMapper.selectById(mcpTool.getServiceId());
                    if (service != null) {
                        serviceCache.put(mcpTool.getServiceId(), service);
                    }
                }
                if (service == null) {
                    log.warn("[McpMiddleware] McpService not found for tool: {} (serviceId={})",
                            mcpTool.getName(), mcpTool.getServiceId());
                    continue;
                }

                // 去重：如果已有同名工具（来自 AppToolBinding 手动绑定），跳过
                if (tools.stream().anyMatch(t -> mcpTool.getName().equals(t.getName()))) {
                    log.debug("[McpMiddleware] Skipping duplicate tool: {}", mcpTool.getName());
                    continue;
                }

                // 构建 ToolDefinition（config 格式与 ToolRegistry.resolveMcpTool() 一致）
                Map<String, Object> config = new HashMap<>();
                config.put("mcpServiceId", service.getId());
                config.put("mcpServiceName", service.getName());
                config.put("mcpToolId", mcpTool.getId());
                config.put("transport", service.getTransport() != null ? service.getTransport() : "sse");
                config.put("mcpUrl", service.getMcpUrl() != null ? service.getMcpUrl() : "");
                config.put("command", service.getCommand());
                config.put("args", service.getArgs());
                config.put("env", service.getEnv());
                config.put("authType", service.getAuthType() != null ? service.getAuthType() : "none");
                config.put("authValue", service.getAuthValue());
                config.put("rawName", mcpTool.getName());

                ToolDefinition def = ToolDefinition.builder()
                        .toolId(mcpTool.getToolId() != null
                                ? mcpTool.getToolId() : "mcp_" + mcpTool.getId())
                        .name(mcpTool.getToolId() != null
                                ? mcpTool.getToolId() : mcpTool.getName())
                        .description("[MCP:" + service.getName() + "] "
                                + (mcpTool.getDescription() != null ? mcpTool.getDescription() : ""))
                        .type("mcp")
                        .inputSchema(mcpTool.getParams() != null
                                ? mcpTool.getParams() : Map.of("type", "object", "properties", Map.of()))
                        .config(config)
                        .build();

                tools.add(def);
                registeredCount++;
            }

            log.info("[McpMiddleware] Registered {} MCP tools from {} services (total tools now: {})",
                    registeredCount, serviceIds.size(), tools.size());

        } catch (Exception e) {
            log.error("[McpMiddleware] Failed to register MCP tools: {}", e.getMessage(), e);
        }

        return context;
    }

    @Override
    public BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        // MCP 中间件不需要后置处理
        return context;
    }

    @Override
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                 ChatMessage.ToolCall toolCall) {
        // MCP 工具调用由 McpToolExecutor 处理，无需拦截
        return null;
    }
}
