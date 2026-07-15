package com.fastrag.module.tools.service;
import com.fastrag.module.tools.entity.*; import java.util.*;

public interface McpServiceService {
    List<Map<String, Object>> list(String keyword);
    List<Map<String, Object>> listEnabled();
    List<McpService> listBuiltin();
    McpService get(String id);
    /** 获取 MCP 服务详情（包含工具列表） */
    Map<String, Object> getWithTools(String id);
    McpService getBySlug(String slug);
    McpService create(Map<String, Object> form);
    McpService update(String id, Map<String, Object> form);
    void delete(String id);
    void toggleEnabled(String id);
    void toggleTool(Long toolId);
    List<McpTool> listTools(String serviceId);
    boolean existsBySlug(String slug);

    /** 刷新 MCP 服务：连接服务器、发现工具、更新状态 */
    McpService refresh(String id);

    /** 解析 MCP URL：连接服务器发现工具，不持久化。用于创建前的「解析」按钮 */
    Map<String, Object> parseUrl(Map<String, Object> form);

    /** 手动添加工具到 MCP 服务 */
    McpTool addTool(String serviceId, Map<String, Object> form);

    /** 手动更新 MCP 工具 */
    McpTool updateTool(Long toolId, Map<String, Object> form);

    /** 手动删除 MCP 工具 */
    void deleteTool(Long toolId);

    /** 测试 MCP 工具调用：连接服务器，执行 tools/call，返回结果 */
    Map<String, Object> testTool(Long toolId, Map<String, Object> arguments);
}
