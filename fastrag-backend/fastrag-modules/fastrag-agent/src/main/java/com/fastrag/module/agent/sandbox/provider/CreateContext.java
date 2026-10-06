package com.fastrag.module.agent.sandbox.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实例创建上下文（agent.SandboxProviderGateway.create 入参）。
 * <p>
 * Docker 适配器把本结构映射为容器 HostConfig（加固基线为适配器 Pre 不变量，REQ-H14）；
 * Cube/E2B 适配器映射为模板创建请求（provider TTL 传递）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContext {

    /** 创建方沙箱配置 ID（写入 provider metadata，孤儿对账与库存过滤依据，REQ-H11） */
    private String configId;

    /** 会话键 "userId:sessionId"（写入 provider metadata） */
    private String sessionKey;

    private String userId;

    private String sessionId;

    /** Docker：镜像名；Cube/E2B：模板 ID */
    private String imageOrTemplate;

    /** CPU 核数上限（Docker） */
    @Builder.Default
    private double cpuLimit = 2.0;

    /** 内存字节上限（Docker；= memorySwap 即禁 swap） */
    @Builder.Default
    private long memoryBytes = 2L * 1024 * 1024 * 1024;

    /** 进程数上限（Docker） */
    @Builder.Default
    private long pidsLimit = 512;

    /** Docker 网络模式：bridge / none（REQ-H15，保存时已校验） */
    @Builder.Default
    private String networkMode = "bridge";

    /** 注入沙箱的环境变量（来自配置 env_vars） */
    @Builder.Default
    private Map<String, String> envVars = new LinkedHashMap<>();

    /** 空闲 TTL（Docker：写容器 label；Cube/E2B：作为 provider TTL hint 传递） */
    @Builder.Default
    private Duration idleTtl = Duration.ofSeconds(1800);

    /** 远端 daemon 地址（Docker 专用；null = 本地 socket/DOCKER_HOST 探测） */
    private String dockerHost;

    /** daemon TLS 证书目录（ca.pem/cert.pem/key.pem；远端 TCP 必填） */
    private String dockerTlsCertPath;

    /** 私网 endpoint 放行（REQ-H16：null = 回退部署级缺省；tcp://localhost 等回环地址须为 true） */
    private Boolean allowPrivateEndpoints;

    public Map<String, String> managedMetadata() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("com.fastrag.sandbox.managed", "true");
        meta.put("com.fastrag.sandbox.config-id", configId == null ? "" : configId);
        meta.put("com.fastrag.sandbox.user-id", userId == null ? "" : userId);
        meta.put("com.fastrag.sandbox.session-id", sessionId == null ? "" : sessionId);
        return meta;
    }
}
