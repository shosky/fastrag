package com.fastrag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);
    private final JdbcTemplate jdbc;

    public SchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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

        // 添加解析策略模型字段（MySQL 不支持 IF NOT EXISTS，逐个尝试）
        addColumnIfNotExists("kb_parse_strategy", "llm_model", "VARCHAR(128)");
        addColumnIfNotExists("kb_parse_strategy", "vlm_model", "VARCHAR(128)");

        // ===== BPM 业务流程管理（M9，需求 5.4.6.9）10 张表 =====
        ensureBpmTable("bpm_flow_def", """
            CREATE TABLE IF NOT EXISTS bpm_flow_def (
                id              VARCHAR(32) PRIMARY KEY,
                name            VARCHAR(128) NOT NULL,
                description     TEXT,
                category        VARCHAR(64),
                owner_id        VARCHAR(32) NOT NULL,
                visibility      VARCHAR(16) NOT NULL DEFAULT 'private',
                current_version_id VARCHAR(32),
                timeout_ms      INT DEFAULT 86400000,
                trigger_type    VARCHAR(16) DEFAULT 'manual',
                log_snapshot_enabled TINYINT DEFAULT 1,
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_owner (owner_id),
                INDEX idx_visibility (visibility),
                INDEX idx_updated (updated_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义主表'
        """);
        ensureBpmTable("bpm_flow_version", """
            CREATE TABLE IF NOT EXISTS bpm_flow_version (
                id              VARCHAR(32) PRIMARY KEY,
                flow_def_id     VARCHAR(32) NOT NULL,
                version_no      INT NOT NULL,
                status          VARCHAR(16) NOT NULL DEFAULT 'draft',
                canvas_data     LONGTEXT,
                nodes_snapshot  JSON,
                edges_snapshot  JSON,
                remark          VARCHAR(512),
                publisher_id    VARCHAR(32),
                published_at    DATETIME,
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE INDEX uk_def_version (flow_def_id, version_no),
                INDEX idx_status (status),
                INDEX idx_def (flow_def_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程版本表'
        """);
        ensureBpmTable("bpm_flow_node", """
            CREATE TABLE IF NOT EXISTS bpm_flow_node (
                id              VARCHAR(32) PRIMARY KEY,
                version_id      VARCHAR(32) NOT NULL,
                node_key        VARCHAR(64) NOT NULL,
                node_type       VARCHAR(32) NOT NULL,
                name            VARCHAR(128),
                position_x      INT DEFAULT 0,
                position_y      INT DEFAULT 0,
                config          JSON,
                timeout_ms      INT DEFAULT 30000,
                retry_count     INT DEFAULT 0,
                retry_interval_ms INT DEFAULT 1000,
                on_failure      VARCHAR(16) DEFAULT 'fail',
                failure_branch_node_key VARCHAR(64),
                enabled         TINYINT DEFAULT 1,
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE INDEX uk_version_key (version_id, node_key),
                INDEX idx_version_type (version_id, node_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点表'
        """);
        ensureBpmTable("bpm_flow_edge", """
            CREATE TABLE IF NOT EXISTS bpm_flow_edge (
                id              VARCHAR(32) PRIMARY KEY,
                version_id      VARCHAR(32) NOT NULL,
                source_node_key VARCHAR(64) NOT NULL,
                target_node_key VARCHAR(64) NOT NULL,
                edge_kind       VARCHAR(16) DEFAULT 'default',
                condition_expr  TEXT,
                condition_params JSON,
                label           VARCHAR(128),
                priority        INT DEFAULT 0,
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_version_source (version_id, source_node_key),
                INDEX idx_version_target (version_id, target_node_key)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程边表'
        """);
        ensureBpmTable("bpm_flow_instance", """
            CREATE TABLE IF NOT EXISTS bpm_flow_instance (
                id              VARCHAR(32) PRIMARY KEY,
                trace_id        VARCHAR(32),
                flow_def_id     VARCHAR(32) NOT NULL,
                flow_version_id VARCHAR(32) NOT NULL,
                flow_version_no INT NOT NULL,
                status          VARCHAR(16) NOT NULL DEFAULT 'pending',
                input_params    JSON,
                output_params   JSON,
                variables       JSON,
                current_node_keys JSON,
                pending_input_token VARCHAR(64),
                pending_input_form JSON,
                input_timeout_ms INT DEFAULT 86400000,
                input_deadline  DATETIME,
                timeout_at      DATETIME,
                start_user_id   VARCHAR(32),
                trigger_type    VARCHAR(16),
                started_at      DATETIME,
                finished_at     DATETIME,
                duration_ms     BIGINT,
                failure_reason  TEXT,
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_flow_def (flow_def_id),
                INDEX idx_status (status),
                INDEX idx_trace (trace_id),
                INDEX idx_started (started_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表'
        """);
        ensureBpmTable("bpm_flow_instance_event", """
            CREATE TABLE IF NOT EXISTS bpm_flow_instance_event (
                id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                instance_id     VARCHAR(32) NOT NULL,
                trace_id        VARCHAR(32),
                node_key        VARCHAR(64),
                event_type      VARCHAR(32) NOT NULL,
                level           VARCHAR(16) DEFAULT 'info',
                message         TEXT,
                context         JSON,
                input_snapshot  JSON,
                output_snapshot JSON,
                duration_ms     INT,
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_instance (instance_id, created_at),
                INDEX idx_instance_node (instance_id, node_key),
                INDEX idx_event_type (event_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例事件/日志表'
        """);
        ensureBpmTable("bpm_flow_template", """
            CREATE TABLE IF NOT EXISTS bpm_flow_template (
                id              VARCHAR(32) PRIMARY KEY,
                name            VARCHAR(128) NOT NULL,
                category        VARCHAR(32),
                description     TEXT,
                canvas_data     LONGTEXT,
                is_builtin      TINYINT DEFAULT 0,
                thumbnail_url   VARCHAR(256),
                created_by      VARCHAR(32),
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_builtin (is_builtin),
                INDEX idx_category (category)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板表'
        """);
        ensureBpmTable("bpm_flow_test_case", """
            CREATE TABLE IF NOT EXISTS bpm_flow_test_case (
                id              VARCHAR(32) PRIMARY KEY,
                flow_def_id     VARCHAR(32) NOT NULL,
                version_id      VARCHAR(32),
                name            VARCHAR(256),
                inputs          JSON,
                expected_output TEXT,
                actual_output   TEXT,
                match_result    TINYINT,
                last_run_at     DATETIME,
                last_instance_id VARCHAR(32),
                created_by      VARCHAR(32),
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_def (flow_def_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程测试用例表'
        """);
        ensureBpmTable("bpm_flow_permission", """
            CREATE TABLE IF NOT EXISTS bpm_flow_permission (
                id              VARCHAR(32) PRIMARY KEY,
                flow_def_id     VARCHAR(32) NOT NULL,
                subject_type    VARCHAR(16) NOT NULL,
                subject_id      VARCHAR(32) NOT NULL,
                permission      VARCHAR(32) NOT NULL,
                granted_by      VARCHAR(32),
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE INDEX uk_perm (flow_def_id, subject_type, subject_id, permission),
                INDEX idx_subject (subject_type, subject_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程授权表'
        """);
        ensureBpmTable("bpm_node_type_meta", """
            CREATE TABLE IF NOT EXISTS bpm_node_type_meta (
                type            VARCHAR(32) PRIMARY KEY,
                label           VARCHAR(64) NOT NULL,
                icon            VARCHAR(64),
                color           VARCHAR(16),
                category        VARCHAR(32),
                description     VARCHAR(256),
                config_schema   JSON,
                default_config  JSON,
                enabled         TINYINT DEFAULT 1,
                sort_order      INT DEFAULT 0,
                created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点类型元数据表'
        """);

        // ===== BPM 种子数据(节点元数据 + 内置模板)— 表必须先建好 =====
        seedBpmData();

        // ===== 功能清单补齐：FAQ/表格知识/属性/同步/语义配置/向量缓存 =====
        addColumnIfNotExists("kb_qa_pair", "faq_type", "VARCHAR(16) DEFAULT 'common'");
        addColumnIfNotExists("kb_qa_pair", "keywords", "VARCHAR(512)");
        addColumnIfNotExists("kb_qa_pair", "effective_start", "DATETIME");
        addColumnIfNotExists("kb_qa_pair", "effective_end", "DATETIME");
        addColumnIfNotExists("kb_qa_pair", "effective_scope", "VARCHAR(128)");
        addColumnIfNotExists("kb_qa_pair", "related_knowledge_ids", "TEXT");
        addColumnIfNotExists("kb", "kb_type", "VARCHAR(16) DEFAULT 'general'");
        addColumnIfNotExists("kb_chunk", "embedding", "LONGTEXT");
        addColumnIfNotExists("kb_knowledge", "attributes", "TEXT");
        addColumnIfNotExists("model", "threshold", "VARCHAR(512)");
        // 实体 AppSemanticConfig.createdAt 对应列（初期 DDL 遗漏，旧库补列）
        addColumnIfNotExists("app_semantic_config", "created_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");

        // ===== 标准问法/相似问法/问答对（旧库可能缺这几张 schema.sql 后期追加的表，启动自愈） =====
        ensureTable("kb_qa_pair", """
            CREATE TABLE IF NOT EXISTS kb_qa_pair (
                id VARCHAR(32) PRIMARY KEY,
                kb_id VARCHAR(32) NOT NULL,
                file_id VARCHAR(32),
                file_name VARCHAR(256),
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
                source VARCHAR(16) DEFAULT 'manual',
                status VARCHAR(16) DEFAULT 'draft',
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_kb_id (kb_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);
        ensureTable("kb_standard_question", """
            CREATE TABLE IF NOT EXISTS kb_standard_question (
                id VARCHAR(32) PRIMARY KEY,
                kb_id VARCHAR(32) NOT NULL,
                category VARCHAR(64),
                standard_question TEXT NOT NULL,
                answer TEXT,
                hit_count INT DEFAULT 0,
                enabled TINYINT DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_kb_id (kb_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);
        ensureTable("kb_similar_question", """
            CREATE TABLE IF NOT EXISTS kb_similar_question (
                id VARCHAR(32) PRIMARY KEY,
                kb_id VARCHAR(32) NOT NULL,
                standard_question_id VARCHAR(32) NOT NULL,
                question TEXT NOT NULL,
                similarity DECIMAL(5,2) DEFAULT 0.0,
                hit_count INT DEFAULT 0,
                enabled TINYINT DEFAULT 1,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_standard (standard_question_id),
                INDEX idx_kb_id (kb_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);

        ensureBpmTable("kb_answer_table", """
            CREATE TABLE IF NOT EXISTS kb_answer_table (
                id VARCHAR(32) PRIMARY KEY,
                kb_id VARCHAR(32) NOT NULL,
                name VARCHAR(128) NOT NULL,
                description VARCHAR(512),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_at_kb (kb_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表格型应答知识主表'
        """);
        ensureBpmTable("kb_answer_table_column", """
            CREATE TABLE IF NOT EXISTS kb_answer_table_column (
                id VARCHAR(32) PRIMARY KEY,
                table_id VARCHAR(32) NOT NULL,
                name VARCHAR(128) NOT NULL,
                col_key VARCHAR(64) NOT NULL,
                col_type VARCHAR(16) DEFAULT 'text',
                sort_order INT DEFAULT 0,
                INDEX idx_atc_table (table_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表格知识列定义'
        """);
        ensureBpmTable("kb_answer_table_row", """
            CREATE TABLE IF NOT EXISTS kb_answer_table_row (
                id VARCHAR(32) PRIMARY KEY,
                table_id VARCHAR(32) NOT NULL,
                content JSON,
                sort_order INT DEFAULT 0,
                INDEX idx_atr_table (table_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表格知识行内容'
        """);
        ensureBpmTable("kb_attribute_def", """
            CREATE TABLE IF NOT EXISTS kb_attribute_def (
                id VARCHAR(32) PRIMARY KEY,
                kb_id VARCHAR(32) NOT NULL,
                name VARCHAR(128) NOT NULL,
                attr_type VARCHAR(16) DEFAULT 'text',
                required TINYINT DEFAULT 0,
                description VARCHAR(256),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_ad_kb (kb_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库自定义属性定义'
        """);
        ensureBpmTable("kb_sync_config", """
            CREATE TABLE IF NOT EXISTS kb_sync_config (
                id VARCHAR(32) PRIMARY KEY,
                name VARCHAR(128) NOT NULL,
                source_kb_id VARCHAR(32) NOT NULL,
                target_kb_id VARCHAR(32) NOT NULL,
                sync_mode VARCHAR(16) DEFAULT 'incremental',
                interval_minutes INT DEFAULT 60,
                enabled TINYINT DEFAULT 1,
                last_sync_at DATETIME,
                created_by VARCHAR(32),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_ksc_source (source_kb_id),
                INDEX idx_ksc_target (target_kb_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库同步配置'
        """);
        ensureBpmTable("kb_sync_record", """
            CREATE TABLE IF NOT EXISTS kb_sync_record (
                id VARCHAR(32) PRIMARY KEY,
                config_id VARCHAR(32) NOT NULL,
                synced_entries INT DEFAULT 0,
                synced_qa_pairs INT DEFAULT 0,
                status VARCHAR(16) DEFAULT 'success',
                message VARCHAR(512),
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_ksr_config (config_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库同步记录'
        """);
        ensureBpmTable("app_semantic_config", """
            CREATE TABLE IF NOT EXISTS app_semantic_config (
                id VARCHAR(32) PRIMARY KEY,
                app_id VARCHAR(32) NOT NULL,
                intent_patterns JSON,
                custom_synonyms JSON,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE INDEX idx_asc_app (app_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用语义理解/语义定制配置'
        """);

        log.info("Schema initialization completed.");
    }

    /** BPM 节点元数据 + 内置模板种子数据(SSOT 前端画布节点库 + 内置模板)
     *  仅在表为空时插入,避免每次启动重复写入 */
    private void seedBpmData() {
        // ===== 1. 节点类型元数据 (9 个 MVP 节点) =====
        try {
            Integer nodeCount = jdbc.queryForObject("SELECT COUNT(*) FROM bpm_node_type_meta", Integer.class);
            if (nodeCount != null && nodeCount > 0) {
                log.info("bpm_node_type_meta already seeded ({} rows), skip", nodeCount);
            } else {
                jdbc.update("""
                    INSERT INTO bpm_node_type_meta (type, label, icon, color, category, description, config_schema, default_config, enabled, sort_order) VALUES
                    ('start',        '开始',       'VideoPlay',  '#67C23A', 'control', '标识业务流程的起始点', CAST('[{"key":"inputParams","label":"输入参数(JSON)","type":"json","defaultValue":[]}]' AS JSON), CAST('{"inputParams":[]}' AS JSON), 1, 10),
                    ('end',          '结束',       'VideoPause', '#F56C6C', 'control', '标识业务流程的终止点', CAST('[{"key":"outputMapping","label":"输出映射(JSON)","type":"json","defaultValue":{}}]' AS JSON), CAST('{"outputMapping":{}}' AS JSON), 1, 11),
                    ('user_input',   '用户输入',   'EditPen',    '#E6A23C', 'input',   '在流程中获取用户输入信息', CAST('[{"key":"fields","label":"输入字段(JSON)","type":"json","defaultValue":[]},{"key":"inputTimeoutMs","label":"输入超时(ms)","type":"number","defaultValue":86400000}]' AS JSON), CAST('{"fields":[{"key":"input","label":"请输入","type":"text","required":true}],"inputTimeoutMs":86400000}' AS JSON), 1, 20),
                    ('llm',          '大模型',     'MagicStick', '#409EFF', 'execute', '调用大语言模型生成文本', CAST('[{"key":"model","label":"模型","type":"select","options":["qwen2.5","deepseek-v3","gpt-4o"],"defaultValue":"qwen2.5"},{"key":"systemPrompt","label":"系统提示词","type":"textarea"},{"key":"userPrompt","label":"用户提示词模板","type":"textarea"},{"key":"temperature","label":"温度","type":"slider","min":0,"max":2,"step":0.1,"defaultValue":0.7},{"key":"maxTokens","label":"最大Tokens","type":"number","defaultValue":2048}]' AS JSON), CAST('{"model":"qwen2.5","temperature":0.7,"maxTokens":2048}' AS JSON), 1, 30),
                    ('kb_retrieval', '知识库检索', 'Collection', '#E6A23C', 'execute', '检索知识库并返回 TopK 文档片段', CAST('[{"key":"kbId","label":"知识库ID","type":"input"},{"key":"topK","label":"Top K","type":"number","min":1,"max":50,"defaultValue":5},{"key":"similarityThreshold","label":"相似度阈值","type":"slider","min":0,"max":1,"step":0.05,"defaultValue":0.5},{"key":"mode","label":"检索策略","type":"select","options":["vector","fulltext","hybrid"],"defaultValue":"hybrid"}]' AS JSON), CAST('{"kbId":"","topK":5,"similarityThreshold":0.5,"mode":"hybrid"}' AS JSON), 1, 31),
                    ('intent',       '意图识别',   'Aim',        '#9B59B6', 'execute', '对输入做意图分类', CAST('[{"key":"model","label":"模型","type":"input","defaultValue":"qwen2.5"},{"key":"labels","label":"意图标签(JSON数组)","type":"json","defaultValue":[]},{"key":"confidenceThreshold","label":"置信度阈值","type":"slider","min":0,"max":1,"step":0.05,"defaultValue":0.8}]' AS JSON), CAST('{"model":"qwen2.5","labels":[],"confidenceThreshold":0.8}' AS JSON), 1, 32),
                    ('http',         'HTTP请求',   'Promotion',  '#FF6B6B', 'execute', '调用外部 HTTP 服务接口', CAST('[{"key":"url","label":"请求URL","type":"input"},{"key":"method","label":"请求方法","type":"select","options":["GET","POST","PUT","DELETE"],"defaultValue":"GET"},{"key":"headers","label":"请求头(JSON)","type":"json","defaultValue":{}},{"key":"body","label":"请求体","type":"textarea"},{"key":"timeoutMs","label":"超时(ms)","type":"number","defaultValue":5000}]' AS JSON), CAST('{"url":"","method":"GET","headers":{},"body":"","timeoutMs":5000}' AS JSON), 1, 33),
                    ('condition',    '条件分支',   'Switch',     '#1ABC9C', 'control', '根据条件判断流程走向', CAST('[{"key":"expression","label":"SpEL 条件表达式","type":"input","placeholder":"#vars[''label'']==''通过''"}]' AS JSON), CAST('{"expression":""}' AS JSON), 1, 40),
                    ('subflow',      '子流程',     'Link',       '#00B4D8', 'execute', '将一个已有业务流程作为子流程嵌入', CAST('[{"key":"subflowKey","label":"子流程(名称/ID)","type":"input"},{"key":"subflowVersionNo","label":"子流程版本号","type":"number"},{"key":"inputMapping","label":"输入映射(JSON)","type":"json","defaultValue":{}},{"key":"outputMapping","label":"输出映射(JSON)","type":"json","defaultValue":{}}]' AS JSON), CAST('{"subflowKey":"","subflowVersionNo":null,"inputMapping":{},"outputMapping":{}}' AS JSON), 1, 34)
                """);
                log.info("Seeded bpm_node_type_meta with 9 MVP node types");
            }
        } catch (Exception e) {
            log.error("Failed to seed bpm_node_type_meta: {}", e.getMessage());
        }

        // ===== 2. 内置模板 (5 个,设计文档 §14) =====
        try {
            Integer tplCount = jdbc.queryForObject("SELECT COUNT(*) FROM bpm_flow_template", Integer.class);
            if (tplCount != null && tplCount > 0) {
                log.info("bpm_flow_template already seeded ({} rows), skip", tplCount);
            } else {
                // 模板 1: 空白模板 start → end
                jdbc.update("""
                    INSERT INTO bpm_flow_template (id, name, category, description, canvas_data, is_builtin, created_by) VALUES
                    ('bpmtpl_blank', '空白模板', 'blank', '从零开始搭建业务流程，仅含开始与结束节点',
                    '{\"nodes\":[{\"nodeKey\":\"node_start\",\"nodeType\":\"start\",\"name\":\"开始\",\"positionX\":120,\"positionY\":180,\"config\":{\"inputParams\":[]}},{\"nodeKey\":\"node_end\",\"nodeType\":\"end\",\"name\":\"结束\",\"positionX\":480,\"positionY\":180,\"config\":{\"outputMapping\":{}}}],\"edges\":[{\"sourceNodeKey\":\"node_start\",\"targetNodeKey\":\"node_end\",\"edgeKind\":\"default\",\"label\":\"\",\"conditionExpr\":\"\",\"priority\":0}]}',
                    1, 'admin')
                """);
                // 模板 2: RAG 检索问答
                jdbc.update("""
                    INSERT INTO bpm_flow_template (id, name, category, description, canvas_data, is_builtin, created_by) VALUES
                    ('bpmtpl_rag_qa', 'RAG 检索问答', 'rag', '用户输入问题 → 知识库混合检索 → 大模型生成回答',
                    '{\"nodes\":[{\"nodeKey\":\"node_start\",\"nodeType\":\"start\",\"name\":\"开始\",\"positionX\":80,\"positionY\":200,\"config\":{\"inputParams\":[{\"key\":\"query\",\"label\":\"问题\",\"type\":\"text\",\"required\":true}]}},{\"nodeKey\":\"node_retrieval\",\"nodeType\":\"kb_retrieval\",\"name\":\"知识库检索\",\"positionX\":320,\"positionY\":200,\"config\":{\"kbId\":\"\",\"topK\":5,\"similarityThreshold\":0.5,\"mode\":\"hybrid\"}},{\"nodeKey\":\"node_llm\",\"nodeType\":\"llm\",\"name\":\"大模型回答\",\"positionX\":560,\"positionY\":200,\"config\":{\"model\":\"qwen2.5\",\"systemPrompt\":\"你是一个智能助手，请基于给定的知识库上下文回答问题。\",\"userPrompt\":\"根据上下文回答问题\",\"temperature\":0.7,\"maxTokens\":2048}},{\"nodeKey\":\"node_end\",\"nodeType\":\"end\",\"name\":\"结束\",\"positionX\":800,\"positionY\":200,\"config\":{\"outputMapping\":{}}}],\"edges\":[{\"sourceNodeKey\":\"node_start\",\"targetNodeKey\":\"node_retrieval\",\"edgeKind\":\"default\"},{\"sourceNodeKey\":\"node_retrieval\",\"targetNodeKey\":\"node_llm\",\"edgeKind\":\"default\"},{\"sourceNodeKey\":\"node_llm\",\"targetNodeKey\":\"node_end\",\"edgeKind\":\"default\"}]}',
                    1, 'admin')
                """);
                // 模板 3: HTTP 集成
                jdbc.update("""
                    INSERT INTO bpm_flow_template (id, name, category, description, canvas_data, is_builtin, created_by) VALUES
                    ('bpmtpl_http', 'HTTP 集成', 'integration', '通过 HTTP 请求调用外部服务并返回结果',
                    '{\"nodes\":[{\"nodeKey\":\"node_start\",\"nodeType\":\"start\",\"name\":\"开始\",\"positionX\":100,\"positionY\":200,\"config\":{\"inputParams\":[{\"key\":\"orderId\",\"label\":\"订单ID\",\"type\":\"text\",\"required\":true}]}},{\"nodeKey\":\"node_http\",\"nodeType\":\"http\",\"name\":\"查询订单\",\"positionX\":360,\"positionY\":200,\"config\":{\"url\":\"https://api.example.com/orders\",\"method\":\"GET\",\"timeoutMs\":5000}},{\"nodeKey\":\"node_end\",\"nodeType\":\"end\",\"name\":\"结束\",\"positionX\":640,\"positionY\":200,\"config\":{\"outputMapping\":{}}}],\"edges\":[{\"sourceNodeKey\":\"node_start\",\"targetNodeKey\":\"node_http\",\"edgeKind\":\"default\"},{\"sourceNodeKey\":\"node_http\",\"targetNodeKey\":\"node_end\",\"edgeKind\":\"default\"}]}',
                    1, 'admin')
                """);
                log.info("Seeded bpm_flow_template with built-in templates");
            }
        } catch (Exception e) {
            log.error("Failed to seed bpm_flow_template: {}", e.getMessage());
        }
    }

    /** BPM 表:逐张确保存在并打印结果(便于 BpmTimeoutScanner 等调度任务立即可用) */
    private void ensureBpmTable(String name, String ddl) {
        try {
            jdbc.execute(ddl);
            log.info("BPM table {} OK", name);
        } catch (Exception e) {
            log.error("Failed to create BPM table {}: {}", name, e.getMessage());
        }
    }

    /** 通用幂等建表：确保表存在（已存在则跳过），用于旧库缺失表的启动自愈 */
    private void ensureTable(String name, String ddl) {
        try {
            jdbc.execute(ddl);
            log.info("Table {} OK", name);
        } catch (Exception e) {
            log.error("Failed to create table {}: {}", name, e.getMessage());
        }
    }

    private void addColumnIfNotExists(String table, String column, String type) {
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            log.info("Added column {}.{}", table, column);
        } catch (Exception e) {
            // Column already exists - MySQL error code 1060
            if (e.getMessage() != null && e.getMessage().contains("1060")) {
                log.info("Column {}.{} already exists", table, column);
            } else {
                log.warn("Failed to add column {}.{}: {}", table, column, e.getMessage());
            }
        }
    }
}
