package com.fastrag.module.agent.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 沙箱实例句柄（SandboxConnection 演进，agent.SandboxProviderGateway 契约）。
 * <p>
 * id 为 provider 侧非确定 ID（Docker 容器 ID / Cube·E2B 沙箱 ID），由绑定存储持久化——
 * 旧「sha256(uid:fileThreadId) 确定性 ID」模型废止（REQ-H01，2026-09-28）。
 * lastUsed 供进程内保活与空闲回收参考；权威活动标记在容器内（适配器折叠进 exec 包装器）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxHandle {

    /** provider 侧实例 ID */
    private String sandboxId;

    /** 后端类型：docker / cube / e2b */
    private String provider;

    /** 创建该实例的沙箱配置 ID（绑定与孤儿对账的关联键） */
    private String configId;

    /** 实例当前态：running / paused / exited / transitioning / unknown */
    @Builder.Default
    private String state = "unknown";

    /** 进程内最近使用时间戳（毫秒） */
    @Builder.Default
    private long lastUsed = System.currentTimeMillis();

    public void touch() {
        this.lastUsed = System.currentTimeMillis();
    }
}
