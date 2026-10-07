package com.fastrag.module.agent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I2（P0-3）：Redis StreamManager——RPush/LRange、key 含 runId、TTL 24h、
 * Redis 不可用降级 memory 不阻断。
 *
 * <p>Trace: [Test] → 单元 I/I2 → RedisStreamManager → 事件通道分布式存储</p>
 */
@ExtendWith(MockitoExtension.class)
class RedisStreamManagerTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ListOperations<String, String> listOps;

    private RedisStreamManager manager;

    @BeforeEach
    void setUp() {
        manager = new RedisStreamManager(redis, new ObjectMapper(), new MemoryStreamManager());
        lenient().when(redis.opsForList()).thenReturn(listOps);
    }

    private static Map<String, Object> event(long seq) {
        return Map.of("runId", "run-1", "eventType", "tool_call", "payload", Map.of("n", seq), "seq", seq);
    }

    @Test
    void appendEvent_rpushesJsonWithKeyContainingRunIdAndTtl24h() {
        manager.appendEvent("run-1", event(1));

        verify(listOps).rightPush(contains("run-1"), anyString());
        verify(redis).expire(contains("run-1"), eq(Duration.ofHours(24)));
    }

    @Test
    void getEvents_lrangesAndFiltersBySeqGreaterThanOffset() {
        String json1 = toJson(event(1));
        String json2 = toJson(event(2));
        String json3 = toJson(event(3));
        when(listOps.range(contains("run-1"), eq(0L), eq(-1L))).thenReturn(List.of(json1, json2, json3));

        List<Map<String, Object>> after1 = manager.getEvents("run-1", 1L);

        assertThat(after1).hasSize(2);
        assertThat(((Number) after1.get(0).get("seq")).longValue()).isEqualTo(2);
        assertThat(((Number) after1.get(1).get("seq")).longValue()).isEqualTo(3);
    }

    @Test
    void getEvents_corruptJsonEntry_skippedNotFatal() {
        when(listOps.range(contains("run-1"), eq(0L), eq(-1L)))
                .thenReturn(List.of("not-json{", toJson(event(2))));

        List<Map<String, Object>> events = manager.getEvents("run-1", 0L);

        assertThat(events).hasSize(1);
        assertThat(((Number) events.get(0).get("seq")).longValue()).isEqualTo(2);
    }

    @Test
    void clear_removesUnderlyingKey() {
        manager.clear("run-1");
        verify(redis).delete(contains("run-1"));
    }

    @Test
    void redisUnavailable_degradesToMemoryWithoutThrowing() {
        when(listOps.rightPush(anyString(), anyString())).thenThrow(new RuntimeException("connection refused"));

        manager.appendEvent("run-1", event(1)); // 不抛出

        // 降级后事件可从 memory 读到
        List<Map<String, Object>> events = manager.getEvents("run-1", 0L);
        assertThat(events).hasSize(1);
        assertThat(((Number) events.get(0).get("seq")).longValue()).isEqualTo(1);
        // 后续写入走 memory，不再触 Redis
        manager.appendEvent("run-1", event(2));
        assertThat(manager.getEvents("run-1", 0L)).hasSize(2);
        verify(listOps, never()).leftPop(anyString());
    }

    private String toJson(Map<String, Object> event) {
        try {
            return new ObjectMapper().writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
