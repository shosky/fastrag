package com.fastrag.module.tools.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * M3-7 parity 单测：schema.sql 与 SchemaInitializer 的 skill 表 DDL 双处同步防漂移。
 *
 * <p>硬约定：{@code init-scripts/schema.sql}（全量）↔ {@code SchemaInitializer}（运行时幂等增补）
 * 必须一致。本测试把两侧的列名集合解析出来做机器比对——凡 SchemaInitializer 运行时增补的列，
 * schema.sql 的 CREATE TABLE 段必须已有（新装库一次到位），并断言 slug 检索索引存在。</p>
 *
 * <p>纯文本解析，无数据库依赖；文件定位从用户目录向上查仓库根，模块深度变化不受影响。</p>
 */
class SkillDdlParityTest {

    // ---------- 定位 ----------

    /** 从当前工作目录向上查找仓库内相对路径文件（fastrag-backend 根下） */
    private static Path locateBackendFile(String relative) {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.exists(candidate)) return candidate;
            dir = dir.getParent();
        }
        return fail("未找到 " + relative + "（从 " + Paths.get("").toAbsolutePath() + " 向上查找失败）");
    }

    private static String schemaSql() {
        try {
            return Files.readString(locateBackendFile("init-scripts/schema.sql"));
        } catch (IOException e) {
            return fail("schema.sql 读取失败: " + e.getMessage());
        }
    }

    private static String schemaInitializerSource() {
        try {
            return Files.readString(locateBackendFile(
                    "fastrag-bootstrap/src/main/java/com/fastrag/config/SchemaInitializer.java"));
        } catch (IOException e) {
            return fail("SchemaInitializer.java 读取失败: " + e.getMessage());
        }
    }

    // ---------- 解析 ----------

    /** 解析 schema.sql 中指定表的 CREATE TABLE 列名集合（跳过 PRIMARY KEY/INDEX 等约束行） */
    private static Set<String> schemaSqlColumns(String ddl, String table) {
        Matcher m = Pattern.compile(
                        "CREATE TABLE IF NOT EXISTS " + table + " \\((.*?)\\)\\s*ENGINE",
                        Pattern.DOTALL)
                .matcher(ddl);
        if (!m.find()) return fail("schema.sql 缺少 " + table + " 表定义");
        Set<String> cols = new LinkedHashSet<>();
        for (String line : m.group(1).split("\\r?\\n")) {
            String t = line.strip().replaceAll(",\\s*$", "");
            if (t.isEmpty()) continue;
            String head = t.split("\\s+")[0].replace("`", "");
            if (List.of("PRIMARY", "INDEX", "UNIQUE", "KEY", "CONSTRAINT", "FULLTEXT").contains(head)) continue;
            cols.add(head);
        }
        return cols;
    }

    /** 收集 SchemaInitializer 中 addColumnIfNotExists(table, col, ...) 增补的列名集合 */
    private static Set<String> initializerColumns(String src, String table) {
        Set<String> cols = new LinkedHashSet<>();
        Matcher m = Pattern.compile(
                        "addColumnIfNotExists\\(\\s*\"" + table + "\"\\s*,\\s*\"([A-Za-z0-9_]+)\"")
                .matcher(src);
        while (m.find()) cols.add(m.group(1));
        return cols;
    }

    // ---------- 断言 ----------

    @Test
    void skillTable_schemaSqlCoversEveryRuntimeAddedColumn() {
        Set<String> runtime = initializerColumns(schemaInitializerSource(), "skill");
        Set<String> ddl = schemaSqlColumns(schemaSql(), "skill");

        Set<String> missing = new LinkedHashSet<>(runtime);
        missing.removeAll(ddl);

        assertTrue(missing.isEmpty(),
                "schema.sql skill 表缺少 SchemaInitializer 运行时增补的列（M3-7 双处同步被破坏）: " + missing
                        + "\n运行时增补列=" + runtime + "\nschema.sql 列=" + ddl);
    }

    @Test
    void skillTable_slugHasRetrievalIndex() {
        String ddl = schemaSql();
        Matcher m = Pattern.compile(
                        "CREATE TABLE IF NOT EXISTS skill \\((.*?)\\)\\s*ENGINE", Pattern.DOTALL)
                .matcher(ddl);
        assertTrue(m.find(), "schema.sql 缺少 skill 表定义");
        assertTrue(Pattern.compile("UNIQUE\\s+INDEX\\s+uk_slug\\s*\\(\\s*slug\\s*\\)").matcher(m.group(1)).find(),
                "schema.sql skill 表应有 UNIQUE INDEX uk_slug (slug)（2026-10-07 拍板：slug 全局唯一，M3-7 检索路径同源）");
    }

    /** skill_dependency 行与实体（SkillDependency: id/skillId/type/name/required）对齐 */
    @Test
    void skillDependencyTable_matchesEntityColumns() {
        Set<String> ddl = schemaSqlColumns(schemaSql(), "skill_dependency");
        Set<String> entityCols = Set.of("id", "skill_id", "type", "name", "required");
        assertTrue(ddl.containsAll(entityCols),
                "schema.sql skill_dependency 缺列: " + entityCols.stream().filter(c -> !ddl.contains(c)).toList());
    }

    /** skill_scope 行与实体（SkillScope: id/skillId/scopeId/scopeName/enabled）对齐 */
    @Test
    void skillScopeTable_matchesEntityColumns() {
        Set<String> ddl = schemaSqlColumns(schemaSql(), "skill_scope");
        Set<String> entityCols = Set.of("id", "skill_id", "scope_id", "scope_name", "enabled");
        assertTrue(ddl.containsAll(entityCols),
                "schema.sql skill_scope 缺列: " + entityCols.stream().filter(c -> !ddl.contains(c)).toList());
    }

    /** app_skill_binding 行与实体（AppSkillBinding: id/appId/skillId/skillName/params/enabled/createdAt）对齐 */
    @Test
    void appSkillBindingTable_matchesEntityColumns() {
        Set<String> ddl = schemaSqlColumns(schemaSql(), "app_skill_binding");
        Set<String> entityCols = Set.of("id", "app_id", "skill_id", "skill_name", "params", "enabled", "created_at");
        assertTrue(ddl.containsAll(entityCols),
                "schema.sql app_skill_binding 缺列: " + entityCols.stream().filter(c -> !ddl.contains(c)).toList());
    }
}
