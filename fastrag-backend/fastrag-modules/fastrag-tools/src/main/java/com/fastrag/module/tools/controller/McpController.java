package com.fastrag.module.tools.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.service.McpServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

@RestController
@RequestMapping("/api/mcp-services")
@RequiredArgsConstructor
public class McpController {
    private final McpServiceService svc;

    /** 列出所有 MCP 服务 */
    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(svc.list(keyword));
    }

    /** 列出已启用的 MCP 服务 */
    @GetMapping("/enabled")
    public ApiResponse<?> listEnabled() {
        return ApiResponse.success(svc.listEnabled());
    }

    /** 列出内置 MCP 服务 */
    @GetMapping("/builtin")
    public ApiResponse<?> listBuiltin() {
        return ApiResponse.success(svc.listBuiltin());
    }

    /** 获取单个 MCP 服务（包含工具列表） */
    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        return ApiResponse.success(svc.getWithTools(id));
    }

    /** 根据 slug 获取 MCP 服务 */
    @GetMapping("/slug/{slug}")
    public ApiResponse<?> getBySlug(@PathVariable String slug) {
        return ApiResponse.success(svc.getBySlug(slug));
    }

    /** 创建 MCP 服务 */
    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> form) {
        McpService created = svc.create(form);
        return ApiResponse.success(svc.getWithTools(created.getId()));
    }

    /** 解析 MCP URL：连接服务器发现工具，不持久化，用于创建前的「解析」按钮 */
    @PostMapping("/parse-url")
    public ApiResponse<?> parseUrl(@RequestBody Map<String, Object> form) {
        return ApiResponse.success(svc.parseUrl(form));
    }

    /** 更新 MCP 服务 */
    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Map<String, Object> form) {
        svc.update(id, form);
        return ApiResponse.success(svc.getWithTools(id));
    }

    /** 删除 MCP 服务 */
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        svc.delete(id);
        return ApiResponse.success();
    }

    /** 切换 MCP 服务启用状态 */
    @PostMapping("/{id}/toggle")
    public ApiResponse<?> toggle(@PathVariable String id) {
        svc.toggleEnabled(id);
        return ApiResponse.success();
    }

    /** 切换 MCP 工具启用状态 */
    @PostMapping("/tools/{toolId}/toggle")
    public ApiResponse<?> toggleTool(@PathVariable Long toolId) {
        svc.toggleTool(toolId);
        return ApiResponse.success();
    }

    /** 获取 MCP 服务的工具列表 */
    @GetMapping("/{id}/tools")
    public ApiResponse<?> tools(@PathVariable String id) {
        return ApiResponse.success(svc.listTools(id));
    }

    /** 刷新 MCP 服务：连接服务器、发现工具、更新状态 */
    @PostMapping("/{id}/refresh")
    public ApiResponse<?> refresh(@PathVariable String id) {
        return ApiResponse.success(svc.refresh(id));
    }

    /** 手动添加工具到 MCP 服务 */
    @PostMapping("/{serviceId}/tools")
    public ApiResponse<?> addTool(@PathVariable String serviceId, @RequestBody Map<String, Object> form) {
        return ApiResponse.success(svc.addTool(serviceId, form));
    }

    /** 手动更新 MCP 工具 */
    @PutMapping("/tools/{toolId}")
    public ApiResponse<?> updateTool(@PathVariable Long toolId, @RequestBody Map<String, Object> form) {
        return ApiResponse.success(svc.updateTool(toolId, form));
    }

    /** 手动删除 MCP 工具 */
    @DeleteMapping("/tools/{toolId}")
    public ApiResponse<?> deleteTool(@PathVariable Long toolId) {
        svc.deleteTool(toolId);
        return ApiResponse.success();
    }

    /** 测试 MCP 工具调用：连接服务器，执行 tools/call，返回结果 */
    @PostMapping("/tools/{toolId}/test")
    public ApiResponse<?> testTool(@PathVariable Long toolId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) body.getOrDefault("arguments", Map.of());
        return ApiResponse.success(svc.testTool(toolId, args));
    }
}
