package com.fastrag.module.application;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S6 DDL 双处同步对账（tdd/new-conversation.md S6；NFR-02）：
 * schema.sql 与 SchemaInitializer 对 new-conversation 四列一索引必须两处齐备。
 * 沿图谱表漂移教训（2026-09-21）——本测试防止其复发。
 */
class NewConversationSchemaSyncTest {

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

    private String read(Path p) throws IOException {
        return Files.readString(p);
    }

    @Test
    void schemaSqlAndSchemaInitializerBothCarryNewConversationDdl() throws IOException {
        Path root = findRepoRoot();
        String schema = read(root.resolve("init-scripts").resolve("schema.sql"));
        Path initializer = root.resolve("fastrag-bootstrap").resolve("src").resolve("main")
                .resolve("java").resolve("com").resolve("fastrag").resolve("config")
                .resolve("SchemaInitializer.java");
        String initJava = read(initializer);

        // schema.sql 侧：app_id 放宽 + 四列 + 一索引
        assertThat(schema).contains("app_id VARCHAR(32) DEFAULT NULL COMMENT '挂载应用；NULL=知识库直聊会话'");
        assertThat(schema).contains("request_state TEXT COMMENT '会话请求态");
        assertThat(schema).contains("is_pinned TINYINT DEFAULT 0");
        assertThat(schema).contains("`references` TEXT COMMENT '消息引用");
        assertThat(schema).contains("iterations INT DEFAULT NULL COMMENT '引擎迭代轮数");
        assertThat(schema).contains("INDEX idx_conv_user (user_id)");

        // SchemaInitializer 侧：同样的五列一索引（幂等增补）
        assertThat(initJava).contains("MODIFY COLUMN app_id VARCHAR(32) DEFAULT NULL");
        assertThat(initJava).contains("addColumnIfNotExists(\"app_conversation\", \"request_state\"");
        assertThat(initJava).contains("addColumnIfNotExists(\"app_conversation\", \"is_pinned\"");
        assertThat(initJava).contains("addColumnIfNotExists(\"app_conversation_message\", \"`references`\"");
        assertThat(initJava).contains("addColumnIfNotExists(\"app_conversation_message\", \"iterations\"");
        assertThat(initJava).contains("idx_conv_user");
    }

    @Test
    void entityCarriesBacktickMappingForReservedColumn() throws IOException {
        // references 为 MySQL 保留字：实体必须反引号映射，防 MyBatis-Plus 生成裸列名
        Path root = findRepoRoot();
        Path entity = root.resolve("fastrag-modules").resolve("fastrag-application")
                .resolve("src").resolve("main").resolve("java").resolve("com").resolve("fastrag")
                .resolve("module").resolve("application").resolve("entity")
                .resolve("AppConversationMessage.java");
        try (Stream<String> lines = Files.lines(entity)) {
            assertThat(lines.anyMatch(l -> l.contains("@TableField(\"`references`\")"))).isTrue();
        }
    }
}
