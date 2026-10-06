package com.fastrag.module.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 沙箱配置出参 VO（读取恒经脱敏，REQ-H06）。
 *
 * <p>脱敏语义：密钥字段（cubeConfig/e2bConfig 内的 {@code api_key_ref}）只回显引用名——
 * 本仓凭据引用式存储本就不落值（ADR-0007），VO 上不存在 resolve 后的实际值字段，
 * 任何读取路径密钥值不出服务端。</p>
 *
 * <p>JSON 列以 Map 形态出参（服务端经 Jackson 反序列化）；provider 块内的键名
 * 与 data.md「sandbox_config」节的 JSON 结构一致。</p>
 */
@Data
public class SandboxConfigVO {

    private String id;

    private String userId;

    private String name;

    /** docker / cube / e2b / disabled */
    private String sandboxType;

    private Boolean enabled;

    private Integer defaultTimeoutSec;

    private Boolean allowPrivateEndpoints;

    /** {allow_internet, allow_domains[], deny_domains[], inbound_closed} */
    private Map<String, Object> networkPolicy;

    private Map<String, String> envVars;

    /** {image, host, tls_cert_path, cpu_limit, memory_limit_mb, pids_limit, network_mode, idle_ttl_sec} */
    private Map<String, Object> dockerConfig;

    /** api_key_ref 为引用名（脱敏恒开，REQ-H06） */
    private Map<String, Object> cubeConfig;

    /** api_key_ref 为引用名（脱敏恒开，REQ-H06） */
    private Map<String, Object> e2bConfig;

    private String identityFingerprint;

    private LocalDateTime cordonAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
