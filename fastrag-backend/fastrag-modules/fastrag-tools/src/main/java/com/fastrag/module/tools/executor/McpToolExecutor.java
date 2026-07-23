package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.entity.McpCallLog;
import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.mapper.McpCallLogMapper;
import com.fastrag.module.tools.mapper.McpServiceMapper;
import com.fastrag.module.tools.mcp.McpProtocolClient;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * MCP 工具执行器。
 * <p>
 * 根据 ToolDefinition.config 中的 transport 选择 stdio 或 sse 模式，
 * 使用 McpProtocolClient 执行 MCP tools/call，记录调用日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolExecutor implements ToolExecutor {

    private final McpCallLogMapper callLogMapper;
    private final McpServiceMapper serviceMapper;

    @Override
    public String getType() {
        return "mcp";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolDefinition tool, Map<String, Object> arguments, ToolContext ctx) {
        long t0 = System.currentTimeMillis();
        Map<String, Object> config = tool.getConfig();
        if (config == null) {
            return fail("MCP tool has no config");
        }

        String transport = getStr(config, "transport");
        String mcpUrl = getStr(config, "mcpUrl");
        String command = getStr(config, "command");
        List<String> args = (List<String>) config.get("args");
        Map<String, String> env = (Map<String, String>) config.get("env");
        String serviceId = getStr(config, "mcpServiceId");
        // 优先使用原始工具名（rawName）调用 MCP Server，兼容 ToolDefinition.name 被改为格式化 ID 的情况
        String rawName = getStr(config, "rawName");
        String toolName = rawName != null ? rawName : tool.getName();

        if (transport == null) transport = "stdio";
        log.info("[McpTool] Executing: tool={}, transport={}, service={}", toolName, transport, serviceId);

        // 设定默认超时 30s
        Map<String, String> headers = new LinkedHashMap<>();
        if (config.containsKey("authType") && !"none".equals(config.get("authType"))) {
            String authType = getStr(config, "authType");
            String authValue = getStr(config, "authValue");
            if ("bearer".equalsIgnoreCase(authType) || "Bearer".equals(authType)) {
                headers.put("Authorization", "Bearer " + authValue);
            } else if ("basic".equalsIgnoreCase(authType)) {
                String encoded = Base64.getEncoder().encodeToString((authValue).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
            }
        }

        try (McpProtocolClient client = new McpProtocolClient(
                transport, command, args, env, mcpUrl, headers, 30)) {

            // 建立连接（initialize 握手）
            client.connect();
            log.debug("[McpTool] Connected to MCP server for tool: {}", toolName);

            // 调用工具（SDK 内部处理 JSON-RPC，返回文本结果）
            String output = client.callTool(toolName, arguments);
            int duration = (int) (System.currentTimeMillis() - t0);

            log.info("[McpTool] Success: tool={}, duration={}ms, outputLen={}", toolName, duration, output.length());

            // 更新服务最近使用时间
            updateLastUsed(serviceId);

            // 记录调用日志
            saveCallLog(serviceId, ctx, toolName, "success", duration);

            ToolResult tr = new ToolResult();
            tr.setSuccess(true);
            tr.setOutput(output);
            tr.setDurationMs(duration);
            return tr;

        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - t0);
            log.error("[McpTool] Failed: tool={}, error={}", toolName, e.getMessage(), e);

            // 记录失败的调用日志
            saveCallLog(serviceId, ctx, toolName, "error", duration);

            return fail(e.getMessage(), duration);
        }
    }

    private void saveCallLog(String serviceId, ToolContext ctx, String toolName, String status, int duration) {
        try {
            if (serviceId == null) return;
            McpCallLog logEntry = new McpCallLog();
            logEntry.setServiceId(serviceId);
            logEntry.setCaller(ctx != null ? ctx.getAppId() : "unknown");
            logEntry.setTool(toolName);
            logEntry.setStatus(status);
            logEntry.setDuration(duration);
            logEntry.setTimestamp(LocalDateTime.now());
            callLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("[McpTool] Failed to save call log: {}", e.getMessage());
        }
    }

    private void updateLastUsed(String serviceId) {
        try {
            if (serviceId == null) return;
            McpService svc = serviceMapper.selectById(serviceId);
            if (svc != null) {
                svc.setLastUsed(LocalDateTime.now());
                serviceMapper.updateById(svc);
            }
        } catch (Exception e) {
            log.warn("[McpTool] Failed to update lastUsed: {}", e.getMessage());
        }
    }

    private ToolResult fail(String error) {
        return fail(error, 0);
    }

    private ToolResult fail(String error, int durationMs) {
        ToolResult r = new ToolResult();
        r.setSuccess(false);
        r.setError(error);
        r.setDurationMs(durationMs);
        return r;
    }

    private String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
