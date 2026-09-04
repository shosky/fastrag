-- FastRAG 业务流程管理（fastrag-bpm）建表脚本
-- 适用日期：2026-09-02
-- 表前缀：bpm_  /  错误码区间 40000-40999
USE fastrag2;

-- ==================== BPM 业务流程管理 ====================

-- 流程定义主表
CREATE TABLE IF NOT EXISTS bpm_flow_def (
    id              VARCHAR(32) PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    category        VARCHAR(64),
    owner_id        VARCHAR(32) NOT NULL,
    visibility      VARCHAR(16) NOT NULL DEFAULT 'private'
                    COMMENT 'private/team/public',
    current_version_id VARCHAR(32),
    timeout_ms      INT DEFAULT 86400000
                    COMMENT '流程整体超时(默认 24h)',
    trigger_type    VARCHAR(16) DEFAULT 'manual'
                    COMMENT 'manual/api/scheduled/event',
    log_snapshot_enabled TINYINT DEFAULT 1
                    COMMENT '是否记录节点输入输出快照',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_owner (owner_id),
    INDEX idx_visibility (visibility),
    INDEX idx_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义主表';

-- 流程版本表
CREATE TABLE IF NOT EXISTS bpm_flow_version (
    id              VARCHAR(32) PRIMARY KEY,
    flow_def_id     VARCHAR(32) NOT NULL,
    version_no      INT NOT NULL
                    COMMENT '版本号,递增整数',
    status          VARCHAR(16) NOT NULL DEFAULT 'draft'
                    COMMENT 'draft/published/archived/disabled',
    canvas_data     LONGTEXT
                    COMMENT '整图快照 JSON,便于回滚',
    nodes_snapshot  JSON
                    COMMENT '节点快照数组,冗余存储便于 SQL',
    edges_snapshot  JSON
                    COMMENT '边快照数组',
    remark          VARCHAR(512)
                    COMMENT '版本说明/发布备注',
    publisher_id    VARCHAR(32),
    published_at    DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_def_version (flow_def_id, version_no),
    INDEX idx_status (status),
    INDEX idx_def (flow_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程版本表';

-- 流程节点表
CREATE TABLE IF NOT EXISTS bpm_flow_node (
    id              VARCHAR(32) PRIMARY KEY,
    version_id      VARCHAR(32) NOT NULL,
    node_key        VARCHAR(64) NOT NULL
                    COMMENT '节点在版本内唯一',
    node_type       VARCHAR(32) NOT NULL,
    name            VARCHAR(128),
    position_x      INT DEFAULT 0,
    position_y      INT DEFAULT 0,
    config          JSON
                    COMMENT '节点参数,统一 JSON',
    timeout_ms      INT DEFAULT 30000
                    COMMENT '节点级超时,0=不超时',
    retry_count     INT DEFAULT 0
                    COMMENT '重试次数',
    retry_interval_ms INT DEFAULT 1000,
    on_failure      VARCHAR(16) DEFAULT 'fail'
                    COMMENT 'fail/ignore/branch',
    failure_branch_node_key VARCHAR(64),
    enabled         TINYINT DEFAULT 1,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_version_key (version_id, node_key),
    INDEX idx_version_type (version_id, node_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点表';

-- 流程边表
CREATE TABLE IF NOT EXISTS bpm_flow_edge (
    id              VARCHAR(32) PRIMARY KEY,
    version_id      VARCHAR(32) NOT NULL,
    source_node_key VARCHAR(64) NOT NULL,
    target_node_key VARCHAR(64) NOT NULL,
    edge_kind       VARCHAR(16) DEFAULT 'default'
                    COMMENT 'default/condition/parallel/exception',
    condition_expr  TEXT
                    COMMENT 'SpEL 条件表达式',
    condition_params JSON
                    COMMENT '条件表达式中的参数定义',
    label           VARCHAR(128)
                    COMMENT '边标签,如 通过/不通过',
    priority        INT DEFAULT 0
                    COMMENT '条件边求值顺序',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_version_source (version_id, source_node_key),
    INDEX idx_version_target (version_id, target_node_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程边表';

-- 流程实例表
CREATE TABLE IF NOT EXISTS bpm_flow_instance (
    id              VARCHAR(32) PRIMARY KEY,
    trace_id        VARCHAR(32)
                    COMMENT '链路追踪 ID,默认等于 id',
    flow_def_id     VARCHAR(32) NOT NULL,
    flow_version_id VARCHAR(32) NOT NULL,
    flow_version_no INT NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'pending'
                    COMMENT 'pending/running/paused/completed/cancelled/failed',
    input_params    JSON
                    COMMENT '启动输入参数',
    output_params   JSON
                    COMMENT '最终输出',
    variables       JSON
                    COMMENT '流程全局变量',
    current_node_keys JSON
                    COMMENT '当前正在执行的节点列表',
    pending_input_token VARCHAR(64)
                    COMMENT '等待用户输入的 token',
    pending_input_form JSON
                    COMMENT '用户输入节点的表单 schema',
    input_timeout_ms INT DEFAULT 86400000,
    input_deadline  DATETIME,
    timeout_at      DATETIME
                    COMMENT '整体超时截止时间',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表';

-- 流程实例事件/日志表
CREATE TABLE IF NOT EXISTS bpm_flow_instance_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id     VARCHAR(32) NOT NULL,
    trace_id        VARCHAR(32),
    node_key        VARCHAR(64),
    event_type      VARCHAR(32) NOT NULL
                    COMMENT 'FLOW_STARTED/FLOW_COMPLETED/FLOW_FAILED/FLOW_PAUSED/FLOW_RESUMED/FLOW_CANCELLED/NODE_STARTED/NODE_COMPLETED/NODE_FAILED/NODE_TIMEOUT/INPUT_REQUESTED/INPUT_RECEIVED',
    level           VARCHAR(16) DEFAULT 'info'
                    COMMENT 'debug/info/warn/error',
    message         TEXT,
    context         JSON
                    COMMENT '事件额外上下文',
    input_snapshot  JSON,
    output_snapshot JSON,
    duration_ms     INT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_instance (instance_id, created_at),
    INDEX idx_instance_node (instance_id, node_key),
    INDEX idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例事件/日志表';

-- 流程模板表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板表';

-- 流程测试用例表
CREATE TABLE IF NOT EXISTS bpm_flow_test_case (
    id              VARCHAR(32) PRIMARY KEY,
    flow_def_id     VARCHAR(32) NOT NULL,
    version_id      VARCHAR(32),
    name            VARCHAR(256),
    inputs          JSON
                    COMMENT '启动输入参数',
    expected_output TEXT
                    COMMENT '期望最终输出',
    actual_output   TEXT
                    COMMENT '最近一次实际输出',
    match_result    TINYINT
                    COMMENT '1=匹配,0=不匹配,NULL=未运行',
    last_run_at     DATETIME,
    last_instance_id VARCHAR(32),
    created_by      VARCHAR(32),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_def (flow_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程测试用例表';

-- 流程授权表
CREATE TABLE IF NOT EXISTS bpm_flow_permission (
    id              VARCHAR(32) PRIMARY KEY,
    flow_def_id     VARCHAR(32) NOT NULL,
    subject_type    VARCHAR(16) NOT NULL
                    COMMENT 'user/role',
    subject_id      VARCHAR(32) NOT NULL,
    permission      VARCHAR(32) NOT NULL
                    COMMENT 'view/edit/execute/publish/delete',
    granted_by      VARCHAR(32),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_perm (flow_def_id, subject_type, subject_id, permission),
    INDEX idx_subject (subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程授权表';

-- 节点类型元数据表
CREATE TABLE IF NOT EXISTS bpm_node_type_meta (
    type            VARCHAR(32) PRIMARY KEY,
    label           VARCHAR(64) NOT NULL,
    icon            VARCHAR(64),
    color           VARCHAR(16),
    category        VARCHAR(32)
                    COMMENT 'control/execute/input/terminal',
    description     VARCHAR(256),
    config_schema   JSON
                    COMMENT '参数 schema:[{key,label,type,defaultValue,options,validation}]',
    default_config  JSON,
    enabled         TINYINT DEFAULT 1,
    sort_order      INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点类型元数据表(可与后端 enum 并存,提供 /api/bpm/node-types 暴露)';