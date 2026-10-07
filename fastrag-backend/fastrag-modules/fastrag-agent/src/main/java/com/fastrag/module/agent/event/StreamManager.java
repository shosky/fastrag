package com.fastrag.module.agent.event;

import java.util.List;
import java.util.Map;

/**
 * Agent 运行事件通道（I2，finding gap-3）：可重放事件流的存储抽象。
 *
 * <p>RunEventPublisher 负责分配单调 seq 并调用 {@link #appendEvent}；SSE 订阅方
 * （AgentRunServiceImpl.streamEvents）按 {@code afterSeq} 增量拉取，实现断点重放。
 * 事件 Map 结构：{@code {runId, eventType, payload, seq}}。</p>
 *
 * <p>实现：
 * <ul>
 *   <li>{@link MemoryStreamManager} — 单机内存实现（默认兜底）</li>
 *   <li>{@link RedisStreamManager} — StringRedisTemplate + RPush/LRange 实现（多副本共享，主实现，
 *       Redis 不可用时降级 memory 并 warn）</li>
 * </ul></p>
 */
public interface StreamManager {

    /** 单 run 事件容量上限（超限丢最旧）。 */
    int MAX_EVENTS_PER_RUN = 500;

    /**
     * 追加一条事件（调用方已填好 seq）。实现方负责容量上限（超限丢最旧）与持久化语义。
     *
     * @param runId 运行 ID
     * @param event 事件 Map（含 runId/eventType/payload/seq）
     */
    void appendEvent(String runId, Map<String, Object> event);

    /**
     * 拉取 seq 严格大于 {@code afterSeq} 的事件（按 seq 升序）。
     *
     * @param runId    运行 ID
     * @param afterSeq 已消费到的最后一个 seq（0 = 从头拉取）
     */
    List<Map<String, Object>> getEvents(String runId, long afterSeq);

    /** 清空某 run 的事件存储（run 终态时调用）。 */
    void clear(String runId);
}
