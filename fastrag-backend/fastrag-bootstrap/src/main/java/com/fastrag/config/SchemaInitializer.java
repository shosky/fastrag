package com.fastrag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public SchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void initSchema() {
        log.info("Checking and creating missing tables...");

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sensitive_word (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    word VARCHAR(128) NOT NULL,
                    category VARCHAR(64),
                    level VARCHAR(16),
                    replacement VARCHAR(128) DEFAULT '***',
                    enabled TINYINT DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table sensitive_word OK");
        } catch (Exception e) {
            log.error("Failed to create sensitive_word: {}", e.getMessage());
        }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_team (
                    id VARCHAR(32) PRIMARY KEY,
                    name VARCHAR(128) NOT NULL,
                    description VARCHAR(256),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table sys_team OK");
        } catch (Exception e) {
            log.error("Failed to create sys_team: {}", e.getMessage());
        }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_team_member (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    team_id VARCHAR(32) NOT NULL,
                    user_id VARCHAR(32) NOT NULL,
                    INDEX idx_team_id (team_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table sys_team_member OK");
        } catch (Exception e) {
            log.error("Failed to create sys_team_member: {}", e.getMessage());
        }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_dictionary (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    dict_type VARCHAR(64) NOT NULL,
                    dict_key VARCHAR(128) NOT NULL,
                    dict_value TEXT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table sys_dictionary OK");
        } catch (Exception e) {
            log.error("Failed to create sys_dictionary: {}", e.getMessage());
        }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS kb_publish_history (
                    id VARCHAR(32) PRIMARY KEY,
                    kb_id VARCHAR(32) NOT NULL,
                    knowledge_id VARCHAR(32) NOT NULL,
                    version INT,
                    publish_type VARCHAR(16),
                    online_version LONGTEXT,
                    offline_version LONGTEXT,
                    status VARCHAR(16),
                    scheduled_at DATETIME,
                    published_at DATETIME,
                    operator VARCHAR(32),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_kb_id (kb_id),
                    INDEX idx_knowledge_id (knowledge_id),
                    INDEX idx_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table kb_publish_history OK");
        } catch (Exception e) {
            log.error("Failed to create kb_publish_history: {}", e.getMessage());
        }

        // 添加解析策略模型字段（MySQL 不支持 IF NOT EXISTS，逐个尝试）
        addColumnIfNotExists("kb_parse_strategy", "llm_model", "VARCHAR(128)");
        addColumnIfNotExists("kb_parse_strategy", "vlm_model", "VARCHAR(128)");

        // --- Agent 模块表 ---
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS agent (
                    id VARCHAR(64) PRIMARY KEY,
                    slug VARCHAR(128) NOT NULL,
                    name VARCHAR(256) NOT NULL,
                    backend_id VARCHAR(128),
                    description TEXT,
                    icon VARCHAR(512),
                    pics JSON,
                    config_json JSON,
                    share_config JSON,
                    is_default TINYINT DEFAULT 0,
                    is_subagent TINYINT DEFAULT 0,
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE INDEX uk_slug (slug),
                    INDEX idx_backend_id (backend_id),
                    INDEX idx_is_default (is_default)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table agent OK");
        } catch (Exception e) {
            log.error("Failed to create agent: {}", e.getMessage());
        }

        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS agent_run (
                    id VARCHAR(64) PRIMARY KEY,
                    thread_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64) NOT NULL,
                    uid VARCHAR(64),
                    request_id VARCHAR(128),
                    input_payload JSON,
                    status VARCHAR(32) DEFAULT 'pending',
                    run_type VARCHAR(32) DEFAULT 'chat',
                    parent_agent_run_id VARCHAR(64),
                    conversation_id BIGINT,
                    checkpoint_thread_id VARCHAR(128),
                    error_message TEXT,
                    error_type VARCHAR(64),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    finished_at DATETIME,
                    UNIQUE INDEX uk_request_id (request_id),
                    INDEX idx_thread_id (thread_id),
                    INDEX idx_agent_id (agent_id),
                    INDEX idx_status (status),
                    INDEX idx_thread_status (thread_id, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table agent_run OK");
        } catch (Exception e) {
            log.error("Failed to create agent_run: {}", e.getMessage());
        }

        // --- Skill 表新增字段 ---
        addColumnIfNotExists("skill", "slug", "VARCHAR(128)");
        addColumnIfNotExists("skill", "source_type", "VARCHAR(16) DEFAULT 'custom'");
        addColumnIfNotExists("skill", "dir_path", "VARCHAR(512)");
        addColumnIfNotExists("skill", "dependencies", "JSON");
        addColumnIfNotExists("skill", "content_hash", "VARCHAR(64)");
        addColumnIfNotExists("skill", "is_builtin", "TINYINT DEFAULT 0");
        addColumnIfNotExists("skill", "metadata", "JSON");
        addColumnIfNotExists("skill", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");

        // --- MCP Service 表新增字段 ---
        addColumnIfNotExists("mcp_service", "slug", "VARCHAR(128)");
        addColumnIfNotExists("mcp_service", "transport", "VARCHAR(16) DEFAULT 'sse'");
        addColumnIfNotExists("mcp_service", "command", "VARCHAR(512)");
        addColumnIfNotExists("mcp_service", "args", "JSON");
        addColumnIfNotExists("mcp_service", "env", "JSON");
        addColumnIfNotExists("mcp_service", "is_builtin", "TINYINT DEFAULT 0");
        addColumnIfNotExists("mcp_service", "config_hash", "VARCHAR(64)");
        addColumnIfNotExists("mcp_service", "metadata", "JSON");
        addColumnIfNotExists("mcp_service", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

        // --- MCP Tool 表新增字段 ---
        addColumnIfNotExists("mcp_tool", "tool_id", "VARCHAR(256)");
        addColumnIfNotExists("mcp_tool", "enabled", "TINYINT DEFAULT 1");

        // 添加 FULLTEXT 索引（用于全文检索）
        try {
            jdbc.execute("CREATE FULLTEXT INDEX idx_content_fulltext ON kb_chunk(content) WITH PARSER ngram");
            log.info("FULLTEXT index idx_content_fulltext created on kb_chunk.content");
        } catch (Exception e) {
            int errorCode = getRootSqlErrorCode(e);
            if (errorCode == 1061 || errorCode == 1146) {
                // 1061 = duplicate index; 1146 = table doesn't exist yet (deferred by migration)
                log.info("FULLTEXT index idx_content_fulltext: {}", errorCode == 1061 ? "already exists" : "table kb_chunk not ready, will be created by migration");
            } else {
                log.warn("FULLTEXT index creation failed: {}", e.getMessage());
            }
        }

        // 兼容：retrieval_metrics 早期定义为 JSON，但实际存的是普通字符串
        try {
            jdbc.execute("ALTER TABLE kb_evaluation_result MODIFY COLUMN retrieval_metrics TEXT COMMENT '检索指标字符串'");
            log.info("Changed kb_evaluation_result.retrieval_metrics from JSON to TEXT");
        } catch (Exception e) {
            log.info("retrieval_metrics column already compatible: {}", e.getMessage());
        }

        // 添加评估结果结构化召回列（兼容已有表）
        addColumnIfNotExists("kb_evaluation_result", "recall_at_1", "DECIMAL(5,4) DEFAULT NULL COMMENT 'Recall@1'");
        addColumnIfNotExists("kb_evaluation_result", "recall_at_3", "DECIMAL(5,4) DEFAULT NULL COMMENT 'Recall@3'");
        addColumnIfNotExists("kb_evaluation_result", "recall_at_5", "DECIMAL(5,4) DEFAULT NULL COMMENT 'Recall@5'");
        addColumnIfNotExists("kb_evaluation_result", "recall_at_10", "DECIMAL(5,4) DEFAULT NULL COMMENT 'Recall@10'");

        // 添加 model 表缺失列（兼容已有表）
        addColumnIfNotExists("model", "context_window", "INT DEFAULT 4096 COMMENT '上下文窗口大小'");
        addColumnIfNotExists("model", "enable_thinking", "TINYINT(1) DEFAULT 0 COMMENT '启用思考模式'");

        // --- 邮箱验证码表 ---
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS email_verification (
                    id VARCHAR(32) PRIMARY KEY,
                    email VARCHAR(128) NOT NULL,
                    code VARCHAR(8) NOT NULL,
                    purpose VARCHAR(16) NOT NULL,
                    expires_at DATETIME NOT NULL,
                    used TINYINT DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_email_purpose (email, purpose)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table email_verification OK");
        } catch (Exception e) {
            log.error("Failed to create email_verification: {}", e.getMessage());
        }

        // kb_graph_index 表新增字段
        addColumnIfNotExists("kb_graph_index", "failed_chunks", "INT DEFAULT 0 COMMENT '构建失败的切片数'");

        log.info("Schema initialization completed.");
    }

    private void addColumnIfNotExists(String table, String column, String type) {
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            log.info("Added column {}.{}", table, column);
        } catch (Exception e) {
            // Column already exists - MySQL error code 1060
            if (getRootSqlErrorCode(e) == 1060) {
                log.info("Column {}.{} already exists", table, column);
            } else {
                log.warn("Failed to add column {}.{}: {}", table, column, e.getMessage());
            }
        }
    }

    /**
     * 提取根因的 MySQL 错误码，避免字符串匹配脆弱性。
     * 例如 Connector/J 8.x 抛出 "Duplicate column name 'xxx'" 时，
     * 错误码 1060 不在 message 中，必须通过 SQLException#getErrorCode 获取。
     */
    private static int getRootSqlErrorCode(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        if (t instanceof SQLException sqlEx) {
            return sqlEx.getErrorCode();
        }
        return -1;
    }
}
