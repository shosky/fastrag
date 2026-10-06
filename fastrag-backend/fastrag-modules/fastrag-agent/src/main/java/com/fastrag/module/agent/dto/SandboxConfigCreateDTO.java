package com.fastrag.module.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 沙箱配置创建请求 DTO（POST /api/sandbox-configs）。
 *
 * <p>字段映射（与 {@link com.fastrag.module.agent.entity.SandboxConfig} 对应）：
 * provider 块（dockerConfig/cubeConfig/e2bConfig）与 networkPolicy/envVars 以 Map 形态入参，
 * 服务端经 Jackson 序列化为 JSON 列字符串；密钥类字段（*_config.api_key_ref）只接受
 * 引用名（环境变量名），值在运行时经 resolveCredentials 解析（ADR-0007）。</p>
 *
 * <p>校验规则：name / sandboxType 通过 @NotBlank 保证非空；其余必填性
 * （类型→provider 块匹配）由服务端校验链判定（E_SANDBOX_CONFIG_INVALID）。</p>
 *
 * @see com.fastrag.module.agent.controller.SandboxConfigController 使用此 DTO 的控制器
 * @see com.fastrag.module.agent.sandbox.config.SandboxConfigService#create 承接创建的写路径
 */
@Data
public class SandboxConfigCreateDTO {

    @NotBlank(message = "配置名不能为空")
    private String name;

    /** 后端类型：docker / cube / e2b / disabled */
    @NotBlank(message = "沙箱类型不能为空")
    private String sandboxType;

    /** 配置级开关；null = 默认启用 */
    private Boolean enabled;

    /** 单次执行超时秒数；null/0 = 内置默认 60s */
    private Integer defaultTimeoutSec;

    /** 私网 endpoint 显式放行（REQ-H16）；null = 默认拒绝 */
    private Boolean allowPrivateEndpoints;

    /** 网络策略（Cube/E2B 域名 allow/deny 列表；docker 携带域名列表保存被拒，REQ-H15） */
    private Map<String, Object> networkPolicy;

    /** 注入沙箱的环境变量（非密钥类） */
    private Map<String, String> envVars;

    /** Docker 块 {image, host, tls_cert_path, cpu_limit, memory_limit_mb, pids_limit, network_mode, idle_ttl_sec} */
    private Map<String, Object> dockerConfig;

    /** Cube 块 {api_url, proxy_url, sandbox_domain, api_key_ref, template_id, http_timeout_sec, ttl_sec, dns_servers[]} */
    private Map<String, Object> cubeConfig;

    /** E2B 块 {api_url, sandbox_domain, api_key_ref, template_id, proxy_url, http_timeout_sec, ttl_sec} */
    private Map<String, Object> e2bConfig;
}
