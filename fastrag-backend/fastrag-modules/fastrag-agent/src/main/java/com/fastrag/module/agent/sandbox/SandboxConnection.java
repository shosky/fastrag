package com.fastrag.module.agent.sandbox;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 沙箱连接封装。
 * 代表一个正在运行的 Docker 沙箱容器连接。
 */
@Data
@AllArgsConstructor
public class SandboxConnection {
    /** 沙箱标识（SHA256(uid:fileThreadId)[:12]） */
    private String sandboxId;
    /** Docker 容器 ID */
    private String containerId;
    /** HTTP API 基础 URL（如 "http://localhost:32768"） */
    private String url;
    /** 最后使用时间戳 */
    private long lastUsed = System.currentTimeMillis();

    public SandboxConnection(String sandboxId, String containerId, String url) {
        this.sandboxId = sandboxId;
        this.containerId = containerId;
        this.url = url;
        this.lastUsed = System.currentTimeMillis();
    }

    /** 更新最后使用时间 */
    public void touch() {
        this.lastUsed = System.currentTimeMillis();
    }

    /** 检查连接是否存活（10分钟内使用过） */
    public boolean isAlive() {
        return System.currentTimeMillis() - lastUsed < 600_000;
    }
}
