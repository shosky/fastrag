package com.fastrag.module.agent.sandbox.config.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.SandboxConfigCreateDTO;
import com.fastrag.module.agent.dto.SandboxConfigUpdateDTO;
import com.fastrag.module.agent.dto.SandboxConfigVO;
import com.fastrag.module.agent.entity.SandboxConfig;
import com.fastrag.module.agent.mapper.SandboxConfigMapper;
import com.fastrag.module.agent.sandbox.SandboxBindingStore;
import com.fastrag.module.agent.sandbox.config.EffectiveConfig;
import com.fastrag.module.agent.sandbox.config.SandboxConfigService;
import com.fastrag.module.agent.sandbox.provider.CreateContext;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderRegistry;
import com.fastrag.module.agent.sandbox.security.EndpointDialGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 沙箱配置面服务实现（契约 agent.SandboxConfigStore，切片 S10）。
 *
 * <p>实现要点：
 * <ul>
 *   <li>provider 块 JSON 列映射：实体以 JSON 字符串承载，服务内 Jackson Map 视图读写，落库边界序列化</li>
 *   <li>保存校验链（create/update 同链）：类型 → provider 块必填性 → 网络策略与类型匹配 →
 *       network_mode 白名单 → {@link EndpointDialGuard#validateEndpoint} → 凭据引用名格式</li>
 *   <li>身份指纹：SHA-256(type|endpoint|apiKeyRef|imageOrTemplate) 确定性哈希；非身份字段变更
 *       不动指纹（确定性哈希对非身份字段天然不敏感）；换凭据/换底模时置 cordon_at</li>
 *   <li>凭据引用式（ADR-0007）：库中只存引用名，resolveCredentials 时 System.getenv 解析，
 *       缺失 fail-loud E_SANDBOX_CREDENTIAL_MISSING；读取出参只回显引用名（脱敏恒开）</li>
 *   <li>总开关三级取值（REQ-H07）：sys_config['sandbox.enabled']（模块内只读查询）&gt;
 *       {@code agent.sandbox.enabled} yml 回退 &gt; false。platform-config 侧键管理界面接线
 *       归 platform-config 链（后续批次）——<b>已登记缺口</b>：当前该键仅能经 DB/接口手工写入，
 *       平台配置 UI 尚无沙箱开关项</li>
 *   <li>待接线（S11 生命周期批次）：update 身份变更触发生命周期侧批量 stale（REQ-H09 上游信号）
 *       与孤儿对账，本切片只重算指纹 + 置 cordon_at</li>
 * </ul></p>
 */
@Slf4j
@Service
public class SandboxConfigServiceImpl implements SandboxConfigService {

    private static final Set<String> LEGAL_TYPES = Set.of("docker", "cube", "e2b", "disabled");
    private static final Set<String> LEGAL_NETWORK_MODES = Set.of("bridge", "none");
    private static final String DEFAULT_NETWORK_MODE = "bridge";
    /** 凭据引用名格式：合法环境变量名（非空时校验；保存不解析值，ADR-0007） */
    private static final Pattern ENV_REF_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** 库存过滤 metadata 的 managed 标记键（config-id 按次拼接） */
    private static final String METADATA_MANAGED_KEY = "com.fastrag.sandbox.managed";
    private static final String METADATA_CONFIG_ID_KEY = "com.fastrag.sandbox.config-id";

    /** Cube/E2B 探活凭据经 envVars 携带的临时键（CreateContext 无专用字段；S11 适配器实装时收敛协议） */
    private static final String PROBE_API_URL_KEY = "com.fastrag.sandbox.api-url";
    private static final String PROBE_API_KEY_KEY = "com.fastrag.sandbox.api-key";

    private final SandboxConfigMapper sandboxConfigMapper;
    private final SandboxProviderRegistry providerRegistry;
    private final EndpointDialGuard dialGuard;
    private final ObjectMapper objectMapper;
    /** 绑定存储：身份变更批量 stale（REQ-H09 接线） */
    private final SandboxBindingStore bindingStore;
    /** yml 回退层：agent.sandbox.enabled（应用 yml 未配置时为 false） */
    private final boolean ymlFallbackEnabled;

    public SandboxConfigServiceImpl(SandboxConfigMapper sandboxConfigMapper,
                                    SandboxProviderRegistry providerRegistry,
                                    EndpointDialGuard dialGuard,
                                    ObjectMapper objectMapper,
                                    SandboxBindingStore bindingStore,
                                    @Value("${agent.sandbox.enabled:false}") boolean ymlFallbackEnabled) {
        this.sandboxConfigMapper = sandboxConfigMapper;
        this.providerRegistry = providerRegistry;
        this.dialGuard = dialGuard;
        this.objectMapper = objectMapper;
        this.bindingStore = bindingStore;
        this.ymlFallbackEnabled = ymlFallbackEnabled;
    }

    // ---------- CRUD（REQ-H04） ----------

    @Override
    public SandboxConfig create(SandboxConfigCreateDTO dto, User user) {
        if (user == null || isBlank(user.getUid())) {
            throw BusinessException.unauthorized("未认证");
        }
        if (dto == null || isBlank(dto.getName())) {
            throw configInvalid("配置名不能为空");
        }
        String type = dto.getSandboxType();
        requireLegalType(type);

        Map<String, Object> docker = emptyIfNull(dto.getDockerConfig());
        Map<String, Object> cube = emptyIfNull(dto.getCubeConfig());
        Map<String, Object> e2b = emptyIfNull(dto.getE2bConfig());
        Map<String, Object> networkPolicy = emptyIfNull(dto.getNetworkPolicy());
        normalizeNetworkMode(type, docker);

        validateChain(type, networkPolicy, docker, cube, e2b, Boolean.TRUE.equals(dto.getAllowPrivateEndpoints()));

        assertNameAvailable(user.getUid(), dto.getName(), null);

        LocalDateTime now = LocalDateTime.now();
        SandboxConfig config = new SandboxConfig();
        config.setUserId(user.getUid());
        config.setName(dto.getName().trim());
        config.setSandboxType(type);
        config.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        config.setDefaultTimeoutSec(dto.getDefaultTimeoutSec() == null ? 0 : dto.getDefaultTimeoutSec());
        config.setAllowPrivateEndpoints(Boolean.TRUE.equals(dto.getAllowPrivateEndpoints()));
        config.setNetworkPolicy(writeJson(networkPolicy));
        config.setEnvVars(writeJson(dto.getEnvVars() == null ? null : new LinkedHashMap<>(dto.getEnvVars())));
        config.setDockerConfig(writeJson(docker));
        config.setCubeConfig(writeJson(cube));
        config.setE2bConfig(writeJson(e2b));
        config.setIdentityFingerprint(fingerprintOf(type, docker, cube, e2b));
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        sandboxConfigMapper.insert(config);
        log.info("sandbox config created: id={}, user={}, type={}", config.getId(), user.getUid(), type);
        return config;
    }

    @Override
    public SandboxConfig update(String configId, SandboxConfigUpdateDTO patch, User user) {
        SandboxConfig existing = requireWritable(configId, user);
        if (patch == null) {
            return existing;
        }

        // 标量合并（null = 不变）
        if (!isBlank(patch.getName())) {
            assertNameAvailable(existing.getUserId(), patch.getName().trim(), configId);
            existing.setName(patch.getName().trim());
        }
        if (!isBlank(patch.getSandboxType())) {
            requireLegalType(patch.getSandboxType());
            existing.setSandboxType(patch.getSandboxType());
        }
        if (patch.getEnabled() != null) {
            existing.setEnabled(patch.getEnabled());
        }
        if (patch.getDefaultTimeoutSec() != null) {
            existing.setDefaultTimeoutSec(patch.getDefaultTimeoutSec());
        }
        if (patch.getAllowPrivateEndpoints() != null) {
            existing.setAllowPrivateEndpoints(patch.getAllowPrivateEndpoints());
        }

        // 块合并：patch 非空时浅合并覆盖；空 api_key_ref 不覆盖既有引用（REQ-H06/ADR-0007）
        Map<String, Object> oldDocker = parseJsonMap(existing.getDockerConfig());
        Map<String, Object> oldCube = parseJsonMap(existing.getCubeConfig());
        Map<String, Object> oldE2b = parseJsonMap(existing.getE2bConfig());
        String oldRef = apiKeyRefOf(existing.getSandboxType(), oldCube, oldE2b);
        String oldImageOrTemplate = imageOrTemplateOf(existing.getSandboxType(), oldDocker, oldCube, oldE2b);

        String type = existing.getSandboxType();
        Map<String, Object> docker = mergeProviderBlock(existing.getDockerConfig(), patch.getDockerConfig());
        Map<String, Object> cube = mergeProviderBlock(existing.getCubeConfig(), patch.getCubeConfig());
        Map<String, Object> e2b = mergeProviderBlock(existing.getE2bConfig(), patch.getE2bConfig());
        Map<String, Object> networkPolicy = patch.getNetworkPolicy() != null
                ? new LinkedHashMap<>(patch.getNetworkPolicy())
                : parseJsonMap(existing.getNetworkPolicy());
        if (patch.getEnvVars() != null) {
            existing.setEnvVars(writeJson(new LinkedHashMap<>(patch.getEnvVars())));
        }
        normalizeNetworkMode(type, docker);

        boolean allowPrivate = Boolean.TRUE.equals(existing.getAllowPrivateEndpoints());
        validateChain(type, networkPolicy, docker, cube, e2b, allowPrivate);

        existing.setNetworkPolicy(writeJson(networkPolicy));
        existing.setDockerConfig(writeJson(docker));
        existing.setCubeConfig(writeJson(cube));
        existing.setE2bConfig(writeJson(e2b));

        // 身份指纹：确定性哈希——非身份字段变更重算结果不变，即「不动指纹」
        String newEndpoint = endpointOf(type, docker, cube, e2b);
        String newRef = apiKeyRefOf(type, cube, e2b);
        String newImageOrTemplate = imageOrTemplateOf(type, docker, cube, e2b);
        String oldFingerprint = existing.getIdentityFingerprint();
        existing.setIdentityFingerprint(fingerprintOf(type, docker, cube, e2b));
        // 换凭据/换底模 → 隔离标记（该配置不再新建实例，存量下次 resolve 重建；data.md cordon_at 语义）
        if (!Objects.equals(newRef, oldRef) || !Objects.equals(newImageOrTemplate, oldImageOrTemplate)) {
            existing.setCordonAt(LocalDateTime.now());
        }
        // 身份变更（type/endpoint/凭据/镜像或模板任一）→ 存量绑定批量 stale（REQ-H09：下次使用销毁重建）
        if (!Objects.equals(existing.getIdentityFingerprint(), oldFingerprint)) {
            bindingStore.markStaleByConfigId(configId);
        }

        existing.setUpdatedAt(LocalDateTime.now());
        sandboxConfigMapper.updateById(existing);
        log.info("sandbox config updated: id={}, type={}, fingerprint={}", configId, type, existing.getIdentityFingerprint());
        return existing;
    }

    @Override
    public void delete(String configId, User user) {
        SandboxConfig existing = requireWritable(configId, user);
        List<SandboxInstanceInfo> instances = listInstancesInternal(existing);        if (!instances.isEmpty()) {
            throw new BusinessException(E_SANDBOX_CONFIG_IN_USE,
                    "配置名下仍有 " + instances.size() + " 个沙箱实例（含 paused），请先清理实例再删除");
        }
        sandboxConfigMapper.deleteById(configId);
        log.info("sandbox config deleted: id={}", configId);
    }

    @Override
    public SandboxConfig get(String configId, User user) {
        SandboxConfig config = sandboxConfigMapper.selectById(configId);
        if (config == null) {
            throw BusinessException.notFound("沙箱配置不存在: " + configId);
        }
        boolean owner = user != null && !isBlank(user.getUid()) && user.getUid().equals(config.getUserId());
        if (!owner && !isAdmin(user)) {
            throw BusinessException.forbidden("无权访问该沙箱配置");
        }
        return config;
    }

    @Override
    public List<SandboxConfig> listByUser(User user) {
        if (user == null) {
            throw BusinessException.unauthorized("未认证");
        }
        LambdaQueryWrapper<SandboxConfig> wrapper = new LambdaQueryWrapper<>();
        if (!isAdmin(user)) {
            wrapper.eq(SandboxConfig::getUserId, user.getUid());
        }
        wrapper.orderByDesc(SandboxConfig::getCreatedAt);
        return sandboxConfigMapper.selectList(wrapper);
    }

    @Override
    public List<EffectiveConfig> listEnabledDockerEffective() {
        List<SandboxConfig> rows = sandboxConfigMapper.selectList(
                new LambdaQueryWrapper<SandboxConfig>()
                        .eq(SandboxConfig::getSandboxType, "docker")
                        .eq(SandboxConfig::getEnabled, 1));
        List<EffectiveConfig> result = new ArrayList<>();
        for (SandboxConfig row : rows) {
            try {
                result.add(resolveCredentials(row));
            } catch (BusinessException e) {
                log.warn("[SandboxConfig] listEnabledDockerEffective: skip config {}: {}",
                        row.getId(), e.getMessage());
            }
        }
        return result;
    }

    // ---------- 验证与库存（REQ-H05） ----------

    @Override
    public VerifyResult verify(String configId) {
        SandboxConfig config = requireExisting(configId);
        String type = config.getSandboxType();
        if ("disabled".equals(type)) {
            throw new BusinessException(E_SANDBOX_DISABLED, "沙箱配置已显式停用（disabled），无需验证");
        }
        SandboxProviderClient client = requireClient(type);
        CreateContext probe = buildProbe(config);
        try {
            client.health(probe);
        } catch (SandboxProviderException e) {
            // 验证失败：配置保持原状（无任何写入），错误体携带 provider 原因分类
            throw new BusinessException(E_SANDBOX_VERIFY_FAILED,
                    "沙箱验证失败: [" + e.getKind() + "] " + e.getProviderMessage());
        }
        return new VerifyResult(true, type + " provider reachable", null);
    }

    @Override
    public List<SandboxInstanceInfo> listInstances(String configId) {
        return listInstancesInternal(requireExisting(configId));
    }

    private List<SandboxInstanceInfo> listInstancesInternal(SandboxConfig config) {
        if ("disabled".equals(config.getSandboxType())) {
            return List.of(); // disabled 为显式关闭态保留行，不可能名下有实例
        }
        SandboxProviderClient client = requireClient(config.getSandboxType());
        return doList(client, config);
    }

    private List<SandboxInstanceInfo> doList(SandboxProviderClient client, SandboxConfig config) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_MANAGED_KEY, "true");
        metadata.put(METADATA_CONFIG_ID_KEY, config.getId() == null ? "" : config.getId());
        Map<String, Object> docker = parseJsonMap(config.getDockerConfig());
        List<com.fastrag.module.agent.sandbox.SandboxHandle> handles;
        try {
            handles = client.list(metadata, Set.of("running", "paused", "transitioning"),
                    str(docker, "host"), strAny(docker, "tls_cert_path", "tlsCertPath"),
                    Boolean.TRUE.equals(config.getAllowPrivateEndpoints()));
        } catch (SandboxProviderException e) {
            // 库存查不到比「有」更要报（契约 listInstances 恢复语义）
            throw new BusinessException(E_SANDBOX_VERIFY_FAILED,
                    "查询沙箱实例库存失败: [" + e.getKind() + "] " + e.getProviderMessage());
        }
        List<SandboxInstanceInfo> result = new ArrayList<>();
        for (com.fastrag.module.agent.sandbox.SandboxHandle handle : handles) {
            result.add(new SandboxInstanceInfo(handle.getSandboxId(), handle.getState(), createdAtOf(handle)));
        }
        return result;
    }

    private Instant createdAtOf(com.fastrag.module.agent.sandbox.SandboxHandle handle) {
        // SandboxHandle 暂无 provider 侧创建时间（S11 适配器补齐）；本切片返回 null 表示未知
        return null;
    }

    // ---------- 凭据解析与总开关（REQ-H06 / H07） ----------

    @Override
    public EffectiveConfig resolveCredentials(SandboxConfig config) {
        if (config == null) {
            throw new BusinessException(E_SANDBOX_NOT_CONFIGURED, "沙箱配置不存在");
        }
        String type = config.getSandboxType();
        Map<String, Object> docker = parseJsonMap(config.getDockerConfig());
        Map<String, Object> cube = parseJsonMap(config.getCubeConfig());
        Map<String, Object> e2b = parseJsonMap(config.getE2bConfig());

        EffectiveConfig.EffectiveConfigBuilder builder = EffectiveConfig.builder()
                .type(type)
                .configId(config.getId())
                .userId(config.getUserId())
                .name(config.getName())
                .enabled(Boolean.TRUE.equals(config.getEnabled()) && !"disabled".equals(type))
                .defaultTimeoutSec(config.getDefaultTimeoutSec() == null ? 0 : config.getDefaultTimeoutSec())
                .allowPrivateEndpoints(Boolean.TRUE.equals(config.getAllowPrivateEndpoints()))
                .networkPolicy(parseJsonMap(config.getNetworkPolicy()))
                .envVars(stringMap(parseJsonMap(config.getEnvVars())))
                .identityFingerprint(config.getIdentityFingerprint());

        switch (type == null ? "" : type) {
            case "docker" -> {
                // 键名兼容（2026-09-30 修复）：前端提交 camelCase、历史数据两套键混存，读取一律 camel 优先、snake 兜底
                Double memoryLimitMb = doubleAny(docker, "memory_limit_mb", "memoryLimitMb");
                builder.dockerHost(str(docker, "host"))
                        .dockerTlsCertPath(strAny(docker, "tls_cert_path", "tlsCertPath"))
                        .imageOrTemplate(str(docker, "image"))
                        .cpuLimit(doubleAny(docker, "cpu_limit", "cpuLimit"))
                        .memoryBytes(memoryLimitMb == null ? null : (long) (memoryLimitMb * 1024 * 1024))
                        .pidsLimit(longAny(docker, "pids_limit", "pidsLimit"))
                        .networkMode(orDefault(strAny(docker, "network_mode", "networkMode"), DEFAULT_NETWORK_MODE))
                        .idleTtlSeconds(longAny(docker, "idle_ttl_sec", "idleTtlSec"));
            }
            case "cube" -> {
                builder.imageOrTemplate(strAny(cube, "template_id", "templateId"))
                        .cubeApiUrl(strAny(cube, "api_url", "apiUrl"))
                        .cubeProxyUrl(strAny(cube, "proxy_url", "proxyUrl"))
                        .cubeSandboxDomain(strAny(cube, "sandbox_domain", "sandboxDomain"))
                        .cubeApiKey(resolveEnv(strAny(cube, "api_key_ref", "apiKeyRef")))
                        .cubeTemplateId(strAny(cube, "template_id", "templateId"))
                        .cubeHttpTimeoutSec(intAny(cube, "http_timeout_sec", "httpTimeoutSec"))
                        .cubeTtlSec(longAny(cube, "ttl_sec", "ttlSec"))
                        .cubeDnsServers(stringList(getAny(cube, "dns_servers", "dnsServers")));
            }
            case "e2b" -> {
                builder.imageOrTemplate(strAny(e2b, "template_id", "templateId"))
                        .e2bApiUrl(strAny(e2b, "api_url", "apiUrl"))
                        .e2bSandboxDomain(strAny(e2b, "sandbox_domain", "sandboxDomain"))
                        .e2bApiKey(resolveEnv(strAny(e2b, "api_key_ref", "apiKeyRef")))
                        .e2bTemplateId(strAny(e2b, "template_id", "templateId"))
                        .e2bProxyUrl(strAny(e2b, "proxy_url", "proxyUrl"))
                        .e2bHttpTimeoutSec(intAny(e2b, "http_timeout_sec", "httpTimeoutSec"))
                        .e2bTtlSec(longAny(e2b, "ttl_sec", "ttlSec"));
            }
            default -> {
                // disabled：无 provider 块、无凭据
            }
        }
        return builder.build();
    }

    /** 引用 → 环境值（use 时 fail-loud；保存时只校验引用名格式，ADR-0007） */
    private String resolveEnv(String refName) {
        if (isBlank(refName)) {
            throw new BusinessException(E_SANDBOX_CREDENTIAL_MISSING, "凭据引用名为空，无法解析");
        }
        String value = System.getenv(refName);
        if (isBlank(value)) {
            throw new BusinessException(E_SANDBOX_CREDENTIAL_MISSING,
                    "凭据引用指向的环境变量缺失: " + refName + "（请在部署环境预置）");
        }
        return value;
    }

    @Override
    public boolean isGloballyEnabled() {
        // 一级：平台配置 sys_config['sandbox.enabled']（模块内只读查询；键值非空即以其布尔义为准）
        try {
            String sysValue = sandboxConfigMapper.selectSysConfigValue();
            if (!isBlank(sysValue)) {
                return parseLooseBoolean(sysValue);
            }
        } catch (Exception e) {
            log.warn("sys_config[sandbox.enabled] 查询失败，降级 yml 回退层: {}", e.getMessage());
        }
        // 二级：application.yml agent.sandbox.enabled；三级：内置默认 false（@Value 默认已兜底）
        return ymlFallbackEnabled;
    }

    /** 宽松布尔解析："true"/"1"（含 JSON 布尔/带引号形态）→ true；其余 → false */
    private boolean parseLooseBoolean(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        v = v.toLowerCase().trim();
        return "true".equals(v) || "1".equals(v);
    }

    // ---------- 脱敏出参（REQ-H06） ----------

    @Override
    public SandboxConfigVO maskForResponse(SandboxConfig config) {
        SandboxConfigVO vo = new SandboxConfigVO();
        vo.setId(config.getId());
        vo.setUserId(config.getUserId());
        vo.setName(config.getName());
        vo.setSandboxType(config.getSandboxType());
        vo.setEnabled(config.getEnabled());
        vo.setDefaultTimeoutSec(config.getDefaultTimeoutSec());
        vo.setAllowPrivateEndpoints(config.getAllowPrivateEndpoints());
        vo.setNetworkPolicy(parseJsonMap(config.getNetworkPolicy()));
        vo.setEnvVars(stringMap(parseJsonMap(config.getEnvVars())));
        // api_key_ref 本就只存引用名（ADR-0007）——引用名原样回显，VO 无 resolve 值字段（脱敏恒开）
        vo.setDockerConfig(parseJsonMap(config.getDockerConfig()));
        vo.setCubeConfig(parseJsonMap(config.getCubeConfig()));
        vo.setE2bConfig(parseJsonMap(config.getE2bConfig()));
        vo.setIdentityFingerprint(config.getIdentityFingerprint());
        vo.setCordonAt(config.getCordonAt());
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    // ---------- 保存校验链（REQ-H04/H15/H16） ----------

    private void validateChain(String type, Map<String, Object> networkPolicy,
                               Map<String, Object> docker, Map<String, Object> cube, Map<String, Object> e2b,
                               boolean allowPrivateEndpoints) {
        requireLegalType(type);
        switch (type) {
            case "docker" -> {
                if (docker == null || isBlank(str(docker, "image"))) {
                    throw configInvalid("docker 配置块必填且须含 image");
                }
                // REQ-H15：Docker 仅 L3/L4，域名级出网策略不可兑现——携带域名 allow/deny 列表保存被拒
                if (hasDomainList(networkPolicy, "allow_domains") || hasDomainList(networkPolicy, "deny_domains")) {
                    throw configInvalid("docker 配置不支持域名级网络策略（allow_domains/deny_domains 仅 Cube/E2B）");
                }
                // REQ-H15：network_mode ∈ {bridge, none}（缺省 bridge；host/container:/命名网络拒绝）
                String mode = orDefault(strAny(docker, "network_mode", "networkMode"), DEFAULT_NETWORK_MODE);
                if (!LEGAL_NETWORK_MODES.contains(mode)) {
                    throw configInvalid("docker network_mode 仅允许 bridge/none，当前: " + mode);
                }
                validateEndpointByGuard(str(docker, "host"), allowPrivateEndpoints);
            }
            case "cube" -> {
                if (cube == null || isBlank(strAny(cube, "api_url", "apiUrl"))
                        || isBlank(strAny(cube, "api_key_ref", "apiKeyRef"))) {
                    throw configInvalid("cube 配置块必填且须含 api_url 与 api_key_ref");
                }
                validateRefFormat(strAny(cube, "api_key_ref", "apiKeyRef"));
                validateEndpointByGuard(strAny(cube, "api_url", "apiUrl"), allowPrivateEndpoints);
                validateEndpointByGuard(strAny(cube, "proxy_url", "proxyUrl"), allowPrivateEndpoints);
            }
            case "e2b" -> {
                if (e2b == null || isBlank(strAny(e2b, "api_url", "apiUrl"))
                        || isBlank(strAny(e2b, "api_key_ref", "apiKeyRef"))) {
                    throw configInvalid("e2b 配置块必填且须含 api_url 与 api_key_ref");
                }
                validateRefFormat(strAny(e2b, "api_key_ref", "apiKeyRef"));
                validateEndpointByGuard(strAny(e2b, "api_url", "apiUrl"), allowPrivateEndpoints);
                validateEndpointByGuard(strAny(e2b, "proxy_url", "proxyUrl"), allowPrivateEndpoints);
            }
            default -> {
                // disabled：显式关闭态保留行，无 provider 块要求
            }
        }
    }

    /** REQ-H16：endpoint 拨号防护（云 metadata/link-local 恒拒；私网须显式放行）；SecurityException/IllegalArgumentException 转业务码 */
    private void validateEndpointByGuard(String endpoint, boolean allowPrivateEndpoints) {
        if (isBlank(endpoint)) {
            return; // 缺省 endpoint（如 Docker 本地 socket）由适配器自行解析
        }
        try {
            dialGuard.validateEndpoint(endpoint, allowPrivateEndpoints);
        } catch (SecurityException | IllegalArgumentException e) {
            throw configInvalid("endpoint 未通过拨号防护（REQ-H16）: " + e.getMessage());
        }
    }

    private void validateRefFormat(String refName) {
        if (!isBlank(refName) && !ENV_REF_PATTERN.matcher(refName).matches()) {
            throw configInvalid("凭据引用名格式非法（须为环境变量名）: " + refName);
        }
    }

    private void requireLegalType(String type) {
        if (isBlank(type) || !LEGAL_TYPES.contains(type)) {
            throw configInvalid("sandbox_type 非法（仅 docker/cube/e2b/disabled，host 不做）: " + type);
        }
    }

    /** REQ-H15：docker 配置缺省 network_mode=bridge（写回块内，落库即含缺省值）；用户已显式指定（camel）时不覆盖 */
    private void normalizeNetworkMode(String type, Map<String, Object> docker) {
        if ("docker".equals(type) && (docker == null || isBlank(strAny(docker, "network_mode", "networkMode")))) {
            docker.put("network_mode", DEFAULT_NETWORK_MODE);
        }
    }

    // ---------- 验证 probe 构建（REQ-H05） ----------

    private CreateContext buildProbe(SandboxConfig config) {
        String type = config.getSandboxType();
        Map<String, Object> docker = parseJsonMap(config.getDockerConfig());
        Map<String, Object> cube = parseJsonMap(config.getCubeConfig());
        Map<String, Object> e2b = parseJsonMap(config.getE2bConfig());

        CreateContext.CreateContextBuilder builder = CreateContext.builder()
                .configId(config.getId())
                .allowPrivateEndpoints(Boolean.TRUE.equals(config.getAllowPrivateEndpoints()))
                .envVars(stringMap(parseJsonMap(config.getEnvVars())));
        switch (type) {
            case "docker" -> builder
                    .dockerHost(str(docker, "host"))
                    .dockerTlsCertPath(strAny(docker, "tls_cert_path", "tlsCertPath"))
                    .imageOrTemplate(str(docker, "image"));
            case "cube", "e2b" -> {
                // 控制面鉴权前先 fail-loud 解析凭据（E_SANDBOX_CREDENTIAL_MISSING，ADR-0007）
                EffectiveConfig eff = resolveCredentials(config);
                Map<String, String> envVars = new LinkedHashMap<>(stringMap(parseJsonMap(config.getEnvVars())));
                // 临时携带（CreateContext 无 Cube/E2B 专用字段；S11 适配器实装时收敛探活协议）
                String apiUrl = "cube".equals(type) ? eff.getCubeApiUrl() : eff.getE2bApiUrl();
                String apiKey = "cube".equals(type) ? eff.getCubeApiKey() : eff.getE2bApiKey();
                envVars.put(PROBE_API_URL_KEY, apiUrl == null ? "" : apiUrl);
                envVars.put(PROBE_API_KEY_KEY, apiKey == null ? "" : apiKey);
                builder.imageOrTemplate(eff.getImageOrTemplate()).envVars(envVars);
            }
            default -> {
                throw new BusinessException(E_SANDBOX_DISABLED, "沙箱配置已停用（disabled）");
            }
        }
        return builder.build();
    }

    // ---------- 私有工具 ----------

    /** 写权限 = 归属者或管理员（REQ-H04）；缺失 404、越权 403 */
    private SandboxConfig requireWritable(String configId, User user) {
        SandboxConfig config = sandboxConfigMapper.selectById(configId);
        if (config == null) {
            throw BusinessException.notFound("沙箱配置不存在: " + configId);
        }
        boolean owner = user != null && !isBlank(user.getUid()) && user.getUid().equals(config.getUserId());
        if (!owner && !isAdmin(user)) {
            throw BusinessException.forbidden("无权操作该沙箱配置（仅归属者或管理员）");
        }
        return config;
    }

    private SandboxConfig requireExisting(String configId) {
        SandboxConfig config = sandboxConfigMapper.selectById(configId);
        if (config == null) {
            throw BusinessException.notFound("沙箱配置不存在: " + configId);
        }
        return config;
    }

    private SandboxProviderClient requireClient(String type) {
        try {
            return providerRegistry.require(type);
        } catch (SandboxProviderException e) {
            throw new BusinessException(E_SANDBOX_VERIFY_FAILED,
                    "沙箱 provider 未接线: [" + e.getKind() + "] " + e.getProviderMessage());
        }
    }

    /** 管理员判定：与 AgentServiceImpl 同款（role ∈ {admin, superadmin}） */
    private boolean isAdmin(User user) {
        return user != null && ("admin".equals(user.getRole()) || "superadmin".equals(user.getRole()));
    }

    private void assertNameAvailable(String userId, String name, String excludeConfigId) {
        LambdaQueryWrapper<SandboxConfig> wrapper = new LambdaQueryWrapper<SandboxConfig>()
                .eq(SandboxConfig::getUserId, userId)
                .eq(SandboxConfig::getName, name);
        if (excludeConfigId != null) {
            wrapper.ne(SandboxConfig::getId, excludeConfigId);
        }
        Long count = sandboxConfigMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw configInvalid("同名配置已存在: " + name);
        }
    }

    /** 身份指纹：SHA-256(type|endpoint|apiKeyRef|imageOrTemplate) 十六进制（REQ-H09 确定性哈希） */
    private String fingerprintOf(String type, Map<String, Object> docker,
                                 Map<String, Object> cube, Map<String, Object> e2b) {
        String canonical = Stream.of(type, endpointOf(type, docker, cube, e2b),
                        apiKeyRefOf(type, cube, e2b), imageOrTemplateOf(type, docker, cube, e2b))
                .map(s -> s == null ? "" : s.trim())
                .collect(Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String endpointOf(String type, Map<String, Object> docker,
                              Map<String, Object> cube, Map<String, Object> e2b) {
        return switch (type == null ? "" : type) {
            case "docker" -> str(docker, "host");
            case "cube" -> strAny(cube, "api_url", "apiUrl");
            case "e2b" -> strAny(e2b, "api_url", "apiUrl");
            default -> null;
        };
    }

    private String apiKeyRefOf(String type, Map<String, Object> cube, Map<String, Object> e2b) {
        return switch (type == null ? "" : type) {
            case "cube" -> strAny(cube, "api_key_ref", "apiKeyRef");
            case "e2b" -> strAny(e2b, "api_key_ref", "apiKeyRef");
            default -> null;
        };
    }

    private String imageOrTemplateOf(String type, Map<String, Object> docker,
                                     Map<String, Object> cube, Map<String, Object> e2b) {
        return switch (type == null ? "" : type) {
            case "docker" -> str(docker, "image");
            case "cube" -> strAny(cube, "template_id", "templateId");
            case "e2b" -> strAny(e2b, "template_id", "templateId");
            default -> null;
        };
    }

    /** patch 块浅合并：空 api_key_ref 不覆盖既有引用；null 值不覆盖 */
    private Map<String, Object> mergeProviderBlock(String existingJson, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>(parseJsonMap(existingJson));
        if (patch == null) {
            return merged;
        }
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            if ("api_key_ref".equals(entry.getKey())
                    && (entry.getValue() == null || String.valueOf(entry.getValue()).isBlank())) {
                continue; // 空引用不覆盖（REQ-H06/ADR-0007）
            }
            if (entry.getValue() != null) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private boolean hasDomainList(Map<String, Object> networkPolicy, String key) {
        if (networkPolicy == null) {
            return false;
        }
        Object value = networkPolicy.get(key);
        return value instanceof List<?> list && !list.isEmpty();
    }

    private BusinessException configInvalid(String message) {
        return new BusinessException(E_SANDBOX_CONFIG_INVALID, message);
    }

    private Map<String, Object> emptyIfNull(Map<String, Object> map) {
        return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> result = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
        } catch (Exception e) {
            log.warn("sandbox_config JSON 列解析失败，按空块降级: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("sandbox_config JSON 序列化失败", e);
        }
    }

    private Map<String, String> stringMap(Map<String, Object> raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> result.put(k, v == null ? null : String.valueOf(v)));
        }
        return result;
    }

    private List<String> stringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(v -> v == null ? null : String.valueOf(v)).collect(Collectors.toList());
        }
        return List.of();
    }

    private String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof String s ? s : String.valueOf(value);
    }

    // ---------- 键名兼容读取（2026-09-30 修复）：前端提交 camelCase，历史数据/normalize 缺省写入 snake_case，
    // 库中两套键并存——读取一律 camel 优先、snake 兜底（camel 是当前唯一写入方 UI 的键形，patch 合并后也以新改的 camel 值为准） ----------

    private Object getAny(Map<String, Object> map, String snakeKey, String camelKey) {
        if (map == null) {
            return null;
        }
        Object value = map.get(camelKey);
        return value != null ? value : map.get(snakeKey);
    }

    private String strAny(Map<String, Object> map, String snakeKey, String camelKey) {
        Object value = getAny(map, snakeKey, camelKey);
        return value == null ? null : (value instanceof String s ? s : String.valueOf(value));
    }

    private Double doubleAny(Map<String, Object> map, String snakeKey, String camelKey) {
        Object value = getAny(map, snakeKey, camelKey);
        return value == null ? null : doubleVal(value);
    }

    private Long longAny(Map<String, Object> map, String snakeKey, String camelKey) {
        Object value = getAny(map, snakeKey, camelKey);
        return value == null ? null : longVal(value);
    }

    private Integer intAny(Map<String, Object> map, String snakeKey, String camelKey) {
        Long value = longAny(map, snakeKey, camelKey);
        return value == null ? null : value.intValue();
    }

    private double doubleVal(Object value) {
        return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private long longVal(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
