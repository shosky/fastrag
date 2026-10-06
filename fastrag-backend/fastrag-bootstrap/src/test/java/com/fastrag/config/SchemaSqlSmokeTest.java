package com.fastrag.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QF-2（idd-review 快修）：schema.sql 全文件冒烟测试。
 * 逐 CREATE TABLE 解析列名，断言无表内重复列（历史上 model_call_log 曾出现两个 status 列，
 * MySQL 建表直接报 duplicate column 错误）。解析器从简：按 CREATE TABLE 块切分、
 * 行首提取列名、容忍 PRIMARY KEY/UNIQUE/INDEX/CONSTRAINT/FOREIGN KEY 等约束行。
 */
class SchemaSqlSmokeTest {

    /** 约束行关键字（大写开头即视为约束，非列定义） */
    private static final Pattern TABLE_HEADER =
            Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?", Pattern.CASE_INSENSITIVE);
    private static final List<String> CONSTRAINT_KEYWORDS =
            List.of("PRIMARY", "UNIQUE", "KEY", "INDEX", "CONSTRAINT", "FOREIGN", "FULLTEXT", "SPATIAL", "CHECK");

    private static Path locateSchemaSql() {
        // surefire 工作目录为模块 basedir（fastrag-bootstrap），schema.sql 在仓库根 init-scripts/
        for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("init-scripts").resolve("schema.sql");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("init-scripts/schema.sql not found from working directory");
    }

    private record Table(String name, List<String> columns) {
    }

    private static List<Table> parseTables(String sql) {
        List<Table> tables = new ArrayList<>();
        Matcher header = TABLE_HEADER.matcher(sql);
        while (header.find()) {
            int openParen = sql.indexOf('(', header.end());
            if (openParen < 0) {
                continue;
            }
            int depth = 0;
            int closeParen = -1;
            for (int i = openParen; i < sql.length(); i++) {
                char c = sql.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        closeParen = i;
                        break;
                    }
                }
            }
            if (closeParen < 0) {
                continue;
            }
            String body = sql.substring(openParen + 1, closeParen);
            List<String> columns = new ArrayList<>();
            for (String rawLine : body.split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                String firstToken = line.split("[\\s(]+", 2)[0].replace("`", "").toUpperCase();
                if (CONSTRAINT_KEYWORDS.contains(firstToken)) {
                    continue;
                }
                columns.add(firstToken.toLowerCase());
            }
            tables.add(new Table(header.group(1).toLowerCase(), columns));
        }
        return tables;
    }

    @Test
    void schemaSql_noDuplicateColumnInsideAnyTable() throws IOException {
        StringBuilder sqlText = new StringBuilder();
        try (Stream<String> lines = Files.lines(locateSchemaSql())) {
            lines.forEach(line -> sqlText.append(line).append('\n'));
        }

        List<Table> tables = parseTables(sqlText.toString());
        assertThat(tables.size()).as("schema.sql 应能解析出全部 CREATE TABLE").isGreaterThanOrEqualTo(50);

        Map<String, List<String>> duplicates = new LinkedHashMap<>();
        for (Table table : tables) {
            Map<String, Integer> seen = new LinkedHashMap<>();
            for (String column : table.columns()) {
                seen.merge(column, 1, Integer::sum);
            }
            List<String> dup = seen.entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .toList();
            if (!dup.isEmpty()) {
                duplicates.put(table.name(), dup);
            }
        }

        assertThat(duplicates).as("表内重复列（会直接导致 MySQL 建表失败）").isEmpty();
    }
}
