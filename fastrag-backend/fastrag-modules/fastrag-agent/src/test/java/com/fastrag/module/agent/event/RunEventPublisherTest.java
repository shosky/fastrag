package com.fastrag.module.agent.event;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I2（P0-3，finding gap-3）：RunEventPublisher 事件流改造——
 * seq 自增单调（AtomicLong）、存储接 StreamManager、容量上限丢最旧、终态清理。
 *
 * <p>Trace: [Test] → 单元 I/I2 → RunEventPublisher + StreamManager(memory) → SSE 可重放事件通道</p>
 */
class RunEventPublisherTest {

    private RunEventPublisher newPublisher() {
        return new RunEventPublisher(new MemoryStreamManager());
    }

    private static long seqOf(Map<String, Object> event) {
        return ((Number) event.get("seq")).longValue();
    }

    @Test
    void pushEvent_seqIncrementsMonotonicallyPerRun() {
        RunEventPublisher publisher = newPublisher();

        publisher.pushEvent("run-1", "status", Map.of("s", 1));
        publisher.pushEvent("run-1", "tool_call", Map.of("n", "a"));
        publisher.pushEvent("run-1", "tool_call", Map.of("n", "b"));

        List<Map<String, Object>> events = publisher.getEvents("run-1");
        assertThat(events).hasSize(3);
        assertThat(seqOf(events.get(0))).isEqualTo(1);
        assertThat(seqOf(events.get(1))).isEqualTo(2);
        assertThat(seqOf(events.get(2))).isEqualTo(3);
        // 旧的时间戳 seq（毫秒级）不再出现
        assertThat(seqOf(events.get(2))).isLessThan(System.currentTimeMillis() / 10);
    }

    @Test
    void getEvents_afterSeq_returnsOnlyEventsNewerThanOffset() {
        RunEventPublisher publisher = newPublisher();
        publisher.pushEvent("run-1", "e1", Map.of());
        publisher.pushEvent("run-1", "e2", Map.of());
        publisher.pushEvent("run-1", "e3", Map.of());

        List<Map<String, Object>> after1 = publisher.getEvents("run-1", 1L);

        assertThat(after1).hasSize(2);
        assertThat(seqOf(after1.get(0))).isEqualTo(2);
        assertThat(seqOf(after1.get(1))).isEqualTo(3);

        // 全量（offset=0）与未知 run 的空列表语义保留
        assertThat(publisher.getEvents("run-1", 0L)).hasSize(3);
        assertThat(publisher.getEvents("run-unknown")).isEmpty();
    }

    @Test
    void appendEvent_overCapacityDropsOldest_keepsNewest500() {
        RunEventPublisher publisher = newPublisher();
        for (int i = 1; i <= StreamManager.MAX_EVENTS_PER_RUN + 5; i++) {
            publisher.pushEvent("run-1", "e" + i, Map.of("i", i));
        }

        List<Map<String, Object>> all = publisher.getEvents("run-1", 0L);
        assertThat(all).hasSize(StreamManager.MAX_EVENTS_PER_RUN);
        // 最旧的 1..5 被丢弃，剩下 6..MAX+5
        assertThat(seqOf(all.get(0))).isEqualTo(6);
        assertThat(seqOf(all.get(all.size() - 1))).isEqualTo(StreamManager.MAX_EVENTS_PER_RUN + 5);

        // 增量拉取同样只看到保留窗口内的事件
        assertThat(publisher.getEvents("run-1", 5L)).hasSize(StreamManager.MAX_EVENTS_PER_RUN);
    }

    @Test
    void clearStream_eventsRemainReadableDuringGraceWindow() {
        RunEventPublisher publisher = newPublisher();
        publisher.graceClearDelayMillis = 60_000;
        publisher.pushEvent("run-1", "e1", Map.of());
        publisher.pushEvent("run-1", "e2", Map.of());

        publisher.clearStream("run-1");

        // 宽限窗口内事件仍可读：活跃 SSE 订阅方（500ms 轮询）能消费到终态前的尾部事件
        List<Map<String, Object>> events = publisher.getEvents("run-1");
        assertThat(events).hasSize(2);
        assertThat(seqOf(events.get(0))).isEqualTo(1);
        assertThat(seqOf(events.get(1))).isEqualTo(2);
    }

    @Test
    void clearStream_afterGraceWindow_eventsWipedAndSeqCounterReset() throws Exception {
        RunEventPublisher publisher = newPublisher();
        publisher.graceClearDelayMillis = 0;
        publisher.pushEvent("run-1", "e1", Map.of());

        publisher.clearStream("run-1");
        awaitTrue(() -> publisher.getEvents("run-1").isEmpty());

        // 清理后（含 seq 计数器一并回收）重新发布从 1 编号（防御性保证语义干净）
        publisher.pushEvent("run-1", "e2", Map.of());
        assertThat(seqOf(publisher.getEvents("run-1").get(0))).isEqualTo(1);
    }

    private static void awaitTrue(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(50);
        }
        org.assertj.core.api.Assertions.fail("condition not met within 5s");
    }

    @Test
    void pushEvent_streamFailureDoesNotThrow() {
        RunEventPublisher publisher = new RunEventPublisher(new StreamManager() {
            @Override
            public void appendEvent(String runId, Map<String, Object> event) {
                throw new IllegalStateException("storage down");
            }

            @Override
            public List<Map<String, Object>> getEvents(String runId, long afterSeq) {
                return List.of();
            }

            @Override
            public void clear(String runId) {
                // no-op
            }
        });

        publisher.pushEvent("run-1", "e1", Map.of()); // 不抛出即通过
        assertThat(publisher.getEvents("run-1")).isEmpty();
    }
}
