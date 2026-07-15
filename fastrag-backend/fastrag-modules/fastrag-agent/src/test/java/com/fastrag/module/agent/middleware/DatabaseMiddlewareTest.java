package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DatabaseMiddleware} — Phase 1.
 *
 * <p>DatabaseMiddleware has no injected dependencies; it reads context.databases
 * and registers built-in tools (list_tables, describe_table, query_database) per database.</p>
 */
@ExtendWith(MockitoExtension.class)
class DatabaseMiddlewareTest {

    private DatabaseMiddleware middleware;

    @BeforeEach
    void setUp() {
        middleware = new DatabaseMiddleware();
    }

    // ========== getOrder ==========

    @Test
    void testGetOrder() {
        assertEquals(10, middleware.getOrder());
    }

    // ========== empty databases → no tools registered ==========

    @Test
    void testEmptyDatabases_NoToolsRegistered() {
        BaseContext context = new BaseContext();
        context.setDatabases(null);

        List<ChatMessage> messages = new ArrayList<>();
        List<ToolDefinition> tools = new ArrayList<>();

        middleware.beforeModelCall(context, messages, tools);

        assertTrue(tools.isEmpty(), "No tools should be registered when databases is null");
    }

    @Test
    void testEmptyListDatabases_NoToolsRegistered() {
        BaseContext context = new BaseContext();
        context.setDatabases(new ArrayList<>());

        List<ChatMessage> messages = new ArrayList<>();
        List<ToolDefinition> tools = new ArrayList<>();

        middleware.beforeModelCall(context, messages, tools);

        assertTrue(tools.isEmpty(), "No tools should be registered when databases is empty list");
    }

    // ========== 1 database → 3 tools registered ==========

    @Test
    void testOneDatabase_ThreeToolsRegistered() {
        BaseContext context = new BaseContext();
        context.setDatabases(List.of("db-001"));

        List<ChatMessage> messages = new ArrayList<>();
        List<ToolDefinition> tools = new ArrayList<>();

        middleware.beforeModelCall(context, messages, tools);

        assertEquals(3, tools.size(), "Should register exactly 3 tools for 1 database");

        // Verify tool names
        assertTrue(tools.stream().anyMatch(t -> "db-001_list_tables".equals(t.getName())),
                "Should have list_tables tool");
        assertTrue(tools.stream().anyMatch(t -> "db-001_describe_table".equals(t.getName())),
                "Should have describe_table tool");
        assertTrue(tools.stream().anyMatch(t -> "db-001_query_database".equals(t.getName())),
                "Should have query_database tool");

        // Verify toolIds
        assertTrue(tools.stream().anyMatch(t -> "list_tables_db-001".equals(t.getToolId())),
                "Should have toolId list_tables_db-001");
        assertTrue(tools.stream().anyMatch(t -> "describe_table_db-001".equals(t.getToolId())),
                "Should have toolId describe_table_db-001");
        assertTrue(tools.stream().anyMatch(t -> "query_database_db-001".equals(t.getToolId())),
                "Should have toolId query_database_db-001");

        // Verify all tools have type="database"
        assertTrue(tools.stream().allMatch(t -> "database".equals(t.getType())),
                "All tools should have type 'database'");

        // Verify config contains action + dbId
        for (ToolDefinition tool : tools) {
            assertNotNull(tool.getConfig(), "Tool config should not be null");
            assertEquals("db-001", tool.getConfig().get("dbId"));
            assertNotNull(tool.getConfig().get("action"));
        }
    }

    // ========== 2 databases → 6 tools registered ==========

    @Test
    void testTwoDatabases_SixToolsRegistered() {
        BaseContext context = new BaseContext();
        context.setDatabases(List.of("db-alpha", "db-beta"));

        List<ChatMessage> messages = new ArrayList<>();
        List<ToolDefinition> tools = new ArrayList<>();

        middleware.beforeModelCall(context, messages, tools);

        assertEquals(6, tools.size(), "Should register exactly 6 tools for 2 databases");

        // Verify tools for db-alpha
        assertTrue(tools.stream().anyMatch(t -> "db-alpha_list_tables".equals(t.getName())));
        assertTrue(tools.stream().anyMatch(t -> "db-alpha_describe_table".equals(t.getName())));
        assertTrue(tools.stream().anyMatch(t -> "db-alpha_query_database".equals(t.getName())));

        // Verify tools for db-beta
        assertTrue(tools.stream().anyMatch(t -> "db-beta_list_tables".equals(t.getName())));
        assertTrue(tools.stream().anyMatch(t -> "db-beta_describe_table".equals(t.getName())));
        assertTrue(tools.stream().anyMatch(t -> "db-beta_query_database".equals(t.getName())));

        // Verify each tool's config has correct dbId
        for (ToolDefinition tool : tools) {
            String dbId = (String) tool.getConfig().get("dbId");
            assertTrue(dbId.equals("db-alpha") || dbId.equals("db-beta"),
                    "dbId should be either 'db-alpha' or 'db-beta', got: " + dbId);
        }
    }

    // ========== inputSchema verification ==========

    @Test
    void testToolInputSchemas() {
        BaseContext context = new BaseContext();
        context.setDatabases(List.of("db-001"));

        List<ToolDefinition> tools = new ArrayList<>();
        middleware.beforeModelCall(context, new ArrayList<>(), tools);

        // list_tables should have no required fields
        ToolDefinition listTables = tools.stream()
                .filter(t -> "list_tables_db-001".equals(t.getToolId()))
                .findFirst().orElseThrow();
        assertNotNull(listTables.getInputSchema());

        // describe_table should require "table"
        ToolDefinition describeTable = tools.stream()
                .filter(t -> "describe_table_db-001".equals(t.getToolId()))
                .findFirst().orElseThrow();
        assertNotNull(describeTable.getInputSchema());
        Map<String, Object> describeProps = (Map<String, Object>) describeTable.getInputSchema().get("properties");
        assertNotNull(describeProps);
        assertTrue(describeProps.containsKey("table"), "describe_table should have 'table' property");

        // query_database should require "sql"
        ToolDefinition queryDatabase = tools.stream()
                .filter(t -> "query_database_db-001".equals(t.getToolId()))
                .findFirst().orElseThrow();
        assertNotNull(queryDatabase.getInputSchema());
        Map<String, Object> queryProps = (Map<String, Object>) queryDatabase.getInputSchema().get("properties");
        assertNotNull(queryProps);
        assertTrue(queryProps.containsKey("sql"), "query_database should have 'sql' property");
    }

    // ========== afterModelCall is a no-op ==========

    @Test
    void testAfterModelCall_NoOp() {
        BaseContext context = new BaseContext();
        ChatResponse response = new ChatResponse();
        response.setContent("hello");

        BaseContext result = middleware.afterModelCall(context, response);

        assertSame(context, result, "afterModelCall should return the same context unchanged");
    }
}
