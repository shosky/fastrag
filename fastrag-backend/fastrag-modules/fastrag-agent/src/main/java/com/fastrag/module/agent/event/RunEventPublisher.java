package com.fastrag.module.agent.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;

/**
 * Agent 运行事件发布组件（I2 改造，finding gap-3）：可重放事件通道的发布端。
 *
 * <p>核心变化（相对旧版）：
 * <ul>
 *   <li><b>seq 自增单调</b>——原为毫秒时间戳（同毫秒碰撞、无法做偏移量），现为每 run
 *       AtomicLong 自增（1,2,3...），SSE 订阅方以 afterSeq 增量拉取实现断点重放</li>
 *   <li><b>存储接 {@link StreamManager}</b>——默认 Redis（RPush/LRange + TTL 24h，多副本可重放），
 *       Redis 不可用粘性降级 memory，不阻断运行</li>
 *   <li><b>容量上限</b>——单 run 最多 {@link StreamManager#MAX_EVENTS_PER_RUN} 条，超限丢最旧
 *       （由 StreamManager 实现保证）</li>
 *   <li><b>终态清理</b>——{@link #clearStream}：宽限延迟清（默认 60s，给活跃 SSE 订阅方留出
 *       末尾事件消费窗口）</li>
 * </ul></p>
 *
 * @see com.fastrag.module.agent.service.impl.AgentRunServiceImpl 事件的使用者（SSE 增量拉取）
 */
@Slf4j
@Component
public class RunEventPublisher {

    /** 终态宽限清理延迟（秒）：给活跃 SSE 订阅方留出末尾事件消费窗口。 */
    static final long GRACE_CLEAR_SECONDS = 60;

    /** 实际调度延迟（毫秒）。包内可写：测试用小延迟验证宽限语义（生产 = 60s）。 */
    volatile long graceClearDelayMillis = GRACE_CLEAR_SECONDS * 1000;

    private final StreamManager streamManager;
    private final ConcurrentHashMap<String, AtomicLong> seqCounters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService graceCleaner;

    public RunEventPublisher(StreamManager streamManager) {
        this.streamManager = streamManager;
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "run-stream-cleaner");
            t.setDaemon(true);
            return t;
        };
        this.graceCleaner = Executors.newSingleThreadScheduledExecutor(tf);
    }

    /**
     * 推送事件（分配单调 seq 后写入 StreamManager）。失败只记日志，不阻断运行。
     *
     * @param runId     运行 ID
     * @param eventType 事件类型 (status / message / tool_call / artifact / subagent ...)
     * @param payload   事件载荷
     */
    public void pushEvent(String runId, String eventType, Object payload) {
        try {
            long seq = seqCounters.computeIfAbsent(runId, k -> new AtomicLong()).incrementAndGet();
            Map<String, Object> event = new java.util.HashMap<>();
            event.put("runId", runId);
            event.put("eventType", eventType);
            event.put("payload", payload);
            event.put("seq", seq);

            log.debug("Run event: runId={}, eventType={}, seq={}", runId, eventType, seq);
            streamManager.appendEvent(runId, event);
        } catch (Exception e) {
            log.error("Failed to publish run event: runId={}, eventType={}", runId, eventType, e);
        }
    }

    /**
     * 获取某 run 全部事件（等价 {@code getEvents(runId, 0)}）。
     */
    public List<Map<String, Object>> getEvents(String runId) {
        return getEvents(runId, 0L);
    }

    /**
     * 增量拉取 seq 严格大于 afterSeq 的事件（升序）。
     *
     * @param afterSeq 已消费到的最后一个 seq（0 = 全量重放）
     */
    public List<Map<String, Object>> getEvents(String runId, long afterSeq) {
        try {
            return streamManager.getEvents(runId, afterSeq);
        } catch (Exception e) {
            log.error("Failed to read run events: runId={}, afterSeq={}", runId, afterSeq, e);
            return Collections.emptyList();
        }
    }

    /**
     * run 终态清理：宽限延迟后清空某 run 的事件流（清空时 seq 计数器一并回收，
     * 保证 per-run seq 严格单调、宽限窗口内追加的尾部事件不会与已消费 seq 撞号）。
     *
     * <p>不能立即清：SSE 订阅方按事件通道轮询（500ms 级），立即清会把终态前最后
     * 一个轮询窗口内推送的尾部事件在被消费前抹掉——而 run 结果内容不落库（SSE 是
     * 唯一投递通道），丢了即永久丢。宽限延迟（默认 60s）即订阅方的末尾消费窗口。</p>
     */
    public void clearStream(String runId) {
        try {
            graceCleaner.schedule(() -> {
                seqCounters.remove(runId);
                try {
                    streamManager.clear(runId);
                } catch (Exception e) {
                    log.warn("Failed to clear run stream: runId={}", runId, e);
                }
            }, graceClearDelayMillis, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // cleaner 已关闭等场景忽略
        }
    }

    @PreDestroy
    void shutdown() {
        graceCleaner.shutdownNow();
    }
}
