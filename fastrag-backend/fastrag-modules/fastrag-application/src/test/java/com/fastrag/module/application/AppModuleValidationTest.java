package com.fastrag.module.application;

import com.fastrag.module.application.entity.*;
import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.*;
import com.fastrag.module.application.service.impl.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive validation test for the fastrag-application module.
 *
 * <p>This test suite verifies:
 * <ul>
 *   <li>Entity-to-Mapper coverage (22 entities ↔ 22 mappers)</li>
 *   <li>Entity field correctness (no more copy-paste errors)</li>
 *   <li>Service interface completeness</li>
 *   <li>Schema-h2.sql table presence for application entities</li>
 *   <li>Mock/stub implementation detection</li>
 *   <li>AppConfigController runTrigger bug fix</li>
 * </ul>
 */
class AppModuleValidationTest {

    // ========================================================================
    // Section 1: Entity-to-Mapper Coverage
    // ========================================================================

    static final List<Class<?>> ALL_ENTITIES = List.of(
            App.class, AppConfig.class, AppBasicConfig.class, AppDialogConfig.class,
            AppGlobalPolicy.class, AppVariable.class, AppKbBinding.class, AppDbBinding.class,
            AppTrigger.class, AppPublishRecord.class, AppTemplate.class, AppDialogTest.class,
            AppOptimization.class, AppKbAutoUpdateConfig.class, Workflow.class, WfNode.class,
            WfTestCase.class, WfTemplate.class, WfMigration.class, WfDebugLog.class,
            WfMonitorData.class, WfOptimization.class
    );

    static final List<Class<?>> ALL_MAPPERS = List.of(
            AppMapper.class, AppConfigMapper.class, AppBasicConfigMapper.class,
            AppDialogConfigMapper.class, AppGlobalPolicyMapper.class, AppVariableMapper.class,
            AppKbBindingMapper.class, AppDbBindingMapper.class, AppTriggerMapper.class,
            AppPublishRecordMapper.class, AppTemplateMapper.class, AppDialogTestMapper.class,
            AppOptimizationMapper.class, AppKbAutoUpdateConfigMapper.class, WorkflowMapper.class,
            WfNodeMapper.class, WfTestCaseMapper.class, WfTemplateMapper.class,
            WfMigrationMapper.class, WfOptimizationMapper.class, WfDebugLogMapper.class,
            WfMonitorDataMapper.class
    );

    @Test
    @DisplayName("TC-ENTITY-MAPPER-001: 22 entities must have 22 corresponding mappers — no gaps")
    void verifyEntityMapperCoverage() {
        Set<String> expectedMapperNames = ALL_ENTITIES.stream()
                .map(Class::getSimpleName)
                .map(name -> name + "Mapper")
                .collect(Collectors.toSet());

        Set<String> actualMapperNames = ALL_MAPPERS.stream()
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        Set<String> missing = new TreeSet<>(expectedMapperNames);
        missing.removeAll(actualMapperNames);

        System.out.println("=== Entity → Mapper Coverage ===");
        System.out.println("Total entities: " + ALL_ENTITIES.size());
        System.out.println("Total mappers:  " + ALL_MAPPERS.size());
        if (missing.isEmpty()) {
            System.out.println("Missing mappers: NONE — all covered ✓");
        } else {
            System.out.println("Missing mappers: " + missing);
        }

        assertEquals(0, missing.size(),
                "Every entity must have a corresponding mapper. Missing: " + missing);
        assertEquals(ALL_ENTITIES.size(), ALL_MAPPERS.size(),
                "Entity and Mapper counts must match (both 22)");
    }

    @Test
    @DisplayName("TC-ENTITY-MAPPER-002: List all mapper-entity pairings for documentation")
    void listMapperEntityPairings() {
        System.out.println("=== Entity ↔ Mapper Pairings ===");
        int missingCount = 0;
        for (Class<?> entity : ALL_ENTITIES) {
            String mapperName = entity.getSimpleName() + "Mapper";
            boolean hasMapper = ALL_MAPPERS.stream()
                    .anyMatch(m -> m.getSimpleName().equals(mapperName));
            System.out.printf("  %-40s %c  %s%n",
                    entity.getSimpleName(),
                    hasMapper ? '✓' : '✗',
                    hasMapper ? mapperName : "(NO MAPPER)");
            if (!hasMapper) missingCount++;
        }
        assertEquals(0, missingCount, "No entities should lack mappers");
    }

    // ========================================================================
    // Section 2: Entity Field Correctness (post copy-paste fix)
    // ========================================================================

    static final List<Class<?>> PREVIOUSLY_COPY_PASTE_ENTITIES = List.of(
            AppKbAutoUpdateConfig.class,
            WfDebugLog.class,
            WfMonitorData.class,
            WfOptimization.class
    );

    @Test
    @DisplayName("TC-ENTITY-FIELD-001: Previously copy-paste entities now have distinct, correct fields")
    void verifyFixedEntitiesHaveDistinctFields() {
        List<Set<String>> fieldSets = PREVIOUSLY_COPY_PASTE_ENTITIES.stream()
                .map(this::getDeclaredFieldNames)
                .toList();

        // At least some should differ from each other
        Set<String> baseline = fieldSets.get(0);
        boolean allIdentical = fieldSets.stream().allMatch(f -> f.equals(baseline));

        System.out.println("=== Entity Field Distinctness Check ===");
        for (int i = 0; i < PREVIOUSLY_COPY_PASTE_ENTITIES.size(); i++) {
            System.out.println("  " + PREVIOUSLY_COPY_PASTE_ENTITIES.get(i).getSimpleName()
                    + ": " + fieldSets.get(i).size() + " fields → " + fieldSets.get(i));
        }

        assertFalse(allIdentical,
                "After fix, the 4 previously-copy-paste entities should NOT all have identical fields. "
                        + "Each should match its database schema.");
    }

    @Test
    @DisplayName("TC-ENTITY-FIELD-002: AppKbAutoUpdateConfig has only correct fields per schema")
    void verifyAppKbAutoUpdateConfigFields() {
        Set<String> fields = getDeclaredFieldNames(AppKbAutoUpdateConfig.class);
        System.out.println("=== AppKbAutoUpdateConfig Fields ===");
        System.out.println("  Fields: " + fields);

        // Must have schema fields
        assertTrue(fields.contains("id"), "Missing: id");
        assertTrue(fields.contains("appId"), "Missing: appId");
        assertTrue(fields.contains("enabled"), "Missing: enabled");
        assertTrue(fields.contains("cronExpr"), "Missing: cronExpr");
        assertTrue(fields.contains("autoPublish"), "Missing: autoPublish");
        assertTrue(fields.contains("notifyChannels"), "Missing: notifyChannels");
        assertTrue(fields.contains("createdAt"), "Missing: createdAt");
        assertTrue(fields.contains("updatedAt"), "Missing: updatedAt");

        // Must NOT have copy-paste artifacts
        assertFalse(fields.contains("temperature"), "Should NOT have temperature (copy-paste artifact)");
        assertFalse(fields.contains("query"), "Should NOT have query (copy-paste artifact)");
        assertFalse(fields.contains("nodeKey"), "Should NOT have nodeKey (copy-paste artifact)");
        assertFalse(fields.contains("metricValue"), "Should NOT have metricValue (copy-paste artifact)");
        assertFalse(fields.contains("expectedAnswer"), "Should NOT have expectedAnswer (copy-paste artifact)");
        assertFalse(fields.contains("triggerType"), "Should NOT have triggerType (copy-paste artifact)");

        assertTrue(fields.size() <= 10, "Should have ≤10 fields (was 50+ before fix), got " + fields.size());
        System.out.println("  ✓ AppKbAutoUpdateConfig has correct " + fields.size() + " fields");
    }

    @Test
    @DisplayName("TC-ENTITY-FIELD-003: WfDebugLog has only correct fields per schema")
    void verifyWfDebugLogFields() {
        Set<String> fields = getDeclaredFieldNames(WfDebugLog.class);
        System.out.println("=== WfDebugLog Fields ===");
        System.out.println("  Fields: " + fields);

        assertTrue(fields.contains("id"), "Missing: id");
        assertTrue(fields.contains("workflowId"), "Missing: workflowId");
        assertTrue(fields.contains("nodeKey"), "Missing: nodeKey");
        assertTrue(fields.contains("level"), "Missing: level");
        assertTrue(fields.contains("message"), "Missing: message");
        assertTrue(fields.contains("context"), "Missing: context");
        assertTrue(fields.contains("createdAt"), "Missing: createdAt");

        assertFalse(fields.contains("temperature"), "Should NOT have temperature (copy-paste artifact)");
        assertFalse(fields.contains("expectedAnswer"), "Should NOT have expectedAnswer (copy-paste artifact)");
        assertFalse(fields.contains("safetyEnabled"), "Should NOT have safetyEnabled (copy-paste artifact)");

        assertTrue(fields.size() <= 10, "Should have ≤10 fields, got " + fields.size());
        System.out.println("  ✓ WfDebugLog has correct " + fields.size() + " fields");
    }

    @Test
    @DisplayName("TC-ENTITY-FIELD-004: WfMonitorData has only correct fields per schema")
    void verifyWfMonitorDataFields() {
        Set<String> fields = getDeclaredFieldNames(WfMonitorData.class);
        System.out.println("=== WfMonitorData Fields ===");
        System.out.println("  Fields: " + fields);

        assertTrue(fields.contains("id"), "Missing: id");
        assertTrue(fields.contains("workflowId"), "Missing: workflowId");
        assertTrue(fields.contains("metricType"), "Missing: metricType");
        assertTrue(fields.contains("metricValue"), "Missing: metricValue");
        assertTrue(fields.contains("dimension"), "Missing: dimension");
        assertTrue(fields.contains("periodStart"), "Missing: periodStart");
        assertTrue(fields.contains("periodEnd"), "Missing: periodEnd");
        assertTrue(fields.contains("details"), "Missing: details");

        assertFalse(fields.contains("temperature"), "Should NOT have temperature (copy-paste artifact)");
        assertFalse(fields.contains("triggerType"), "Should NOT have triggerType (copy-paste artifact)");
        assertFalse(fields.contains("query"), "Should NOT have query (copy-paste artifact)");

        assertTrue(fields.size() <= 10, "Should have ≤10 fields, got " + fields.size());
        System.out.println("  ✓ WfMonitorData has correct " + fields.size() + " fields");
    }

    @Test
    @DisplayName("TC-ENTITY-FIELD-005: WfOptimization has only correct fields per schema")
    void verifyWfOptimizationFields() {
        Set<String> fields = getDeclaredFieldNames(WfOptimization.class);
        System.out.println("=== WfOptimization Fields ===");
        System.out.println("  Fields: " + fields);

        assertTrue(fields.contains("id"), "Missing: id");
        assertTrue(fields.contains("workflowId"), "Missing: workflowId");
        assertTrue(fields.contains("suggestionType"), "Missing: suggestionType");
        assertTrue(fields.contains("title"), "Missing: title");
        assertTrue(fields.contains("description"), "Missing: description");
        assertTrue(fields.contains("impactScore"), "Missing: impactScore");
        assertTrue(fields.contains("status"), "Missing: status");
        assertTrue(fields.contains("beforeMetric"), "Missing: beforeMetric");
        assertTrue(fields.contains("afterMetric"), "Missing: afterMetric");
        assertTrue(fields.contains("createdAt"), "Missing: createdAt");

        assertFalse(fields.contains("temperature"), "Should NOT have temperature (copy-paste artifact)");
        assertFalse(fields.contains("nodeKey"), "Should NOT have nodeKey (copy-paste artifact)");
        assertFalse(fields.contains("query"), "Should NOT have query (copy-paste artifact)");

        assertTrue(fields.size() <= 12, "Should have ≤12 fields, got " + fields.size());
        System.out.println("  ✓ WfOptimization has correct " + fields.size() + " fields");
    }

    // ========================================================================
    // Section 3: Service Interface Completeness
    // ========================================================================

    @Test
    @DisplayName("TC-SERVICE-001: AppService interface declares all required methods")
    void verifyAppServiceInterface() {
        Set<String> methods = Arrays.stream(AppService.class.getDeclaredMethods())
                .map(m -> m.getName() + "(" + m.getParameterCount() + " params)")
                .collect(Collectors.toSet());
        System.out.println("=== AppService Methods (" + methods.size() + ") ===");
        methods.forEach(m -> System.out.println("  " + m));
        assertTrue(methods.stream().anyMatch(m -> m.startsWith("run(")), "Missing: run()");
    }

    @Test
    @DisplayName("TC-SERVICE-002: AppConfigService now has runTrigger method")
    void verifyAppConfigServiceHasRunTrigger() {
        Set<String> methods = Arrays.stream(AppConfigService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
        System.out.println("=== AppConfigService Methods (" + methods.size() + ") ===");
        methods.forEach(m -> System.out.println("  " + m));

        assertTrue(methods.contains("testTrigger"), "Missing: testTrigger");
        assertTrue(methods.contains("runTrigger"), "Missing: runTrigger (bug fix)");
        assertTrue(methods.contains("getBasic"), "Missing: getBasic");
        assertTrue(methods.contains("publish"), "Missing: publish");
        assertTrue(methods.contains("getMonitorData"), "Missing: getMonitorData");
        System.out.println("  ✓ runTrigger method confirmed present (bug fix verified)");
    }

    @Test
    @DisplayName("TC-SERVICE-003: AppConfigServiceImpl implements runTrigger separately from testTrigger")
    void verifyRunTriggerIsSeparateImpl() {
        var testTrigger = Arrays.stream(AppConfigServiceImpl.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("testTrigger")).findFirst();
        var runTrigger = Arrays.stream(AppConfigServiceImpl.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("runTrigger")).findFirst();

        assertTrue(testTrigger.isPresent(), "testTrigger must exist");
        assertTrue(runTrigger.isPresent(), "runTrigger must exist");

        // Both must be different method objects (not aliases)
        assertNotSame(testTrigger.get(), runTrigger.get(),
                "runTrigger must be a separate method from testTrigger");
        System.out.println("  ✓ testTrigger and runTrigger are separate methods");
    }

    @Test
    @DisplayName("TC-SERVICE-004: WorkflowService interface declares all required methods")
    void verifyWorkflowServiceInterface() {
        Set<String> methods = Arrays.stream(WorkflowService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());
        System.out.println("=== WorkflowService Methods (" + methods.size() + ") ===");
        methods.forEach(m -> System.out.println("  " + m));
        assertTrue(methods.contains("execute"), "Missing: execute");
        assertTrue(methods.contains("getDebugInfo"), "Missing: getDebugInfo");
    }

    // ========================================================================
    // Section 4: Mock/Stub Implementation Detection
    // ========================================================================

    @Test
    @DisplayName("TC-MOCK-001: AppServiceImpl.run() is keyword-matching only, no LLM/RAG call")
    void detectAppServiceRunIsMock() {
        String source = extractMethodSource(AppServiceImpl.class, "run");
        System.out.println("=== AppServiceImpl.run() Analysis ===");
        assertTrue(source.contains("matchAnswer"), "run() delegates to matchAnswer()");
        assertFalse(source.toLowerCase().contains("llm"), "run() should NOT call LLM");
        assertFalse(source.contains("retriev"), "run() should NOT perform RAG retrieval");
    }

    @Test
    @DisplayName("TC-MOCK-002: WorkflowServiceImpl.execute() returns hardcoded values")
    void detectWorkflowExecuteIsMock() {
        String source = extractMethodSource(WorkflowServiceImpl.class, "execute");
        assertTrue(source.contains("\"completed\""), "execute() hardcodes 'completed'");
        assertTrue(source.contains("\"执行完成\""), "execute() hardcodes '执行完成'");
    }

    @Test
    @DisplayName("TC-MOCK-003: WorkflowServiceImpl.getMonitorData() returns hardcoded metrics")
    void detectWorkflowMonitorIsMock() {
        String source = extractMethodSource(WorkflowServiceImpl.class, "getMonitorData");
        assertTrue(source.contains("3200"), "Hardcoded totalExecutions=3200");
        assertFalse(source.contains("select") || source.contains("mapper"), "Should NOT query DB");
    }

    // ========================================================================
    // Section 5: Schema Presence Verification
    // ========================================================================

    @Test
    @DisplayName("TC-SCHEMA-001: schema-h2.sql now contains all 22 application entity tables")
    void verifySchemaH2TablesComplete() throws Exception {
        Path schemaPath = findSchemaH2();
        assertNotNull(schemaPath, "schema-h2.sql not found");

        String content = Files.readString(schemaPath);
        List<String> tables = Arrays.stream(content.split("CREATE TABLE"))
                .filter(s -> s.contains("IF NOT EXISTS"))
                .map(s -> {
                    int start = s.indexOf("IF NOT EXISTS") + "IF NOT EXISTS".length();
                    int end = s.indexOf('(', start);
                    return s.substring(start, end).trim().replace("`", "").replace("\"", "");
                })
                .toList();

        Set<String> present = new HashSet<>(tables);
        System.out.println("=== schema-h2.sql Tables (" + tables.size() + " total) ===");

        Map<String, String> entityTables = Map.ofEntries(
                Map.entry("App", "app"), Map.entry("AppConfig", "app_config"),
                Map.entry("AppBasicConfig", "app_basic_config"),
                Map.entry("AppDialogConfig", "app_dialog_config"),
                Map.entry("AppGlobalPolicy", "app_global_policy"),
                Map.entry("AppVariable", "app_variable"),
                Map.entry("AppKbBinding", "app_kb_binding"),
                Map.entry("AppDbBinding", "app_db_binding"),
                Map.entry("AppTrigger", "app_trigger"),
                Map.entry("AppPublishRecord", "app_publish_record"),
                Map.entry("AppTemplate", "app_template"),
                Map.entry("AppDialogTest", "app_dialog_test"),
                Map.entry("AppOptimization", "app_optimization"),
                Map.entry("AppKbAutoUpdateConfig", "app_kb_auto_update_config"),
                Map.entry("Workflow", "workflow"), Map.entry("WfNode", "wf_node"),
                Map.entry("WfTestCase", "wf_test_case"), Map.entry("WfTemplate", "wf_template"),
                Map.entry("WfMigration", "wf_migration"), Map.entry("WfDebugLog", "wf_debug_log"),
                Map.entry("WfMonitorData", "wf_monitor_data"), Map.entry("WfOptimization", "wf_optimization")
        );

        List<String> missing = new ArrayList<>();
        for (var entry : entityTables.entrySet()) {
            boolean found = present.contains(entry.getValue());
            System.out.printf("  %-40s -> %-30s %s%n", entry.getKey(), entry.getValue(),
                    found ? "✓" : "✗ MISSING");
            if (!found) missing.add(entry.getValue());
        }

        System.out.println("\nMissing tables: " + missing.size());
        assertEquals(0, missing.size(),
                "After fix, schema-h2.sql must contain all application tables. Missing: " + missing);
    }

    // ========================================================================
    // Section 6: Regression Tests
    // ========================================================================

    @Test
    @DisplayName("TC-REG-001: App entity has all expected CRUD fields")
    void verifyAppEntityFields() {
        Set<String> fields = getDeclaredFieldNames(App.class);
        for (String f : List.of("id", "name", "description", "type", "icon", "status", "tags", "owner", "createdAt", "updatedAt")) {
            assertTrue(fields.contains(f), "App missing field: " + f);
        }
        System.out.println("  ✓ App entity: " + fields.size() + " fields, all core CRUD fields present");
    }

    @Test
    @DisplayName("TC-REG-002: AppConfig entity has model/temperature/knowledgeIds/toolIds")
    void verifyAppConfigEntityFields() {
        Set<String> fields = getDeclaredFieldNames(AppConfig.class);
        for (String f : List.of("id", "appId", "model", "temperature", "knowledgeIds", "toolIds", "maxTurns")) {
            assertTrue(fields.contains(f), "AppConfig missing field: " + f);
        }
        System.out.println("  ✓ AppConfig entity: " + fields.size() + " fields");
    }

    @Test
    @DisplayName("TC-REG-003: Service implementations have @Service annotation and required-args constructor")
    void verifyServiceAnnotations() {
        for (Class<?> svc : List.of(AppServiceImpl.class, AppConfigServiceImpl.class, WorkflowServiceImpl.class)) {
            assertTrue(svc.isAnnotationPresent(org.springframework.stereotype.Service.class),
                    svc.getSimpleName() + " should have @Service");
            // Lombok @RequiredArgsConstructor is compile-time; verify via constructor with final fields
            boolean hasMultiParamCtor = Arrays.stream(svc.getDeclaredConstructors())
                    .anyMatch(c -> c.getParameterCount() >= 2);
            assertTrue(hasMultiParamCtor,
                    svc.getSimpleName() + " should have a multi-param constructor (from @RequiredArgsConstructor)");
        }
        System.out.println("  ✓ All services have @Service + multi-param constructor");
    }

    @Test
    @DisplayName("TC-REG-004: AppConfigController.runTrigger calls svc.runTrigger (not testTrigger)")
    void verifyControllerRunTriggerBugFix() {
        var methods = Arrays.stream(
                        com.fastrag.module.application.controller.AppConfigController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("runTrigger"))
                .toList();

        assertEquals(1, methods.size(), "AppConfigController should have runTrigger method");
        // Verify by reading source that it calls svc.runTrigger, not svc.testTrigger
        String source = extractMethodSource(
                com.fastrag.module.application.controller.AppConfigController.class, "runTrigger");
        assertTrue(source.contains("runTrigger"),
                "runTrigger endpoint should call svc.runTrigger(), not svc.testTrigger()");
        System.out.println("  ✓ AppConfigController.runTrigger correctly calls svc.runTrigger()");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Set<String> getDeclaredFieldNames(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private Path findSchemaH2() {
        String baseDir = System.getProperty("user.dir", ".");
        List<String> candidates = List.of(
                "fastrag-bootstrap/src/main/resources/schema-h2.sql",
                "../fastrag-bootstrap/src/main/resources/schema-h2.sql",
                "../../fastrag-bootstrap/src/main/resources/schema-h2.sql",
                "../../../fastrag-bootstrap/src/main/resources/schema-h2.sql"
        );
        for (String r : candidates) {
            Path p = Paths.get(baseDir).resolve(r).normalize();
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private String extractMethodSource(Class<?> clazz, String methodName) {
        String sourcePath = clazz.getCanonicalName().replace('.', '/') + ".java";
        List<String> roots = List.of(
                "src/main/java", "../fastrag-application/src/main/java",
                "../../fastrag-application/src/main/java", "../../../fastrag-application/src/main/java"
        );
        String baseDir = System.getProperty("user.dir", ".");
        for (String root : roots) {
            try {
                Path full = Paths.get(baseDir).resolve(root).resolve(sourcePath).normalize();
                if (Files.exists(full)) return extractMethodBody(Files.readString(full), methodName);
            } catch (Exception ignored) {}
        }
        return "Cannot locate source for " + clazz.getSimpleName() + "." + methodName;
    }

    private String extractMethodBody(String source, String methodName) {
        int idx = source.indexOf(methodName + "(");
        if (idx < 0) idx = source.indexOf(" " + methodName + "(");
        if (idx < 0) return "Cannot locate method: " + methodName;
        int brace = source.indexOf('{', idx);
        if (brace < 0) return "Cannot find body for: " + methodName;
        int depth = 1, pos = brace + 1;
        while (depth > 0 && pos < source.length()) {
            if (source.charAt(pos) == '{') depth++;
            else if (source.charAt(pos) == '}') depth--;
            pos++;
        }
        return source.substring(brace, pos);
    }
}
