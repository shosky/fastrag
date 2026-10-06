package com.fastrag.module.agent.sandbox.impl;

import com.fastrag.module.agent.sandbox.BindingRecord;
import com.fastrag.module.agent.sandbox.SandboxBindingStore;
import com.fastrag.module.agent.sandbox.SandboxHandle;
import com.fastrag.module.agent.sandbox.SandboxLifecycleService;
import com.fastrag.module.agent.sandbox.config.EffectiveConfig;
import com.fastrag.module.agent.sandbox.config.SandboxConfigService;
import com.fastrag.module.agent.sandbox.provider.CreateContext;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderClient;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderErrorKind;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderException;
import com.fastrag.module.agent.sandbox.provider.SandboxProviderRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 沙箱绑定与生命周期契约实现（contract agent.SandboxLifecycle，切片 S7）。
 *
 * <p>acquire 四路径 / 降级上抛 / 失败不留半截绑定语义见接口 javadoc。
 * TTL 固定于实例创建时（provider label 承载，{@link SandboxProviderClient#idleTtl} 读取，
 * 进程内创建记录兜底）；Cube/E2B（UNSUPPORTED 分类）回收路径跳过，由 provider TTL 承担。</p>
 *
 * <p>接线缺口：onConfigIdentityChanged 与 SandboxConfigServiceImpl.update 的钩子对接，
 * 见 {@link SandboxLifecycleService} javadoc 登记（S10 后续批次补线）。</p>
 */
@Slf4j
@Service
public class SandboxLifecycleServiceImpl implements SandboxLifecycleService {

    private static final String MANAGED_META_KEY = "com.fastrag.sandbox.managed";
    private static final String MANAGED_META_VALUE = "true";
    private static final long DEFAULT_IDLE_TTL_SECONDS = 1800;
    private static final long DEFAULT_ORPHAN_GRACE_SECONDS = 600;

    private final SandboxProviderRegistry registry;
    /** 全部已接线 provider（回收/对账遍历用；UNSUPPORTED/UNAVAILABLE 分类逐个容忍） */
    private final List<SandboxProviderClient> providers;
    private final SandboxBindingStore bindingStore;
    /** 启用中的 docker 配置枚举（按配置逐 daemon 扫描；WeKnora per-config sweep 同款） */
    private final SandboxConfigService sandboxConfigService;

    /** 空闲 TTL 秒（yml 回退键，agent.sandbox.idle-ttl） */
    @Value("${agent.sandbox.idle-ttl:1800}")
    private long idleTtlSeconds = DEFAULT_IDLE_TTL_SECONDS;

    /** 孤儿对账宽限期秒（agent.sandbox.orphan-grace） */
    @Value("${agent.sandbox.orphan-grace:600}")
    private long orphanGraceSeconds = DEFAULT_ORPHAN_GRACE_SECONDS;

    /** 时钟（测试注入）；生产 = Instant::now */
    private Supplier<Instant> now = Instant::now;

    /** 本进程创建的实例 TTL（sandboxId → 秒）——provider 侧 label 读不到时的兜底 */
    private final ConcurrentHashMap<String, Long> createdTtlSeconds = new ConcurrentHashMap<>();

    public SandboxLifecycleServiceImpl(SandboxProviderRegistry registry,
                                       List<SandboxProviderClient> providers,
                                       SandboxBindingStore bindingStore,
                                       SandboxConfigService sandboxConfigService) {
        this.registry = registry;
        this.providers = providers;
        this.bindingStore = bindingStore;
        this.sandboxConfigService = sandboxConfigService;
    }

    /** 时钟注入（package 可见，测试用）。 */
    void setNow(Supplier<Instant> now) {
        this.now = now;
    }

    // ---------- acquire（四路径） ----------

    @Override
    public SandboxHandle acquire(String userId, String sessionId, EffectiveConfig config) {
        requireUsableConfig(config);
        SandboxProviderClient client = registry.require(config.getType());

        BindingRecord binding = bindingStore.get(userId, sessionId);
        if (binding == null) {
            // 路径④：无绑定 → create → put（失败不留半截绑定：先建后写）
            SandboxHandle fresh = createOrThrow(client, config, userId, sessionId);
            bindingStore.put(userId, sessionId, recordOf(fresh, config, userId, sessionId));
            return fresh;
        }

        boolean identityChanged = binding.getStaleAt() != null
                || !matchesConfig(binding, config);
        if (identityChanged) {
            // 路径③：身份已变（REQ-H09）→ 销毁旧实例 → create 新 → casRebind
            log.info("[SandboxLifecycle] identity changed for session {}:{}, rebuild (old={})",
                    userId, sessionId, binding.getSandboxId());
            deleteQuietly(binding);
            SandboxHandle fresh = createOrThrow(client, config, userId, sessionId);
            BindingRecord freshRecord = recordOf(fresh, config, userId, sessionId);
            boolean casOk = bindingStore.casRebind(userId, sessionId, binding.getSandboxId(), freshRecord);
            if (!casOk) {
                // 绑定已被并发改写：put 兜底（输者实例成孤儿，由孤儿对账兜底回收）
                log.warn("[SandboxLifecycle] casRebind lost race for session {}:{}, fallback to put", userId, sessionId);
                bindingStore.put(userId, sessionId, freshRecord);
            }
            return fresh;
        }

        try {
            // 路径①：绑定命中 → connect 自愈（exited/created → running）→ touch 返回
            //（touch 用注入时钟，保证可测；进程内 lastUsed 供空闲参考，权威活动标记在容器内）
            SandboxHandle handle = handleOf(binding);
            SandboxHandle connected = client.connect(handle);
            connected.setLastUsed(now().toEpochMilli());
            return connected;
        } catch (SandboxProviderException e) {
            if (e.getKind() == SandboxProviderErrorKind.NOT_FOUND) {
                // 路径②：绑定命中但实例已消失 → 清绑定 → 重建 → put
                log.info("[SandboxLifecycle] bound instance gone for session {}:{}, recreate", userId, sessionId);
                bindingStore.delete(userId, sessionId);
                SandboxHandle fresh = createOrThrow(client, config, userId, sessionId);
                bindingStore.put(userId, sessionId, recordOf(fresh, config, userId, sessionId));
                return fresh;
            }
            throw e; // UNAVAILABLE 等：绑定不变，调用方（门面）转降级文本，下次重试
        }
    }

    @Override
    public SandboxHandle lookup(String userId, String sessionId) {
        BindingRecord binding = bindingStore.get(userId, sessionId);
        return binding == null ? null : handleOf(binding);
    }

    // ---------- 释放 ----------

    @Override
    public void release(String userId, String sessionId) {
        BindingRecord binding = bindingStore.get(userId, sessionId);
        if (binding == null) {
            return; // 幂等
        }
        bindingStore.delete(userId, sessionId);
        deleteQuietly(binding);
    }

    @Override
    public void onSessionDeleted(String userId, String sessionId) {
        release(userId, sessionId);
    }

    @Override
    public void onConfigIdentityChanged(String configId) {
        if (configId == null || configId.isBlank()) {
            return;
        }
        bindingStore.markStaleByConfigId(configId);
        log.info("[SandboxLifecycle] config {} marked stale on all bindings", configId);
    }

    // ---------- 空闲回收（REQ-H02） ----------

    @Override
    @Scheduled(fixedDelay = 60_000)
    public void reclaimIdle() {
        Instant now = now();
        for (SandboxProviderClient client : providers) {
            for (ProviderSweep sweep : sweepsFor(client)) {
                List<SandboxHandle> instances;
                try {
                    instances = client.list(sweep.metadata(), Set.of(),
                            sweep.endpointHost(), sweep.endpointTlsCertPath(), sweep.endpointAllowPrivate());
                } catch (SandboxProviderException e) {
                    if (e.getKind() == SandboxProviderErrorKind.UNSUPPORTED) {
                        continue; // Cube/E2B：provider TTL 承担（REQ-H02）
                    }
                    log.warn("[SandboxLifecycle] reclaimIdle: provider {} sweep {} failed: {}",
                            client.provider(), sweep.label(), e.getMessage());
                    continue;
                } catch (Exception e) {
                    log.warn("[SandboxLifecycle] reclaimIdle: provider {} sweep {} failed: {}",
                            client.provider(), sweep.label(), e.getMessage());
                    continue;
                }
                for (SandboxHandle instance : instances) {
                    reclaimIfIdle(client, instance, now);
                }
            }
        }
    }

    private void reclaimIfIdle(SandboxProviderClient client, SandboxHandle instance, Instant now) {
        Instant lastActivity;
        try {
            lastActivity = client.lastActivity(instance);
        } catch (SandboxProviderException e) {
            log.debug("[SandboxLifecycle] reclaimIdle: lastActivity unavailable for {}: {}",
                    instance.getSandboxId(), e.getMessage());
            return; // 消失/不可达 → 交孤儿对账
        } catch (Exception e) {
            log.debug("[SandboxLifecycle] reclaimIdle: lastActivity unavailable for {}: {}",
                    instance.getSandboxId(), e.getMessage());
            return;
        }
        if (lastActivity == null) {
            return;
        }
        long ttlSeconds = ttlOf(instance);
        if (now.isAfter(lastActivity.plusSeconds(ttlSeconds))) {
            log.info("[SandboxLifecycle] reclaim idle sandbox {} (idle since {}, ttl={}s)",
                    instance.getSandboxId(), lastActivity, ttlSeconds);
            safeDelete(client, instance);
            deleteBindingBySandboxId(instance.getSandboxId());
        }
    }

    // ---------- 孤儿对账（REQ-H11） ----------

    @Override
    @Scheduled(fixedDelay = 300_000)
    public void reconcileOrphans() {
        Set<String> boundSandboxIds = new HashSet<>();
        for (SandboxBindingStore.BindingEntry entry : bindingStore.listAll()) {
            if (entry.record() != null && entry.record().getSandboxId() != null) {
                boundSandboxIds.add(entry.record().getSandboxId());
            }
        }

        Instant now = now();
        for (SandboxProviderClient client : providers) {
            for (ProviderSweep sweep : sweepsFor(client)) {
                List<SandboxHandle> instances;
                try {
                    instances = client.list(sweep.metadata(), Set.of(), // 含 paused（适配器 list 全态）
                            sweep.endpointHost(), sweep.endpointTlsCertPath(), sweep.endpointAllowPrivate());
                } catch (SandboxProviderException e) {
                    log.warn("[SandboxLifecycle] reconcileOrphans: provider {} sweep {} failed: {}",
                            client.provider(), sweep.label(), e.getMessage());
                    continue;
                } catch (Exception e) {
                    log.warn("[SandboxLifecycle] reconcileOrphans: provider {} sweep {} failed: {}",
                            client.provider(), sweep.label(), e.getMessage());
                    continue;
                }
                for (SandboxHandle instance : instances) {
                    if (boundSandboxIds.contains(instance.getSandboxId())) {
                        continue; // 绑定库仍引用 → 保留
                    }
                    orphanSweep(client, instance, now);
                }
            }
        }
    }

    private void orphanSweep(SandboxProviderClient client, SandboxHandle instance, Instant now) {
        Instant lastActivity;
        try {
            lastActivity = client.lastActivity(instance);
        } catch (SandboxProviderException e) {
            // UNAVAILABLE（本进程未持有 endpoint 等）容忍跳过；NOT_FOUND（已消失）自然跳过
            log.warn("[SandboxLifecycle] reconcileOrphans: skip {} ({}): {}",
                    instance.getSandboxId(), e.getKind(), e.getMessage());
            return;
        } catch (Exception e) {
            log.warn("[SandboxLifecycle] reconcileOrphans: skip {}: {}", instance.getSandboxId(), e.getMessage());
            return;
        }
        if (lastActivity == null) {
            return;
        }
        if (now.isAfter(lastActivity.plusSeconds(orphanGraceSeconds))) {
            log.info("[SandboxLifecycle] orphan sandbox {} reaped (last activity {}, grace={}s)",
                    instance.getSandboxId(), lastActivity, orphanGraceSeconds);
            safeDelete(client, instance);
            deleteBindingBySandboxId(instance.getSandboxId());
        }
    }

    // ---------- 关停清理 ----------

    @PreDestroy
    public void shutdown() {
        List<SandboxBindingStore.BindingEntry> entries = bindingStore.listAll();
        log.info("[SandboxLifecycle] shutdown: releasing {} bound sandbox instances", entries.size());
        for (SandboxBindingStore.BindingEntry entry : entries) {
            try {
                release(entry.userId(), entry.sessionId());
            } catch (Exception e) {
                log.warn("[SandboxLifecycle] shutdown release failed for session {}:{}: {}",
                        entry.userId(), entry.sessionId(), e.getMessage());
            }
        }
        createdTtlSeconds.clear();
    }

    // ---------- 内部 ----------

    private void requireUsableConfig(EffectiveConfig config) {
        if (config == null) {
            throw SandboxProviderException.invalidRequest("sandbox config is required");
        }
        String type = config.getType();
        if (type == null || type.isBlank() || "disabled".equals(type)) {
            throw SandboxProviderException.invalidRequest("sandbox type is disabled or missing");
        }
        if (!config.isEnabled()) {
            throw SandboxProviderException.invalidRequest(
                    "sandbox config '" + config.getConfigId() + "' is disabled");
        }
    }

    /** 绑定与当前配置是否仍同身份：configId 指向一致且指纹一致（staleAt 判定在调用方）。 */
    private boolean matchesConfig(BindingRecord binding, EffectiveConfig config) {
        if (binding.getConfigId() != null && !binding.getConfigId().equals(config.getConfigId())) {
            return false;
        }
        if (binding.getProvider() != null && !binding.getProvider().equals(config.getType())) {
            return false;
        }
        // 指纹比对：绑定无指纹字段（data.md 值结构），经 staleAt 通道承载身份变更（markStaleByConfigId）
        return true;
    }

    private SandboxHandle createOrThrow(SandboxProviderClient client, EffectiveConfig config,
                                        String userId, String sessionId) {
        SandboxHandle created = client.create(buildCreateContext(config, userId, sessionId));
        createdTtlSeconds.put(created.getSandboxId(),
                config.getIdleTtlSeconds() != null && config.getIdleTtlSeconds() > 0
                        ? config.getIdleTtlSeconds() : idleTtlSeconds);
        return created;
    }

    private CreateContext buildCreateContext(EffectiveConfig config, String userId, String sessionId) {
        return CreateContext.builder()
                .configId(config.getConfigId())
                .userId(userId)
                .sessionId(sessionId)
                .sessionKey(userId + ":" + sessionId)
                .imageOrTemplate(config.getImageOrTemplate())
                .cpuLimit(config.getCpuLimit() != null ? config.getCpuLimit() : 2.0)
                .memoryBytes(config.getMemoryBytes() != null
                        ? config.getMemoryBytes() : 2L * 1024 * 1024 * 1024)
                .pidsLimit(config.getPidsLimit() != null ? config.getPidsLimit() : 512)
                .networkMode(config.getNetworkMode() == null || config.getNetworkMode().isBlank()
                        ? "bridge" : config.getNetworkMode())
                .envVars(config.getEnvVars() != null
                        ? new HashMap<>(config.getEnvVars()) : new HashMap<>())
                .idleTtl(Duration.ofSeconds(config.getIdleTtlSeconds() != null
                        && config.getIdleTtlSeconds() > 0
                        ? config.getIdleTtlSeconds() : idleTtlSeconds))
                .dockerHost(config.getDockerHost())
                .dockerTlsCertPath(config.getDockerTlsCertPath())
                .allowPrivateEndpoints(config.isAllowPrivateEndpoints())
                .build();
    }

    private BindingRecord recordOf(SandboxHandle handle, EffectiveConfig config,
                                   String userId, String sessionId) {
        return BindingRecord.of(handle.getProvider() != null ? handle.getProvider() : config.getType(),
                handle.getSandboxId(), config.getConfigId(), now());
    }

    private SandboxHandle handleOf(BindingRecord binding) {
        return SandboxHandle.builder()
                .sandboxId(binding.getSandboxId())
                .provider(binding.getProvider())
                .configId(binding.getConfigId())
                .state(binding.getStaleAt() != null ? "stale" : "unknown")
                .build();
    }

    private void deleteQuietly(BindingRecord binding) {
        if (binding.getProvider() == null || binding.getSandboxId() == null) {
            return;
        }
        try {
            SandboxHandle handle = handleOf(binding);
            registry.find(binding.getProvider()).ifPresent(client -> client.delete(handle));
        } catch (SandboxProviderException e) {
            log.warn("[SandboxLifecycle] delete old instance {} failed ({}): {}",
                    binding.getSandboxId(), e.getKind(), e.getMessage());
        } catch (Exception e) {
            log.warn("[SandboxLifecycle] delete old instance {} failed: {}",
                    binding.getSandboxId(), e.getMessage());
        }
    }

    private void safeDelete(SandboxProviderClient client, SandboxHandle instance) {
        try {
            client.delete(instance);
            createdTtlSeconds.remove(instance.getSandboxId());
        } catch (Exception e) {
            log.warn("[SandboxLifecycle] delete {} failed (instance left for next sweep): {}",
                    instance.getSandboxId(), e.getMessage());
        }
    }

    private long ttlOf(SandboxHandle instance) {
        // 优先 provider 侧创建时固定 label；进程内创建记录兜底；最后用服务缺省
        try {
            Duration ttl = registry.find(instance.getProvider())
                    .map(client -> client.idleTtl(instance))
                    .orElse(null);
            if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
                return ttl.getSeconds();
            }
        } catch (Exception ignored) {
            // 读不到 label → 兜底链
        }
        Long local = createdTtlSeconds.get(instance.getSandboxId());
        return local != null ? local : idleTtlSeconds;
    }

    private void deleteBindingBySandboxId(String sandboxId) {
        for (SandboxBindingStore.BindingEntry entry : bindingStore.listAll()) {
            if (entry.record() != null && sandboxId.equals(entry.record().getSandboxId())) {
                bindingStore.delete(entry.userId(), entry.sessionId());
            }
        }
    }

    private Map<String, String> managedFilter() {
        Map<String, String> meta = new HashMap<>();
        meta.put(MANAGED_META_KEY, MANAGED_META_VALUE);
        return meta;
    }

    private static final String CONFIG_ID_META_KEY = "com.fastrag.sandbox.config-id";

    /** 一次清扫 = 一个 provider 的一个 daemon 视角（per-config sweep，WeKnora 同款——按租户扫会误删兄弟配置的沙箱） */
    private record ProviderSweep(String label, Map<String, String> metadata,
                                 String endpointHost, String endpointTlsCertPath, boolean endpointAllowPrivate) {
    }

    /**
     * 枚举一个 provider 的清扫视角：docker = 启用中的配置逐个（endpoint 感知）；
     * 其他 provider = 单一缺省视角（Cube/E2B 由 UNSUPPORTED 分类跳过，provider TTL 承担回收）。
     */
    private List<ProviderSweep> sweepsFor(SandboxProviderClient client) {
        if (!"docker".equals(client.provider())) {
            return List.of(new ProviderSweep("default", managedFilter(), null, null, false));
        }
        List<EffectiveConfig> configs;
        try {
            configs = sandboxConfigService.listEnabledDockerEffective();
        } catch (Exception e) {
            log.warn("[SandboxLifecycle] sweepsFor: enumerate docker configs failed: {}", e.getMessage());
            return List.of();
        }
        List<ProviderSweep> sweeps = new ArrayList<>();
        for (EffectiveConfig cfg : configs) {
            Map<String, String> metadata = managedFilter();
            if (cfg.getConfigId() != null) {
                metadata.put(CONFIG_ID_META_KEY, cfg.getConfigId());
            }
            sweeps.add(new ProviderSweep("config:" + cfg.getConfigId(), metadata,
                    cfg.getDockerHost(), cfg.getDockerTlsCertPath(), cfg.isAllowPrivateEndpoints()));
        }
        return sweeps;
    }

    private Instant now() {
        return now != null ? now.get() : Instant.now();
    }
}
