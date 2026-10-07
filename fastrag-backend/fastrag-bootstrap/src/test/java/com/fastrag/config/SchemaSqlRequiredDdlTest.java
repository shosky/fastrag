package com.fastrag.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * backlog 单元 S1/S2：schema.sql 必备 DDL 冒烟。
 *
 * <p>S1（P0-7）：sys_api_token 表入 schema.sql——列结构以真库为准
 * （id/kb_id/name/token/permission/expires_at/revoked/created_by/created_at/updated_at，
 * kb_id NOT NULL、permission default read、revoked int default 0），并带 token/kb_id 索引。
 * 真库已存在该表（存量），schema.sql 只服务新装。</p>
 *
 * <p>S2：model_call_log.org_id 列补齐（快修遗留：SchemaInitializer 已幂等增补，schema.sql 缺列）。</p>
 */
class SchemaSqlRequiredDdlTest {

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

    /** 表体内的一行定义：列定义或约束（INDEX/UNIQUE KEY 等） */
    private record Line(String firstToken, String raw) {
    }

    /** 提取指定表的 CREATE TABLE 体（括号配平），逐行拆 firstToken；表不存在返回 null。 */
    private static List<Line> tableBody(String sql, String tableName) {
        String marker = "CREATE TABLE IF NOT EXISTS " + tableName + " (";
        int start = sql.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int openParen = start + marker.length() - 1;
        int depth = 0;
        int closeParen = -1;
        for (int i = openParen; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    closeParen = i;
                    break;
                }
            }
        }
        assertThat(closeParen).as("表 %s 括号未配平", tableName).isGreaterThan(0);
        List<Line> lines = new ArrayList<>();
        for (String rawLine : sql.substring(openParen + 1, closeParen).split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            lines.add(new Line(line.split("[\\s(]+", 2)[0].replace("`", "").toLowerCase(), line));
        }
        return lines;
    }

    private static String schemaSql() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<String> lines = Files.lines(locateSchemaSql())) {
            lines.forEach(line -> sb.append(line).append('\n'));
        }
        return sb.toString();
    }

    @Test
    void sys_api_token表_按真库列结构入schema_sql() throws IOException {
        String sql = schemaSql();
        List<Line> body = tableBody(sql, "sys_api_token");
        assertThat(body).as("S1: sys_api_token 应存在于 init-scripts/schema.sql").isNotNull();

        List<String> columns = body.stream()
                .filter(l -> !l.firstToken().equals("index") && !l.firstToken().equals("unique")
                        && !l.firstToken().equals("key") && !l.firstToken().equals("primary")
                        && !l.firstToken().equals("constraint"))
                .map(Line::firstToken)
                .toList();
        // 真库 DESCRIBE 权威列（2026-10-07 核实）：kb_id NOT NULL 是真库与 h2 快照的关键差异
        assertThat(columns).contains(
                "id", "kb_id", "name", "token", "permission",
                "expires_at", "revoked", "created_by", "created_at", "updated_at");

        String kbIdLine = body.stream().filter(l -> l.firstToken().equals("kb_id"))
                .map(Line::raw).findFirst().orElse("");
        assertThat(kbIdLine).as("真库 kb_id NOT NULL").contains("NOT NULL");
    }

    @Test
    void sys_api_token表_带token与kb_id索引() throws IOException {
        String sql = schemaSql();
        List<Line> body = tableBody(sql, "sys_api_token");
        assertThat(body).isNotNull();

        String indexLines = String.join("\n", body.stream()
                .filter(l -> l.firstToken().equals("index"))
                .map(Line::raw)
                .toList());
        assertThat(indexLines)
                .as("token 等值检索索引（对齐 data-mysql.sql idx_api_token_value）")
                .contains("idx_api_token_value")
                .as("kb_id 归属索引")
                .contains("kb_id");
    }

    @Test
    void model_call_log表_含org_id列() throws IOException {
        String sql = schemaSql();
        List<Line> body = tableBody(sql, "model_call_log");
        assertThat(body).as("model_call_log 应存在于 schema.sql").isNotNull();
        assertThat(body.stream().map(Line::firstToken))
                .as("S2: model_call_log.org_id 与 SchemaInitializer 增补列对齐")
                .contains("org_id");
    }
}
