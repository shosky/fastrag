package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 数据库中间件。为每个关联的数据库注册表操作、表结构查询、SQL执行工具。
 *
 * <p>执行顺序: Order=10。</p>
 *
 * <p>当 context.databases 非空时，为每个数据库ID自动注册3个内置工具供 LLM 使用：
 * list_tables、describe_table、query_database。</p>
 */
@Slf4j
@Component
public class DatabaseMiddleware implements AgentMiddleware {

    @Override
    public int getOrder() { return 10; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        if (context.getDatabases() == null || context.getDatabases().isEmpty()) {
            return context;
        }

        try {
            log.info("[DatabaseMiddleware] Registering database tools for {} databases",
                    context.getDatabases().size());

            int toolCount = 0;

            for (String dbId : context.getDatabases()) {
                // list_tables_{dbId}
                tools.add(ToolDefinition.builder()
                        .toolId("list_tables_" + dbId)
                        .name(dbId + "_list_tables")
                        .description("列出数据库 " + dbId + " 的所有表")
                        .type("database")
                        .inputSchema(Map.of("type", "object", "properties", Map.of()))
                        .config(Map.of("action", "list_tables", "dbId", dbId))
                        .build());

                // describe_table_{dbId}
                tools.add(ToolDefinition.builder()
                        .toolId("describe_table_" + dbId)
                        .name(dbId + "_describe_table")
                        .description("查看数据库 " + dbId + " 中指定表的结构")
                        .type("database")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "table", Map.of("type", "string", "description", "表名")
                                ),
                                "required", List.of("table")
                        ))
                        .config(Map.of("action", "describe_table", "dbId", dbId))
                        .build());

                // query_database_{dbId}
                tools.add(ToolDefinition.builder()
                        .toolId("query_database_" + dbId)
                        .name(dbId + "_query_database")
                        .description("对数据库 " + dbId + " 执行 SQL 查询")
                        .type("database")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "sql", Map.of("type", "string", "description", "SQL查询语句")
                                ),
                                "required", List.of("sql")
                        ))
                        .config(Map.of("action", "query_database", "dbId", dbId))
                        .build());

                toolCount += 3;
            }

            log.info("[DatabaseMiddleware] Registered {} database tools", toolCount);
        } catch (Exception e) {
            log.error("[DatabaseMiddleware] Failed to register database tools: {}", e.getMessage());
        }

        return context;
    }

    @Override
    public BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        return context;
    }

    @Override
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                 ChatMessage.ToolCall toolCall) {
        return null;
    }
}
