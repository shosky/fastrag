package com.fastrag.module.agent.executor;

import com.fastrag.module.tools.entity.DbTable;
import com.fastrag.module.tools.executor.ToolContext;
import com.fastrag.module.tools.executor.ToolExecutor;
import com.fastrag.module.tools.executor.ToolResult;
import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.service.DbInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库工具执行器。
 * 支持 list_tables / describe_table / query_database 操作。
 *
 * <p>type = "database"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseToolExecutor implements ToolExecutor {

    private final DbInstanceService dbInstanceService;

    @Override
    public String getType() {
        return "database";
    }

    @Override
    public ToolResult execute(ToolDefinition tool, Map<String, Object> arguments, ToolContext ctx) {
        long start = System.currentTimeMillis();

        String action = (String) tool.getConfig().get("action");
        String dbId = (String) tool.getConfig().get("dbId");

        try {
            Map<String, Object> structured = null;
            String output = switch (action) {
                case "list_tables" -> {
                    List<DbTable> tables = dbInstanceService.listTables(dbId);
                    if (tables == null || tables.isEmpty()) {
                        yield "该数据库中没有表";
                    }
                    yield tables.stream()
                            .map(t -> t.getTableName() + (t.getTableComment() != null ? " — " + t.getTableComment() : ""))
                            .collect(Collectors.joining("\n"));
                }
                case "describe_table" -> {
                    String tableName = (String) arguments.get("table");
                    if (tableName == null || tableName.isBlank()) {
                        yield "Error: missing required argument 'table'";
                    }
                    List<DbTable> tables = dbInstanceService.listTables(dbId);
                    DbTable found = tables != null
                            ? tables.stream().filter(t -> tableName.equals(t.getTableName())).findFirst().orElse(null)
                            : null;
                    if (found == null) {
                        yield "Error: 未找到表: " + tableName;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("表名: ").append(found.getTableName()).append("\n");
                    sb.append("注释: ").append(found.getTableComment() != null ? found.getTableComment() : "无").append("\n");
                    sb.append("列信息: ").append(found.getColumns() != null ? found.getColumns() : "无").append("\n");
                    sb.append("行数: ").append(found.getRowCount() != null ? found.getRowCount() : "未知");
                    yield sb.toString();
                }
                case "query_database" -> {
                    String sql = (String) arguments.get("sql");
                    if (sql == null || sql.isBlank()) {
                        yield "Error: missing or blank argument 'sql'";
                    }
                    Map<String, Object> result = dbInstanceService.query(dbId, sql);
                    // 结构化通道（app-chat-optimize 二批）：display_type+columns/rows 直达前端表格渲染器
                    structured = new java.util.LinkedHashMap<>();
                    structured.put("display_type", "database_query");
                    structured.put("columns", result != null ? result.get("columns") : null);
                    structured.put("rows", result != null ? result.get("rows") : null);
                    structured.put("row_count", result != null ? result.get("rowCount") : 0);
                    structured.put("elapsed_ms", result != null ? result.get("elapsedMs") : null);
                    yield formatQueryResult(result);
                }
                default -> "Error: Unknown database action: " + action;
            };

            ToolResult result = ToolResult.success(output, (int) (System.currentTimeMillis() - start));
            if (structured != null) result.setStructured(structured);
            return result;
        } catch (Exception e) {
            log.error("Database tool execution failed", e);
            return ToolResult.error(e.getMessage(), (int) (System.currentTimeMillis() - start));
        }
    }

    private String formatQueryResult(Map<String, Object> result) {
        if (result == null) {
            return "查询无返回结果";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString().trim();
    }
}
