package com.fastrag.module.tools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.McpTool;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP工具配置Mapper接口。
 *
 * <p>对应 mcp_tool 表，提供MCP（Model Context Protocol）工具配置的CRUD操作，
 * 管理MCP服务中注册的工具信息。</p>
 */
@Mapper public interface McpToolMapper extends BaseMapper<McpTool> {}
