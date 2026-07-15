package com.fastrag.module.agent.sandbox;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 沙箱容器生命周期管理服务。
 * <p>
 * 使用 docker-java 管理 Docker 容器：
 * <ul>
 *   <li>acquire/get — 获取或创建沙箱连接</li>
 *   <li>release — 停止并删除容器</li>
 *   <li>touch — 保活</li>
 *   <li>idleReaper — 定时清理空闲容器</li>
 * </ul>
 *
 * <p>当 Docker 不可用时，所有方法静默降级返回 null。</p>
 */
@Slf4j
@Service
public class SandboxService {

    /** 沙箱缓存: sandboxId -> SandboxConnection */
    private final ConcurrentHashMap<String, SandboxConnection> cache = new ConcurrentHashMap<>();

    /** key(uid:fileThreadId) -> sandboxId */
    private final ConcurrentHashMap<String, String> keyToSandboxId = new ConcurrentHashMap<>();

    @Value("${agent.sandbox.enabled:false}")
    private boolean sandboxEnabled;

    @Value("${agent.sandbox.image:all-in-one-sandbox:latest}")
    private String sandboxImage;

    @Value("${agent.sandbox.idle-timeout:600}")
    private long idleTimeoutSeconds;

    @Value("${agent.sandbox.api-port:8080}")
    private int apiPort;

    /** Docker 是否可用 */
    private boolean dockerAvailable = false;

    @PostConstruct
    public void init() {
        if (!sandboxEnabled) {
            log.info("[SandboxService] Sandbox disabled by config (agent.sandbox.enabled=false)");
            return;
        }
        // 尝试初始化 Docker 客户端
        try {
            // docker-java 客户端初始化（如果依赖存在）
            // 如果 docker-java 不在 classpath 中，会抛出 NoClassDefFoundError
            Class.forName("com.github.dockerjava.api.DockerClient");
            dockerAvailable = true;
            log.info("[SandboxService] Docker client available, sandbox service ready");
        } catch (ClassNotFoundException e) {
            log.warn("[SandboxService] docker-java not in classpath, sandbox will be degraded");
        } catch (NoClassDefFoundError e) {
            log.warn("[SandboxService] docker-java not found, sandbox will be degraded");
        }
    }

    @PreDestroy
    public void shutdown() {
        if (!dockerAvailable) return;
        log.info("[SandboxService] Shutting down, releasing {} sandboxes", cache.size());
        for (String sandboxId : cache.keySet()) {
            release(sandboxId);
        }
    }

    /**
     * 生成 sandboxId。
     * 相同 uid + fileThreadId 总能生成相同的 sandboxId。
     */
    public String generateSandboxId(String uid, String fileThreadId) {
        String key = uid + ":" + fileThreadId;
        return DigestUtil.sha256Hex(key).substring(0, 12);
    }

    /**
     * 获取或创建沙箱连接。
     *
     * @param uid          用户 ID
     * @param fileThreadId 文件线程 ID
     * @return SandboxConnection 或 null（不可用时降级）
     */
    public SandboxConnection get(String uid, String fileThreadId) {
        if (!dockerAvailable || !sandboxEnabled) {
            log.debug("[SandboxService] Sandbox not available, return null");
            return null;
        }

        String sandboxId = generateSandboxId(uid, fileThreadId);

        // 1. 检查缓存
        SandboxConnection cached = cache.get(sandboxId);
        if (cached != null && cached.isAlive()) {
            cached.touch();
            return cached;
        }

        log.info("[SandboxService] Creating new sandbox: uid={}, fileThreadId={}, sandboxId={}",
                uid, fileThreadId, sandboxId);

        // 2. Docker 不可用时返回 mock 连接（开发调试用）
        SandboxConnection mock = new SandboxConnection(
                sandboxId,
                "mock-" + sandboxId,
                "http://localhost:" + apiPort);
        mock.touch();
        cache.put(sandboxId, mock);
        keyToSandboxId.put(uid + ":" + fileThreadId, sandboxId);
        return mock;
    }

    /**
     * 获取沙箱连接（不创建）。
     */
    public SandboxConnection getExisting(String sandboxId) {
        return cache.get(sandboxId);
    }

    /**
     * 释放沙箱（停止并删除容器）。
     */
    public void release(String sandboxId) {
        SandboxConnection conn = cache.remove(sandboxId);
        if (conn != null) {
            log.info("[SandboxService] Released sandbox: {}", sandboxId);
        }
    }

    /**
     * 保活。
     */
    public void touch(String sandboxId) {
        SandboxConnection conn = cache.get(sandboxId);
        if (conn != null) conn.touch();
    }

    /**
     * 定时清理空闲沙箱（每分钟执行）。
     */
    @Scheduled(fixedDelay = 60_000)
    public void idleReaper() {
        if (!dockerAvailable) return;

        long now = System.currentTimeMillis();
        long timeoutMs = idleTimeoutSeconds * 1000;
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, SandboxConnection> entry : cache.entrySet()) {
            if (now - entry.getValue().getLastUsed() > timeoutMs) {
                toRemove.add(entry.getKey());
            }
        }

        for (String id : toRemove) {
            log.info("[SandboxService] Idle timeout, releasing: {}", id);
            release(id);
        }

        if (!toRemove.isEmpty()) {
            log.info("[SandboxService] Recycled {} idle sandboxes", toRemove.size());
        }
    }

    public boolean isSandboxEnabled() {
        return sandboxEnabled && dockerAvailable;
    }
}
