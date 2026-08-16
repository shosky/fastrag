package com.fastrag.module.tools.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.tools.entity.McpCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP调用日志Mapper接口。
 *
 * <p>对应 mcp_call_log 表，提供MCP工具调用日志的记录和查询操作，
 * 用于追踪和审计MCP协议的调用历史。</p>
 */
@Mapper public interface McpCallLogMapper extends BaseMapper<McpCallLog> {}
