package com.fastrag.module.tools.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.tools.entity.*;
import com.fastrag.module.tools.mapper.*;
import com.fastrag.module.tools.mcp.McpProtocolClient;
import com.fastrag.module.tools.service.McpServiceService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.DataScope;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServiceServiceImpl implements McpServiceService {

    private final McpServiceMapper mapper;
    private final McpToolMapper toolMapper;
    private final McpCallLogMapper callLogMapper;

    /** MCP 可见性：系统级（内置/存量） / 属主 / 同组织 / API Token */
    private boolean visible(McpService s, LoginUser user) {
        return DataScope.visible(user, s.getCreator(), s.getOrgId(), s.getIsBuiltin());
    }

    private void requireManage(McpService s) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (s == null) throw BusinessException.notFound("MCP 服务不存在");
        if (!DataScope.manageable(user, s.getCreator())) throw BusinessException.forbidden("无权管理该 MCP 服务");
    }

    @Override
    public List<Map<String, Object>> list(String keyword) {
        LambdaQueryWrapper<McpService> w = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) w.like(McpService::getName, keyword);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!DataScope.isApiToken(user)) {
            w.and(q -> q.eq(McpService::getCreator, user.getUserId())
                    .or(o -> o.eq(McpService::getOrgId, user.getOrgId())
                            .or().eq(McpService::getCreator, "system")
                            .or().eq(McpService::getIsBuiltin, 1)));
        }
        w.orderByDesc(McpService::getCreatedAt);
        return attachToolCounts(mapper.selectList(w));
    }

    @Override
    public List<Map<String, Object>> listEnabled() {
        LambdaQueryWrapper<McpService> w = new LambdaQueryWrapper<McpService>()
                .eq(McpService::getEnabled, 1)
                .orderByDesc(McpService::getCreatedAt);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!DataScope.isApiToken(user)) {
            w.and(q -> q.eq(McpService::getCreator, user.getUserId())
                    .or(o -> o.eq(McpService::getOrgId, user.getOrgId())
                            .or().eq(McpService::getCreator, "system")
                            .or().eq(McpService::getIsBuiltin, 1)));
        }
        return attachToolCounts(mapper.selectList(w));
    }

    @Override
    public List<McpService> listBuiltin() {
        return mapper.selectBuiltin();
    }

    @Override
    public McpService get(String id) {
        McpService svc = mapper.selectById(id);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (svc == null || !visible(svc, user)) throw BusinessException.forbidden("无权访问该 MCP 服务");
        return svc;
    }

    @Override
    public Map<String, Object> getWithTools(String id) {
        McpService svc = mapper.selectById(id);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (svc == null || !visible(svc, user)) throw BusinessException.forbidden("无权访问该 MCP 服务");
        Map<String, Object> result = serviceToMap(svc);
        // 凭据（authValue/env）仅属主可见
        if (!DataScope.manageable(user, svc.getCreator())) {
            result.remove("authValue");
            result.remove("env");
        }
        List<McpTool> tools = listTools(id);
        result.put("toolsList", tools);
        return result;
    }

    @Override
    public McpService getBySlug(String slug) {
        McpService svc = mapper.selectBySlug(slug);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (svc == null || !visible(svc, user)) throw BusinessException.forbidden("无权访问该 MCP 服务");
        return svc;
    }

    @Override
    public McpService create(Map<String, Object> form) {
        LoginUser user = SecurityUtil.getCurrentUser();
        McpService s = new McpService();
        s.setCreator(user.getUserId());
        s.setOrgId(user.getOrgId());
        s.setIsBuiltin(0);
        s.setName((String) form.get("name"));
        s.setSlug((String) form.get("slug"));
        s.setTransport((String) form.getOrDefault("transport", "sse"));
        s.setMcpUrl((String) form.get("mcpUrl"));
        s.setCommand((String) form.get("command"));

        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) form.get("args");
        s.setArgs(args);

        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) form.get("env");
        s.setEnv(env);

        s.setAuthType((String) form.getOrDefault("authType", "none"));
        s.setAuthValue((String) form.get("authValue"));
        s.setEnabled(1);
        s.setStatus("offline");
        s.setCreatedAt(LocalDateTime.now());
        mapper.insert(s);

        // 如果前端传入了工具列表，直接保存（手动添加工具模式）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolsList = (List<Map<String, Object>>) form.get("toolsList");
        if (toolsList != null && !toolsList.isEmpty()) {
            String serviceCamel = toCamelCase(s.getName());
            for (Map<String, Object> toolForm : toolsList) {
                McpTool tool = new McpTool();
                tool.setServiceId(s.getId());
                tool.setName((String) toolForm.get("name"));
                tool.setDescription((String) toolForm.get("description"));
                String rawName = tool.getName() != null ? tool.getName() : "unknown";
                tool.setToolId("mcp__" + serviceCamel + "__" + toCamelCase(rawName));
                @SuppressWarnings("unchecked")
                Map<String, Object> params = normalizeParams(toolForm.get("params"));
                tool.setParams(params);
                tool.setEnabled(1);
                toolMapper.insert(tool);
            }
        }

        return s;
    }

    @Override
    public Map<String, Object> parseUrl(Map<String, Object> form) {
        String transport = (String) form.getOrDefault("transport", "sse");
        String mcpUrl = (String) form.get("mcpUrl");
        String command = (String) form.get("command");

        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) form.get("args");
        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) form.get("env");
        String authType = (String) form.getOrDefault("authType", "none");
        String authValue = (String) form.get("authValue");

        if (mcpUrl == null && command == null) {
            throw new RuntimeException("MCP URL 或 command 至少需要提供一个");
        }

        // 构建认证头
        Map<String, String> headers = new LinkedHashMap<>();
        if (authValue != null && !"none".equals(authType)) {
            if ("bearer".equalsIgnoreCase(authType)) {
                headers.put("Authorization", "Bearer " + authValue);
            } else if ("basic".equalsIgnoreCase(authType)) {
                String encoded = Base64.getEncoder().encodeToString(
                        (authValue).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
            }
        }

        try (McpProtocolClient client = new McpProtocolClient(
                transport, command, args, env, mcpUrl, headers, 30)) {

            client.connect();
            List<McpProtocolClient.McpToolInfo> discovered = client.listTools();

            List<Map<String, Object>> tools = new ArrayList<>();
            for (McpProtocolClient.McpToolInfo t : discovered) {
                Map<String, Object> toolMap = new LinkedHashMap<>();
                toolMap.put("name", t.getName());
                toolMap.put("description", t.getDescription() != null ? t.getDescription() : "");
                toolMap.put("params", t.getInputSchema() != null ? t.getInputSchema() : Map.of());
                tools.add(toolMap);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tools", tools);
            result.put("message", "解析成功，共发现 " + tools.size() + " 个工具");
            return result;

        } catch (Exception e) {
            log.error("[McpService] Parse URL failed: {}", e.getMessage());
            throw new RuntimeException("解析 MCP 地址失败: " + e.getMessage(), e);
        }
    }

    @Override
    public McpService update(String id, Map<String, Object> form) {
        McpService s = mapper.selectById(id);
        requireManage(s);
        if (s != null) {
            if (form.containsKey("name")) s.setName((String) form.get("name"));
            if (form.containsKey("slug")) s.setSlug((String) form.get("slug"));
            if (form.containsKey("mcpUrl")) s.setMcpUrl((String) form.get("mcpUrl"));
            if (form.containsKey("command")) s.setCommand((String) form.get("command"));
            if (form.containsKey("authType")) s.setAuthType((String) form.get("authType"));
            if (form.containsKey("authValue")) s.setAuthValue((String) form.get("authValue"));
            if (form.containsKey("env")) {
                @SuppressWarnings("unchecked")
                Map<String, String> env = (Map<String, String>) form.get("env");
                s.setEnv(env);
            }
            if (form.containsKey("transport")) {
                s.setTransport((String) form.get("transport"));
            }
            s.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(s);

            // 如果前端传入了工具列表，替换现有工具
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolsList = (List<Map<String, Object>>) form.get("toolsList");
            if (toolsList != null) {
                // 先删除旧工具
                toolMapper.delete(new LambdaQueryWrapper<McpTool>().eq(McpTool::getServiceId, id));
                // 再插入新工具
                String serviceCamel = toCamelCase(s.getName());
                for (Map<String, Object> toolForm : toolsList) {
                    McpTool tool = new McpTool();
                    tool.setServiceId(id);
                    tool.setName((String) toolForm.get("name"));
                    tool.setDescription((String) toolForm.get("description"));
                    String rawName = tool.getName() != null ? tool.getName() : "unknown";
                    tool.setToolId("mcp__" + serviceCamel + "__" + toCamelCase(rawName));
                    Map<String, Object> params = normalizeParams(toolForm.get("params"));
                    tool.setParams(params);
                    tool.setEnabled(1);
                    toolMapper.insert(tool);
                }
            }
        }
        return s;
    }

    @Override
    public void delete(String id) {
        McpService s = mapper.selectById(id);
        requireManage(s);
        if (s != null && Integer.valueOf(1).equals(s.getIsBuiltin())) {
            throw new RuntimeException("内置 MCP 服务不允许删除");
        }
        mapper.deleteById(id);
    }

    @Override
    public void toggleEnabled(String id) {
        McpService s = mapper.selectById(id);
        requireManage(s);
        if (s != null) {
            s.setEnabled(s.getEnabled() == 1 ? 0 : 1);
            mapper.updateById(s);
        }
    }

    @Override
    public void toggleTool(Long toolId) {
        McpTool tool = toolMapper.selectById(toolId);
        if (tool != null) {
            tool.setEnabled(tool.getEnabled() == 1 ? 0 : 1);
            toolMapper.updateById(tool);
        }
    }

    @Override
    public List<McpTool> listTools(String serviceId) {
        return toolMapper.selectList(
                new LambdaQueryWrapper<McpTool>().eq(McpTool::getServiceId, serviceId)
        );
    }

    @Override
    public boolean existsBySlug(String slug) {
        return mapper.existsBySlug(slug);
    }

    @Override
    public McpService refresh(String id) {
        McpService service = mapper.selectById(id);
        if (service == null) {
            throw new RuntimeException("MCP service not found: " + id);
        }

        log.info("[McpService] Refreshing service: {} ({})", service.getName(), id);

        // 构建认证头
        Map<String, String> headers = new LinkedHashMap<>();
        String authType = service.getAuthType();
        String authValue = service.getAuthValue();
        if (authType != null && authValue != null && !"none".equals(authType)) {
            if ("bearer".equalsIgnoreCase(authType)) {
                headers.put("Authorization", "Bearer " + authValue);
            } else if ("basic".equalsIgnoreCase(authType)) {
                String encoded = Base64.getEncoder().encodeToString(
                        (authValue).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
            }
        }

        try (McpProtocolClient client = new McpProtocolClient(
                service.getTransport() != null ? service.getTransport() : "sse",
                service.getCommand(),
                service.getArgs(),
                service.getEnv(),
                service.getMcpUrl(),
                headers,
                15 // refresh timeout 15s
        )) {
            // 连接并握手
            client.connect();

            // 调用 tools/list 发现工具
            List<McpProtocolClient.McpToolInfo> discoveredTools = client.listTools();

            log.info("[McpService] Discovered {} tools from service '{}'", discoveredTools.size(), service.getName());

            // 删除旧工具
            toolMapper.delete(new LambdaQueryWrapper<McpTool>().eq(McpTool::getServiceId, id));

            // 插入新工具
            String serviceCamel = toCamelCase(service.getName());
            for (McpProtocolClient.McpToolInfo toolInfo : discoveredTools) {
                McpTool tool = new McpTool();
                tool.setServiceId(id);
                tool.setName(toolInfo.getName());
                tool.setDescription(toolInfo.getDescription() != null ? toolInfo.getDescription() : "");
                tool.setToolId("mcp__" + serviceCamel + "__" + toCamelCase(toolInfo.getName()));
                tool.setParams(toolInfo.getInputSchema());
                tool.setEnabled(1);
                toolMapper.insert(tool);
            }

            // 更新服务状态为在线
            service.setStatus("online");
            service.setLastUsed(LocalDateTime.now());
            service.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(service);

            log.info("[McpService] Refresh successful: service={}, tools={}", service.getName(), discoveredTools.size());

        } catch (Exception e) {
            log.error("[McpService] Refresh failed: service={}, error={}", service.getName(), e.getMessage());
            service.setStatus("error");
            service.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(service);
            throw new RuntimeException("MCP service refresh failed: " + e.getMessage(), e);
        }

        return mapper.selectById(id);
    }

    @Override
    public McpTool addTool(String serviceId, Map<String, Object> form) {
        McpService service = mapper.selectById(serviceId);
        if (service == null) {
            throw new RuntimeException("MCP service not found: " + serviceId);
        }

        McpTool tool = new McpTool();
        tool.setServiceId(serviceId);
        tool.setName((String) form.get("name"));
        tool.setDescription((String) form.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> params = normalizeParams(form.get("params"));
        tool.setParams(params);

        String rawName = tool.getName() != null ? tool.getName() : "unknown";
        String serviceCamel = toCamelCase(service.getName());
        tool.setToolId("mcp__" + serviceCamel + "__" + toCamelCase(rawName));
        tool.setEnabled(1);
        toolMapper.insert(tool);
        return tool;
    }

    @Override
    public McpTool updateTool(Long toolId, Map<String, Object> form) {
        McpTool tool = toolMapper.selectById(toolId);
        if (tool == null) {
            throw new RuntimeException("MCP tool not found: " + toolId);
        }

        if (form.containsKey("name")) tool.setName((String) form.get("name"));
        if (form.containsKey("description")) tool.setDescription((String) form.get("description"));
        if (form.containsKey("enabled")) {
            Object v = form.get("enabled");
            tool.setEnabled(v instanceof Integer ? (Integer) v : (v instanceof Boolean ? (((Boolean) v) ? 1 : 0) : tool.getEnabled()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> params = normalizeParams(form.get("params"));
        if (params != null) tool.setParams(params);

        // 如果名称变了，更新 toolId
        if (form.containsKey("name") && tool.getServiceId() != null) {
            McpService service = mapper.selectById(tool.getServiceId());
            if (service != null) {
                String serviceCamel = toCamelCase(service.getName());
                tool.setToolId("mcp__" + serviceCamel + "__" + toCamelCase(tool.getName()));
            }
        }

        toolMapper.updateById(tool);
        return toolMapper.selectById(toolId);
    }

    @Override
    public void deleteTool(Long toolId) {
        toolMapper.deleteById(toolId);
    }

    @Override
    public Map<String, Object> testTool(Long toolId, Map<String, Object> arguments) {
        McpTool tool = toolMapper.selectById(toolId);
        if (tool == null) {
            throw new RuntimeException("MCP 工具不存在: " + toolId);
        }

        McpService service = mapper.selectById(tool.getServiceId());
        if (service == null) {
            throw new RuntimeException("MCP 服务不存在: " + tool.getServiceId());
        }

        log.info("[McpService] Testing tool: {} on service: {}", tool.getName(), service.getName());

        // 构建认证头
        Map<String, String> headers = new LinkedHashMap<>();
        String authType = service.getAuthType();
        String authValue = service.getAuthValue();
        if (authValue != null && !"none".equals(authType)) {
            if ("bearer".equalsIgnoreCase(authType)) {
                headers.put("Authorization", "Bearer " + authValue);
            } else if ("basic".equalsIgnoreCase(authType)) {
                String encoded = Base64.getEncoder().encodeToString(
                        (authValue).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
            }
        }

        long t0 = System.currentTimeMillis();
        try (McpProtocolClient client = new McpProtocolClient(
                service.getTransport() != null ? service.getTransport() : "sse",
                service.getCommand(),
                service.getArgs(),
                service.getEnv(),
                service.getMcpUrl(),
                headers,
                30)) {

            client.connect();

            String output = client.callTool(tool.getName(), arguments);
            long elapsed = System.currentTimeMillis() - t0;

            // 更新最近使用时间
            service.setLastUsed(LocalDateTime.now());
            mapper.updateById(service);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("output", output);
            result.put("durationMs", elapsed);
            log.info("[McpService] Tool test completed: tool={}, duration={}ms, outputLen={}",
                    tool.getName(), elapsed, output.length());
            return result;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("[McpService] Tool test failed: tool={}, error={}", tool.getName(), e.getMessage());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("output", e.getMessage());
            result.put("durationMs", elapsed);
            return result;
        }
    }

    /**
     * 将 McpService 列表转换为带工具数量的 Map 列表。
     */
    private List<Map<String, Object>> attachToolCounts(List<McpService> services) {
        if (services == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (McpService s : services) {
            Map<String, Object> map = serviceToMap(s);
            // 查询工具数量
            Long count = toolMapper.selectCount(
                    new LambdaQueryWrapper<McpTool>().eq(McpTool::getServiceId, s.getId()));
            map.put("toolCount", count != null ? count.intValue() : 0);
            map.put("toolsList", List.of()); // 列表不返回完整工具，前端用 toolCount
            // 列表凭据脱敏：authValue/env 仅属主可见
            LoginUser user = SecurityUtil.getCurrentUser();
            if (!DataScope.manageable(user, s.getCreator())) {
                map.remove("authValue");
                map.remove("env");
            }
            result.add(map);
        }
        return result;
    }

    /**
     * 将 McpService 转为 Map，用于 API 响应。
     */
    private Map<String, Object> serviceToMap(McpService s) {
        if (s == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("slug", s.getSlug());
        map.put("name", s.getName());
        map.put("transport", s.getTransport());
        map.put("mcpUrl", s.getMcpUrl());
        map.put("command", s.getCommand());
        map.put("args", s.getArgs());
        map.put("env", s.getEnv());
        map.put("authType", s.getAuthType());
        map.put("authValue", s.getAuthValue());
        map.put("status", s.getStatus());
        map.put("enabled", s.getEnabled());
        map.put("isBuiltin", s.getIsBuiltin());
        map.put("configHash", s.getConfigHash());
        map.put("metadata", s.getMetadata());
        map.put("lastUsed", s.getLastUsed());
        map.put("createdAt", s.getCreatedAt());
        map.put("updatedAt", s.getUpdatedAt());
        return map;
    }

    /**
     * 将前端传入的 params 格式统一转换为 JSON Schema Map 格式。
     * <p>
     * 前端格式（数组）：
     * <pre>[{"name":"query","type":"string","description":"关键词","required":true}]</pre>
     * 转换为：
     * <pre>{"type":"object","properties":{"query":{"type":"string","description":"关键词"}},"required":["query"]}</pre>
     * <p>
     * 如果 params 已经是 Map（JSON Schema），直接返回。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeParams(Object rawParams) {
        if (rawParams == null) return null;

        // 已经是 Map（JSON Schema 格式），直接返回
        if (rawParams instanceof Map) {
            return (Map<String, Object>) rawParams;
        }

        // 数组格式（前端手动添加工具时），转换为 JSON Schema
        if (rawParams instanceof List) {
            List<Map<String, Object>> paramList = (List<Map<String, Object>>) rawParams;
            if (paramList.isEmpty()) return null;

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            for (Map<String, Object> p : paramList) {
                String name = (String) p.get("name");
                if (name == null) continue;

                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", p.getOrDefault("type", "string"));
                if (p.containsKey("description") && p.get("description") != null) {
                    prop.put("description", p.get("description"));
                }
                properties.put(name, prop);

                if (Boolean.TRUE.equals(p.get("required")) || "true".equals(p.get("required"))) {
                    required.add(name);
                }
            }

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
            return schema;
        }

        return null;
    }

    /**
     * 将字符串转换为 CamelCase。
     * 例如: "bing_search" → "BingSearch", "my-tool" → "MyTool", "hello world" → "HelloWorld"
     */
    private String toCamelCase(String str) {
        if (str == null || str.isBlank()) return "";
        String[] parts = str.split("[\\s_\\-./:]+");
        return Arrays.stream(parts)
                .filter(p -> !p.isEmpty())
                .map(p -> Character.toUpperCase(p.charAt(0)) + (p.length() > 1 ? p.substring(1) : ""))
                .collect(Collectors.joining());
    }
}
