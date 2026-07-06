package com.fastrag.module.tools.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.tools.service.McpServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    /** 获取单个 MCP 服务 */
    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        return ApiResponse.success(svc.get(id));
    }

    /** 根据 slug 获取 MCP 服务 */
    @GetMapping("/slug/{slug}")
    public ApiResponse<?> getBySlug(@PathVariable String slug) {
        return ApiResponse.success(svc.getBySlug(slug));
    }

    /** 创建 MCP 服务 */
    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> form) {
        return ApiResponse.success(svc.create(form));
    }

    /** 更新 MCP 服务 */
    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Map<String, Object> form) {
        return ApiResponse.success(svc.update(id, form));
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
}
