package com.fastrag.module.agent.sandbox.config;

import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.SandboxConfigCreateDTO;
import com.fastrag.module.agent.dto.SandboxConfigUpdateDTO;
import com.fastrag.module.agent.dto.SandboxConfigVO;
import com.fastrag.module.agent.entity.SandboxConfig;

import java.time.Instant;
import java.util.List;

/**
 * 沙箱配置面服务（契约 agent.SandboxConfigStore，design SandboxConfigModule，切片 S10）。
 *
 * <p>沙箱资源的命名配置台账：CRUD + 归属权限（REQ-H04）、探活验证与实例库存（REQ-H05）、
 * 凭据引用式存储与脱敏（REQ-H06 / ADR-0007）、平台总开关三级取值（REQ-H07）、
 * 保存校验链（REQ-H15 网络模式白名单 + docker 域名策略拒绝、REQ-H16 EndpointDialGuard）。</p>
 *
 * <p>语义要点：
 * <ul>
 *   <li>业务错误一律 {@code BusinessException(code, msg)} → HTTP 200 + body.code（宪法）</li>
 *   <li>写权限 = 归属者或管理员（role ∈ {admin, superadmin}），否则 403 语义业务错</li>
 *   <li>读取恒经脱敏：{@code api_key_ref} 只回显引用名，任何出参不含 resolve 后的值（REQ-H06）</li>
 *   <li>update：身份字段变更重算 identity_fingerprint；空 api_key_ref 不覆盖既有引用</li>
 * </ul></p>
 */
public interface SandboxConfigService {

    // ---------- 业务错误码（与契约 E_ 码一致的 int 常量，HTTP 200 + body.code 承载） ----------

    /** 配置非法：类型越界/provider 块缺失/网络模式越界/docker 域名策略/endpoint 拨号拒/引用名格式 */
    int E_SANDBOX_CONFIG_INVALID = 41001;
    /** 探活不可达或鉴权失败（附 provider 原因） */
    int E_SANDBOX_VERIFY_FAILED = 41002;
    /** 凭据引用指向的环境变量缺失（resolve/verify 时 fail-loud，ADR-0007） */
    int E_SANDBOX_CREDENTIAL_MISSING = 41003;
    /** 配置名下仍有实例（含 paused），删除被拒（409 语义） */
    int E_SANDBOX_CONFIG_IN_USE = 40901;
    /** 沙箱被显式关闭（disabled 配置或平台总开关关闭） */
    int E_SANDBOX_DISABLED = 41004;
    /** 应用未配置沙箱（app.sandbox_config_id 缺失/指向不存在配置） */
    int E_SANDBOX_NOT_CONFIGURED = 41005;
    /** provider 连接期失败（daemon/控制面不可达） */
    int E_SANDBOX_UNAVAILABLE = 41006;

    // ---------- CRUD（REQ-H04） ----------

    /**
     * 创建配置（校验链：类型合法 → provider 块必填性 → 网络策略与类型匹配 →
     * network_mode 白名单 → EndpointDialGuard.validateEndpoint → 凭据引用名格式）。
     *
     * @throws com.fastrag.common.exception.BusinessException E_SANDBOX_CONFIG_INVALID 校验链任一环失败（不落库）
     */
    SandboxConfig create(SandboxConfigCreateDTO dto, User user);

    /**
     * 更新配置（patch 合并后重走保存校验链；身份字段变更重算 identity_fingerprint，
     * 空 api_key_ref 不覆盖既有引用；非身份字段变更不动指纹）。
     *
     * @throws com.fastrag.common.exception.BusinessException 403 非归属者非管理员；E_SANDBOX_CONFIG_INVALID 校验失败
     */
    SandboxConfig update(String configId, SandboxConfigUpdateDTO patch, User user);

    /**
     * 删除配置：名下有实例（含 paused）时拒绝（E_SANDBOX_CONFIG_IN_USE）；
     * 无实例物理删，历史实例交孤儿对账按宽限期回收（REQ-H05）。
     *
     * @throws com.fastrag.common.exception.BusinessException 403 / 404 / E_SANDBOX_CONFIG_IN_USE
     */
    void delete(String configId, User user);

    /**
     * 读取配置（归属者或管理员可读）。
     *
     * @throws com.fastrag.common.exception.BusinessException 403 / 404
     */
    SandboxConfig get(String configId, User user);

    /**
     * 列举配置：普通用户回自己的配置，管理员回全量。
     */
    List<SandboxConfig> listByUser(User user);

    /**
     * 枚举「启用中的 docker 型配置」的有效配置（系统清扫用：空闲回收/孤儿对账按配置逐 daemon 扫描；
     * WeKnora 同款——按租户扫会误删兄弟配置的沙箱）。非请求路径，无属主过滤。
     */
    List<EffectiveConfig> listEnabledDockerEffective();

    // ---------- 验证与库存（REQ-H05） ----------

    /**
     * 探活验证：Docker = daemon ping；Cube/E2B = 控制面鉴权（先经 resolveCredentials fail-loud）。
     *
     * @throws com.fastrag.common.exception.BusinessException E_SANDBOX_VERIFY_FAILED（附 provider 原因分类）；
     *         配置保持原状（验证失败无写入）
     */
    VerifyResult verify(String configId);

    /**
     * 实例库存：该配置名下现存沙箱实例（managed 标记 + configId 过滤，含 paused/transitioning）。
     * provider 不可达/未接线按业务错误暴露（库存查不到比「有」更要报）。
     *
     * @throws com.fastrag.common.exception.BusinessException E_SANDBOX_VERIFY_FAILED 语义
     */
    List<SandboxInstanceInfo> listInstances(String configId);

    // ---------- 凭据解析与总开关（REQ-H06 / H07） ----------

    /**
     * 凭据引用解析：*_config.api_key_ref → System.getenv(refName)，返回有效配置
     * （EffectiveConfig 的 *ApiKey 字段为解析后实际值，仅供生命周期/执行面消费，永不出服务端）。
     *
     * @throws com.fastrag.common.exception.BusinessException E_SANDBOX_CREDENTIAL_MISSING 环境变量缺失
     */
    EffectiveConfig resolveCredentials(SandboxConfig config);

    /**
     * 平台总开关三级取值（REQ-H07）：SysConfig（sys_config.sandbox.enabled）
     * &gt; application.yml 回退键 {@code agent.sandbox.enabled} &gt; 内置默认 false。
     */
    boolean isGloballyEnabled();

    /**
     * 出参脱敏组装（design maskForResponse）：实体 → VO；api_key_ref 保持引用名，
     * VO 上不存在 resolve 后的值字段（脱敏恒开，REQ-H06）。
     */
    SandboxConfigVO maskForResponse(SandboxConfig config);

    // ---------- 出参值对象 ----------

    /**
     * 验证结果（契约 VerifyResult{reachable, providerInfo?, templates?}）。
     */
    record VerifyResult(boolean reachable, String providerInfo, List<String> templates) {
    }

    /**
     * 库存实例条目（契约 listInstances → [{sandboxId, state, createdAt}]）。
     * createdAt 依赖 provider 侧创建时间，S10 阶段 SandboxHandle 无此字段时为 null（S11 适配器补齐）。
     */
    record SandboxInstanceInfo(String sandboxId, String state, Instant createdAt) {
    }
}
