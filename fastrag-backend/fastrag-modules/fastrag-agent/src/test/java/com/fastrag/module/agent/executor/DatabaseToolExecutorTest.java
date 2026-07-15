package com.fastrag.module.agent.executor;

import com.fastrag.module.tools.entity.DbTable;
import com.fastrag.module.tools.executor.ToolContext;
import com.fastrag.module.tools.executor.ToolResult;
import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.service.DbInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DatabaseToolExecutor} — Phase 1.
 *
 * <p>Covers: getType, list_tables / describe_table / query_database actions,
 * unknown action error, and null dbId error handling.</p>
 */
@ExtendWith(MockitoExtension.class)
class DatabaseToolExecutorTest {

    @Mock
    private DbInstanceService dbInstanceService;

    private DatabaseToolExecutor executor;

    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        executor = new DatabaseToolExecutor(dbInstanceService);
        toolContext = new ToolContext();
        toolContext.setUserId("user-1");
        toolContext.setAppId("app-1");
    }

    // ========== getType ==========

    @Test
    void testGetType() {
        assertEquals("database", executor.getType());
    }

    // ========== list_tables action ==========

    @Test
    void testListTablesAction() {
        DbTable table1 = new DbTable();
        table1.setTableName("users");
        table1.setTableComment("User table");
        DbTable table2 = new DbTable();
        table2.setTableName("orders");
        table2.setTableComment("Order table");

        when(dbInstanceService.listTables("db-001")).thenReturn(List.of(table1, table2));

        ToolDefinition tool = buildTool("list_tables", "db-001");
        ToolResult result = executor.execute(tool, Map.of(), toolContext);

        assertTrue(result.isSuccess());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().contains("users"));
        assertTrue(result.getOutput().contains("User table"));
        assertTrue(result.getOutput().contains("orders"));
        assertTrue(result.getOutput().contains("Order table"));
        verify(dbInstanceService).listTables("db-001");
    }

    @Test
    void testListTablesAction_EmptyDatabase() {
        when(dbInstanceService.listTables("db-empty")).thenReturn(List.of());

        ToolDefinition tool = buildTool("list_tables", "db-empty");
        ToolResult result = executor.execute(tool, Map.of(), toolContext);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("没有表"));
    }

    // ========== describe_table action ==========

    @Test
    void testDescribeTableAction() {
        DbTable usersTable = new DbTable();
        usersTable.setTableName("users");
        usersTable.setTableComment("User table");
        usersTable.setColumns("[{\"name\":\"id\",\"type\":\"bigint\"},{\"name\":\"name\",\"type\":\"varchar\"}]");
        usersTable.setRowCount(100L);

        when(dbInstanceService.listTables("db-001")).thenReturn(List.of(usersTable));

        ToolDefinition tool = buildTool("describe_table", "db-001");
        ToolResult result = executor.execute(tool, Map.of("table", "users"), toolContext);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("users"));
        assertTrue(result.getOutput().contains("User table"));
        assertTrue(result.getOutput().contains("100"));
    }

    @Test
    void testDescribeTableAction_TableNotFound() {
        DbTable otherTable = new DbTable();
        otherTable.setTableName("orders");

        when(dbInstanceService.listTables("db-001")).thenReturn(List.of(otherTable));

        ToolDefinition tool = buildTool("describe_table", "db-001");
        ToolResult result = executor.execute(tool, Map.of("table", "nonexistent"), toolContext);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("未找到表"));
    }

    // ========== query_database action ==========

    @Test
    void testQueryDatabaseAction() {
        when(dbInstanceService.query(eq("db-001"), anyString()))
                .thenReturn(Map.of(
                        "columns", List.of("id", "name"),
                        "rows", List.of(List.of(1, "Alice"), List.of(2, "Bob")),
                        "rowCount", 2
                ));

        ToolDefinition tool = buildTool("query_database", "db-001");
        ToolResult result = executor.execute(tool, Map.of("sql", "SELECT * FROM users LIMIT 2"), toolContext);

        assertTrue(result.isSuccess());
        assertNotNull(result.getOutput());
        verify(dbInstanceService).query(eq("db-001"), eq("SELECT * FROM users LIMIT 2"));
    }

    // ========== unknown action ==========

    @Test
    void testUnknownAction() {
        ToolDefinition tool = buildTool("unknown_action", "db-001");
        ToolResult result = executor.execute(tool, Map.of(), toolContext);

        assertTrue(result.isSuccess()); // executor returns success with error message in output
        assertTrue(result.getOutput().contains("Unknown database action"));
    }

    // ========== null dbId → error ==========

    @Test
    void testNullDbId() {
        ToolDefinition tool = ToolDefinition.builder()
                .toolId("list_tables_null")
                .name("list_tables")
                .description("List tables")
                .type("database")
                .config(Map.of("action", "list_tables", "dbId", "null-db"))
                .build();

        // listTables with a null-db that doesn't exist — service throws
        when(dbInstanceService.listTables("null-db"))
                .thenThrow(new RuntimeException("Database instance not found: null-db"));

        ToolResult result = executor.execute(tool, Map.of(), toolContext);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("not found") || result.getError().contains("null-db"));
    }
    
    // ========== query_database returns null ==========

    @Test
    void testQueryDatabase_NullResult() {
        when(dbInstanceService.query(eq("db-001"), anyString())).thenReturn(null);

        ToolDefinition tool = buildTool("query_database", "db-001");
        ToolResult result = executor.execute(tool, Map.of("sql", "SELECT 1"), toolContext);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("查询无返回结果"));
    }

    // ========== helper: build a ToolDefinition for a given action + dbId ==========

    private ToolDefinition buildTool(String action, String dbId) {
        return ToolDefinition.builder()
                .toolId(action + "_" + dbId)
                .name(dbId + "_" + action)
                .description("Test tool for " + action)
                .type("database")
                .config(Map.of("action", action, "dbId", dbId))
                .build();
    }
}
