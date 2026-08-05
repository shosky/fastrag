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
                CREATE TABLE IF NOT EXISTS sys_user_role (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(32) NOT NULL,
                    role_id VARCHAR(32) NOT NULL,
                    INDEX idx_user_id (user_id),
                    UNIQUE KEY uk_user_role (user_id, role_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("Table sys_user_role OK");
        } catch (Exception e) {
            log.error("Failed to create sys_user_role: {}", e.getMessage());
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

        // kb_folder 表新增时间戳字段
        addColumnIfNotExists("kb_folder", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        addColumnIfNotExists("kb_folder", "updated_at", "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

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

        // --- 组织数据隔离：kb / kb_category 增加 org_id 列（存量按创建者组织回填） ---
        addColumnIfNotExists("kb", "org_id", "VARCHAR(32) COMMENT '归属组织（同组织成员默认可见）'");
        try {
            jdbc.execute("UPDATE kb k LEFT JOIN sys_user u ON u.id = k.creator SET k.org_id = u.org_id WHERE k.org_id IS NULL");
            log.info("Backfilled kb.org_id from creator's org");
        } catch (Exception e) {
            log.warn("Failed to backfill kb.org_id: {}", e.getMessage());
        }
        addColumnIfNotExists("kb_category", "org_id", "VARCHAR(32) COMMENT '归属组织（NULL=未分配，仅管理员可见）'");
        // 存量分类按创建者所属组织回填（消灭"未分配"状态，保证所有用户分类可见性完全一致）
        try {
            jdbc.execute("UPDATE kb_category c LEFT JOIN sys_user u ON u.id = c.created_by SET c.org_id = u.org_id WHERE c.org_id IS NULL");
            log.info("Backfilled kb_category.org_id from creator's org");
        } catch (Exception e) {
            log.warn("Failed to backfill kb_category.org_id: {}", e.getMessage());
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

        // 检索日志图谱维度列（兼容已有表；D1 可观测性）
        addColumnIfNotExists("kb_retrieval_log", "graph_entity_count", "INT DEFAULT 0 COMMENT '图谱通道命中结果数'");

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

        // ==================== 应用/工具/运营数据归属列（组织隔离） ====================
        // 存量数据回填为系统级（creator='system' / is_builtin=1），保持全员可见现状；
        // 新数据由创建服务写入真实 creator + org_id
        addColumnIfNotExists("app", "org_id", "VARCHAR(32) COMMENT '归属组织（同组织可见）'");
        try { jdbc.execute("UPDATE app SET owner='system' WHERE owner IS NULL OR owner=''"); } catch (Exception e) { log.warn("backfill app.owner: {}", e.getMessage()); }
        addColumnIfNotExists("tool", "creator", "VARCHAR(32)");
        addColumnIfNotExists("tool", "org_id", "VARCHAR(32)");
        addColumnIfNotExists("tool", "is_builtin", "TINYINT DEFAULT 1");
        try { jdbc.execute("UPDATE tool SET creator='system', is_builtin=1 WHERE creator IS NULL OR creator=''"); } catch (Exception e) { log.warn("backfill tool.creator: {}", e.getMessage()); }
        addColumnIfNotExists("mcp_service", "creator", "VARCHAR(32)");
        addColumnIfNotExists("mcp_service", "org_id", "VARCHAR(32)");
        try { jdbc.execute("UPDATE mcp_service SET creator='system', is_builtin=1 WHERE creator IS NULL OR creator=''"); } catch (Exception e) { log.warn("backfill mcp_service.creator: {}", e.getMessage()); }
        addColumnIfNotExists("skill", "creator", "VARCHAR(32)");
        addColumnIfNotExists("skill", "org_id", "VARCHAR(32)");
        try { jdbc.execute("UPDATE skill SET creator='system', is_builtin=1 WHERE creator IS NULL OR creator=''"); } catch (Exception e) { log.warn("backfill skill.creator: {}", e.getMessage()); }
        addColumnIfNotExists("db_instance", "org_id", "VARCHAR(32)");
        try { jdbc.execute("UPDATE db_instance SET created_by='system' WHERE created_by IS NULL OR created_by=''"); } catch (Exception e) { log.warn("backfill db_instance.created_by: {}", e.getMessage()); }
        addColumnIfNotExists("user_feedback", "org_id", "VARCHAR(32)");
        try { jdbc.execute("UPDATE user_feedback f LEFT JOIN sys_user u ON u.id=f.user_id SET f.org_id=u.org_id"); } catch (Exception e) { log.warn("backfill user_feedback.org_id: {}", e.getMessage()); }
        addColumnIfNotExists("model_call_log", "org_id", "VARCHAR(32)");
        try { jdbc.execute("UPDATE model_call_log m LEFT JOIN sys_user u ON u.username=m.caller SET m.org_id=u.org_id"); } catch (Exception e) { log.warn("backfill model_call_log.org_id: {}", e.getMessage()); }

        // --- 角色存量权限补授（幂等）：分类菜单、运营中心菜单改为细分权限键控制 ---
        grantRolePerms("role_kb_admin",
                "menu:knowledge:categories",
                "menu:operation:kb-analytics", "menu:operation:retrieval-analysis",
                "menu:operation:feedback", "menu:operation:model-monitor",
                "kb:manage");
        grantRolePerms("role_kb_user", "menu:knowledge:categories");

        log.info("Schema initialization completed.");
    }

    /** 为角色补授权限（INSERT IGNORE，重复执行安全） */
    private void grantRolePerms(String roleId, String... permKeys) {
        try {
            for (String key : permKeys) {
                jdbc.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_key) VALUES (?, ?)",
                        roleId, key);
            }
            log.info("Granted {} permission(s) to role {}", permKeys.length, roleId);
        } catch (Exception e) {
            log.warn("Failed to grant permissions to role {}: {}", roleId, e.getMessage());
        }
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
