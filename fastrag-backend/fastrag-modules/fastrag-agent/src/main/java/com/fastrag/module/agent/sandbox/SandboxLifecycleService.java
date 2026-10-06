package com.fastrag.module.agent.sandbox;

import com.fastrag.module.agent.sandbox.config.EffectiveConfig;

/**
 * 沙箱绑定与生命周期契约（contract agent.SandboxLifecycle 目标态，design SandboxLifecycleModule，切片 S7）。
 *
 * <p>会话级沙箱实例的唯一合法通道：(用户, 会话) → 实例绑定的获取/保活/释放；
 * 跨轮状态保持（REQ-H08）与四类回收路径（空闲 REQ-H02 / 配置变更 REQ-H09 /
 * 会话删除 REQ-H10 / 孤儿对账 REQ-H11）全部内聚于此。</p>
 *
 * <p>acquire 四路径：
 * <ol>
 *   <li>绑定命中（configId 一致且未失效）→ provider.connect 自愈（exited/created → running）→ touch 返回</li>
 *   <li>绑定命中但实例已消失（connect NOT_FOUND）→ 清绑定 → 重建 → put 新绑定</li>
 *   <li>绑定存在但身份已变（staleAt 非空或 configId 不一致）→ 销毁旧实例 → create 新 → casRebind（REQ-H09）</li>
 *   <li>无绑定 → create → put 新绑定</li>
 * </ol>
 * provider 异常按 {@link com.fastrag.module.agent.sandbox.provider.SandboxProviderException}
 * 分类直接上抛（执行门面转降级文本），<b>失败不留半截绑定</b>。</p>
 *
 * <p><b>接线缺口登记（2026-09-28，S7）</b>：{@link #onConfigIdentityChanged(String)} 应由
 * SandboxConfigServiceImpl.update 在「换凭据/置 cordon_at」（Batch 1，S10 切片）时调用；
 * 该切片未预留钩子、构造签名被 SandboxConfigServiceTest 锁定，本切片不改其构造——
 * 钩子对接留待 S10 后续批次补线（在 update 的 fingerprint/cordon 分支注入本回调）。
 * 当前语义等价兜底：绑定侧 acquire 亦比对 binding.configId 与当前 config 的
 * identityFingerprint 差异通道（staleAt / configId 换指），未接钩子期间 REQ-H09 重建
 * 由「cordon 语义 + 应用改指配置」路径部分覆盖。</p>
 */
public interface SandboxLifecycleService {

    /**
     * 获取或创建会话绑定实例（见类注释四路径）。
     *
     * @throws com.fastrag.module.agent.sandbox.provider.SandboxProviderException
     *         config 缺失/disabled/类型未接线 = INVALID_REQUEST/UNSUPPORTED；
     *         连接期失败 = UNAVAILABLE 等——调用方（执行门面）转「Error: …」文本，不中断运行
     */
    SandboxHandle acquire(String userId, String sessionId, EffectiveConfig config);

    /**
     * 只查不建（不触发 provision）；无绑定返回 null。
     */
    SandboxHandle lookup(String userId, String sessionId);

    /**
     * 释放：删绑定 + provider.delete（幂等）。
     */
    void release(String userId, String sessionId);

    /**
     * 会话删除钩子（REQ-H10，app-center 侧触发调用）：删绑定 + 删实例。
     */
    void onSessionDeleted(String userId, String sessionId);

    /**
     * 配置身份变更批量失效（REQ-H09 上游信号）：该配置名下全部绑定置 staleAt。
     * 对接缺口见类 javadoc 登记。
     */
    void onConfigIdentityChanged(String configId);

    /**
     * 空闲回收（@Scheduled fixedDelay=60s，REQ-H02）：managed 实例活动标记超
     * 创建时 TTL（默认 1800s，实例侧固定）→ provider.delete + 绑定清除；
     * Cube/E2B（UNSUPPORTED 分类）跳过——provider TTL 承担同等语义。
     */
    void reclaimIdle();

    /**
     * 孤儿对账（@Scheduled fixedDelay=300s，REQ-H11）：provider list（managed，含 paused）
     * 对比绑定存储，删除「无绑定且 lastActivity 早于宽限期（默认 600s）」的实例；
     * 本进程未持有 endpoint 的实例按 UNAVAILABLE 容忍跳过。
     */
    void reconcileOrphans();
}
