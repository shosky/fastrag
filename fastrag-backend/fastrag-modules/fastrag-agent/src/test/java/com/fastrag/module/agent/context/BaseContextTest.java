package com.fastrag.module.agent.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BaseContext} — Phase 1: databases & maxSteps fields,
 * configurable items filtering, and runtimeState initialization.
 */
@ExtendWith(MockitoExtension.class)
class BaseContextTest {

    // ========== updateFromMap — databases ==========

    @Test
    void testSetDatabasesViaMap() {
        BaseContext ctx = new BaseContext();

        Map<String, Object> map = Map.of(
                "databases", List.of("db1", "db2")
        );

        ctx.updateFromMap(map);

        assertNotNull(ctx.getDatabases());
        assertEquals(2, ctx.getDatabases().size());
        assertTrue(ctx.getDatabases().contains("db1"));
        assertTrue(ctx.getDatabases().contains("db2"));
    }

    // ========== updateFromMap — maxSteps ==========

    @Test
    void testSetMaxStepsViaMap() {
        BaseContext ctx = new BaseContext();

        Map<String, Object> map = Map.of("maxSteps", 10);

        ctx.updateFromMap(map);

        assertNotNull(ctx.getMaxSteps());
        assertEquals(10, ctx.getMaxSteps());
    }

    // ========== getConfigurableItems — includes databases ==========

    @Test
    void testConfigurableItemsIncludesDatabases() {
        BaseContext ctx = new BaseContext();

        List<ConfigurableItem> items = ctx.getConfigurableItems("user");

        boolean hasDatabases = items.stream()
                .anyMatch(item -> "databases".equals(item.getField()));
        assertTrue(hasDatabases, "configurableItems should include 'databases' for user role");
    }

    // ========== getConfigurableItems — includes maxSteps ==========

    @Test
    void testConfigurableItemsIncludesMaxSteps() {
        BaseContext ctx = new BaseContext();

        List<ConfigurableItem> items = ctx.getConfigurableItems("user");

        boolean hasMaxSteps = items.stream()
                .anyMatch(item -> "maxSteps".equals(item.getField()));
        assertTrue(hasMaxSteps, "configurableItems should include 'maxSteps' for user role");
    }

    // ========== getConfigurableItems — excludes hidden fields ==========

    @Test
    void testConfigurableItemsExcludesHiddenFields() {
        BaseContext ctx = new BaseContext();

        List<ConfigurableItem> items = ctx.getConfigurableItems("admin");

        boolean hasRunId = items.stream()
                .anyMatch(item -> "runId".equals(item.getField()));
        boolean hasRequestId = items.stream()
                .anyMatch(item -> "requestId".equals(item.getField()));

        assertFalse(hasRunId, "configurableItems should NOT include 'runId' (hide=true)");
        assertFalse(hasRequestId, "configurableItems should NOT include 'requestId' (hide=true)");
    }

    // ========== runtimeState defaults to non-null ==========

    @Test
    void testRuntimeStateDefaultsToNonNull() {
        BaseContext ctx = new BaseContext();

        assertNotNull(ctx.getRuntimeState(),
                "runtimeState should never be null, even on a fresh instance");
        assertTrue(ctx.getRuntimeState().isEmpty(),
                "runtimeState should be empty map on a fresh instance");
    }
}
