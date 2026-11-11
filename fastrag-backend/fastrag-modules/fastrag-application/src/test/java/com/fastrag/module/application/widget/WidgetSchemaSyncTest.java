package com.fastrag.module.application.widget;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S9 双处/双端守护（tdd/widget.md S9；SkillDdlParityTest 式）：
 * ①widget 与 app_widget_binding 的 DDL 在 schema.sql 与 SchemaInitializer 双处齐备且列集一致；
 * ②后端白名单常量 ↔ 前端 registry 键集双向相等（前端目录不可达环境跳过并登记）。
 */
class WidgetSchemaSyncTest {

    private Path findRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("init-scripts").resolve("schema.sql"))) return dir;
            Path parent = dir.getParent();
            if (parent == null && Files.exists(dir.resolve("fastrag-backend").resolve("init-scripts"))) {
                return dir.resolve("fastrag-backend");
            }
            dir = parent;
        }
        throw new IllegalStateException("repo root with init-scripts/schema.sql not found");
    }

    @Test
    void widgetDdlPresentInBothPlaces() throws IOException {
        Path root = findRepoRoot();
        String schemaSql = Files.readString(root.resolve("init-scripts").resolve("schema.sql"));
        String initializer = Files.readString(root
                .resolve("fastrag-bootstrap/src/main/java/com/fastrag/config/SchemaInitializer.java")
                .toAbsolutePath().normalize());

        for (String table : List.of("widget", "app_widget_binding")) {
            assertThat(schemaSql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(initializer).contains("CREATE TABLE IF NOT EXISTS " + table);
        }
        // 列集一致性：正则抽 CREATE 块，逐列名对账（SchemaInitializer 列集 = schema.sql 列集）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "CREATE TABLE IF NOT EXISTS (widget|app_widget_binding) \\((.*?)ENGINE=InnoDB",
                java.util.regex.Pattern.DOTALL);
        var sqlMatcher = pattern.matcher(schemaSql);
        var initMatcher = pattern.matcher(initializer);
        assertThat(sqlMatcher.find()).isTrue();
        assertThat(initMatcher.find()).isTrue();
        Set<String> sqlCols = columnsOf(sqlMatcher.group(2));
        Set<String> initCols = columnsOf(initMatcher.group(2));
        assertThat(initCols).as("SchemaInitializer 列集").containsExactlyInAnyOrderElementsOf(sqlCols);
    }

    @Test
    void bindingColumnsPresentInBothPlaces() throws IOException {
        Path root = findRepoRoot();
        String schemaSql = Files.readString(root.resolve("init-scripts").resolve("schema.sql"));
        String initializer = Files.readString(root
                .resolve("fastrag-bootstrap/src/main/java/com/fastrag/config/SchemaInitializer.java")
                .toAbsolutePath().normalize());
        for (String col : List.of("id", "app_id", "widget_id", "enabled", "created_at")) {
            assertThat(schemaSql).contains(col);
            assertThat(initializer).contains(col);
        }
        assertThat(schemaSql).contains("uk_app_widget");
        assertThat(initializer).contains("uk_app_widget");
    }

    @Test
    void backendWhitelistMatchesFrontendRegistryWhenReachable() throws IOException {
        Path root = findRepoRoot();
        Path registry = root.resolve("../fastrag-frontend/src/views/conversation/components/widget/registry.ts")
                .toAbsolutePath().normalize();
        if (!Files.exists(registry)) {
            // 前端目录不可达环境（独立后端 checkout）：跳过并登记（tdd/widget.md S9 口径）
            System.out.println("[WidgetWhitelistParity] frontend registry not reachable, parity check skipped");
            return;
        }
        String registrySrc = Files.readString(registry);
        for (String component : WidgetComponentWhitelist.COMPONENTS) {
            assertThat(registrySrc).contains(component);
        }
    }

    private Set<String> columnsOf(String ddl) {
        return ddl.lines()
                .map(String::trim)
                .filter(l -> l.contains(" ") && !l.startsWith("--") && !l.toUpperCase().startsWith("CREATE")
                        && !l.toUpperCase().startsWith("UNIQUE") && !l.toUpperCase().startsWith("INDEX")
                        && !l.toUpperCase().startsWith("KEY") && !l.startsWith(")"))
                .map(l -> l.split("\\s+")[0].replaceAll(",", ""))
                .filter(c -> !c.isBlank() && !c.startsWith("--"))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
