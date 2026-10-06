package com.fastrag.module.agent.sandbox.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * resolveCredentials 的出参值对象（契约 agent.SandboxConfigStore.resolveCredentials → EffectiveConfig）。
 *
 * <p>把一行 {@code sandbox_config}（JSON 列展开 + 凭据引用解析为环境值）折叠为
 * 生命周期/执行面直接可消费的扁平结构：Docker 字段映射 CreateContext 的容器 HostConfig 入参，
 * Cube/E2B 字段映射控制面创建请求；{@code *ApiKey} 为解析后的实际值（永不回显到任何 VO/HTTP 出参）。</p>
 *
 * <p>同一时刻只有一种后端生效：type=docker 时仅 docker* 字段非空，cube/e2b 同理。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveConfig {

    // ---------- 通用 ----------

    /** 后端类型：docker / cube / e2b / disabled */
    private String type;

    private String configId;

    private String userId;

    private String name;

    private boolean enabled;

    /** 单次执行超时秒数（0 = 内置默认 60s） */
    private int defaultTimeoutSec;

    /** 私网 endpoint 放行（REQ-H16，建连复查仍恒开） */
    private boolean allowPrivateEndpoints;

    /** 网络策略（Cube/E2B 域名 allow/deny 等） */
    private Map<String, Object> networkPolicy;

    /** 注入沙箱的环境变量 */
    @Builder.Default
    private Map<String, String> envVars = new LinkedHashMap<>();

    private String identityFingerprint;

    // ---------- Docker ----------

    /** 远端 daemon 地址（null = 本地 socket/DOCKER_HOST 探测） */
    private String dockerHost;

    /** daemon TLS 证书目录 */
    private String dockerTlsCertPath;

    /** Docker：镜像名；Cube/E2B：模板 ID */
    private String imageOrTemplate;

    /** CPU 核数上限 */
    private Double cpuLimit;

    /** 内存字节上限（配置 memory_limit_mb × 1MiB） */
    private Long memoryBytes;

    /** 进程数上限 */
    private Long pidsLimit;

    /** 网络模式：bridge / none（REQ-H15，保存时已校验） */
    private String networkMode;

    /** 空闲 TTL 秒（null = 调用方默认 1800s） */
    private Long idleTtlSeconds;

    // ---------- Cube ----------

    private String cubeApiUrl;

    private String cubeProxyUrl;

    private String cubeSandboxDomain;

    /** 解析后的实际 API key（引用 → System.getenv；缺失在 resolve 时已 fail-loud） */
    private String cubeApiKey;

    private String cubeTemplateId;

    private Integer cubeHttpTimeoutSec;

    private Long cubeTtlSec;

    private List<String> cubeDnsServers;

    // ---------- E2B ----------

    private String e2bApiUrl;

    private String e2bSandboxDomain;

    /** 解析后的实际 API key（引用 → System.getenv；缺失在 resolve 时已 fail-loud） */
    private String e2bApiKey;

    private String e2bTemplateId;

    private String e2bProxyUrl;

    private Integer e2bHttpTimeoutSec;

    private Long e2bTtlSec;
}
