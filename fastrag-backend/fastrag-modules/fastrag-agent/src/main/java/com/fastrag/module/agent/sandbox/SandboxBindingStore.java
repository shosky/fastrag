package com.fastrag.module.agent.sandbox;

import java.time.Instant;
import java.util.List;

/**
 * 沙箱绑定存储附属缝（contract agent.SandboxLifecycle 附属接口 SandboxBindingStore，REQ-H01/H09/H11）。
 *
 * <p>(用户, 会话) → 实例绑定的唯一读写口。绑定值结构见 data.md「sandbox 绑定存储」节：
 * {@code {version:1, provider, sandboxId, configId, createdAt, staleAt?}}，JSON 序列化。</p>
 *
 * <p>双适配器：
 * <ul>
 *   <li>{@link memory.InMemorySandboxBindingStore} — 单实例开发/测试（ConcurrentHashMap）</li>
 *   <li>{@link redis.RedisSandboxBindingStore} — 生产多副本共享（StringRedisTemplate，
 *       key {@code sandbox:binding:{userId}:{sessionId}}，<b>不设 Redis TTL</b>——
 *       过期会造成 provider 侧实例仍在、绑定已失的泄漏形态，由孤儿对账兜底）</li>
 * </ul></p>
 *
 * <p>写语义：换绑走 {@link #casRebind}（compare-and-set 旧 sandboxId，防并发换绑丢更新）；
 * 失效标记走 {@link #markStaleByConfigId}（配置身份变更批量标记，REQ-H09）。</p>
 */
public interface SandboxBindingStore {

    /**
     * 读绑定；无绑定返回 null（只查不建）。
     */
    BindingRecord get(String userId, String sessionId);

    /**
     * 写入/覆盖绑定（acquire 未命中新建、CAS 竞态兜底路径使用）。
     */
    void put(String userId, String sessionId, BindingRecord record);

    /**
     * CAS 换绑：仅当当前绑定指向 {@code oldSandboxId} 时替换为 {@code newBinding}。
     *
     * @return true = 换绑成功；false = 绑定不存在或已被并发改写（丢更新的输者由孤儿对账兜底）
     */
    boolean casRebind(String userId, String sessionId, String oldSandboxId, BindingRecord newBinding);

    /**
     * 配置身份变更批量失效：该配置名下全部绑定置 staleAt（REQ-H09——下次使用销毁重建）。
     */
    void markStaleByConfigId(String configId);

    /**
     * 删除绑定（release / 会话删除 / 空闲回收路径）；不存在时幂等。
     */
    void delete(String userId, String sessionId);

    /**
     * 列举引用指定配置的全部绑定（孤儿对账与生命周期关联键）。
     */
    List<BindingRecord> listByConfigId(String configId);

    /**
     * 全量列举（含绑定键）——@PreDestroy 关停全量释放与回收路径的反查依据。
     * <p>BindingRecord 本身不携带 userId/sessionId（data.md 值结构），故以 BindingEntry 承载键。</p>
     */
    List<BindingEntry> listAll();

    /** 绑定条目：定位键 (userId, sessionId) + 绑定值。 */
    record BindingEntry(String userId, String sessionId, BindingRecord record) {
    }
}
