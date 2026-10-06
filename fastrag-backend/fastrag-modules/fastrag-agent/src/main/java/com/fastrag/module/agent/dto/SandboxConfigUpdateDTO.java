package com.fastrag.module.agent.dto;

import lombok.Data;

import java.util.Map;

/**
 * 沙箱配置更新请求 DTO（PUT /api/sandbox-configs/{id}）——全字段可选的 patch 形态。
 *
 * <p>合并语义（契约 agent.SandboxConfigStore.update）：
 * <ul>
 *   <li>null 字段不动既有值；provider 块非 null 时按「块内浅合并」覆盖同名键</li>
 *   <li><b>空 api_key_ref 不覆盖既有引用</b>（REQ-H06 / ADR-0007）——patch 块内
 *       api_key_ref 为空串/null 时保留库中原引用</li>
 *   <li>身份字段（sandbox_type / docker host / image / cube|e2b 的 endpoint /
 *       api_key_ref / template）变更时重算 identity_fingerprint；非身份字段变更不动指纹</li>
 * </ul></p>
 */
@Data
public class SandboxConfigUpdateDTO {

    private String name;

    /** 后端类型：docker / cube / e2b / disabled；null = 不变 */
    private String sandboxType;

    private Boolean enabled;

    private Integer defaultTimeoutSec;

    private Boolean allowPrivateEndpoints;

    /** null = 不变；非 null = 整体替换 */
    private Map<String, Object> networkPolicy;

    /** null = 不变；非 null = 整体替换 */
    private Map<String, String> envVars;

    /** null = 不变；非 null = 块内浅合并（空 api_key_ref 不覆盖） */
    private Map<String, Object> dockerConfig;

    /** null = 不变；非 null = 块内浅合并（空 api_key_ref 不覆盖） */
    private Map<String, Object> cubeConfig;

    /** null = 不变；非 null = 块内浅合并（空 api_key_ref 不覆盖） */
    private Map<String, Object> e2bConfig;
}
