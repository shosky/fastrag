package com.fastrag.module.agent.sandbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 沙箱绑定值对象（data.md「sandbox 绑定存储」节，JSON 序列化经 Jackson）。
 *
 * <pre>
 * {version: 1, provider: docker|cube|e2b, sandboxId, configId, createdAt, staleAt?}
 * </pre>
 *
 * <p>version 为结构版本位；{@code staleAt} 非空表示绑定对应配置身份已变更，
 * 下次使用销毁重建（REQ-H09）。{@code createdAt} 供排查；绑定存活由 sandbox_config 的
 * idle_ttl 与 reclaimIdle 驱动删除，不设 Redis TTL（data.md 明示泄漏形态）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BindingRecord {

    /** 结构版本位 */
    private int version;

    /** 后端类型：docker / cube / e2b */
    private String provider;

    /** provider 侧实例 ID */
    private String sandboxId;

    /** 创建该实例的沙箱配置 ID */
    private String configId;

    /** 绑定创建时间 */
    private Instant createdAt;

    /** 失效标记时间（null = 正常；非空 = 配置身份已变更，下次使用销毁重建，REQ-H09） */
    private Instant staleAt;

    /** 便捷构造（version 恒 1，staleAt 空）。 */
    public static BindingRecord of(String provider, String sandboxId, String configId, Instant createdAt) {
        return new BindingRecord(1, provider, sandboxId, configId, createdAt, null);
    }

    /** 失效副本（其余字段不变）。 */
    public BindingRecord withStaleAt(Instant at) {
        return new BindingRecord(this.version, this.provider, this.sandboxId, this.configId, this.createdAt, at);
    }
}
