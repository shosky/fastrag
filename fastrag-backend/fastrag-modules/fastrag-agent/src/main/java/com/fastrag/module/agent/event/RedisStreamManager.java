package com.fastrag.module.agent.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 版事件通道（I2，主实现——多副本部署下事件可重放）。
 *
 * <p>Key：{@code fastrag:agent:run:stream:{runId}}；Value：事件 Map 的 JSON 串；
 * RPush 追加 / LRange 拉取 / LTrim 维持容量上限 / TTL 24h（每次追加续期）。</p>
 *
 * <p>降级语义：Redis 不可用（连接失败等）时<b>粘性降级</b>到 memory 实现并 warn 一次，
 * 不阻断运行——事件通道是旁路能力，丢通道不能丢 run。</p>
 */
@Slf4j
@Primary
@Component
public class RedisStreamManager implements StreamManager {

    public static final String KEY_PREFIX = "fastrag:agent:run:stream:";
    /** 事件保留时长。 */
    public static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MemoryStreamManager fallback;
    /** 粘性降级标志：首次 Redis 故障后固定走 memory（避免每条事件都等超时）。 */
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    public RedisStreamManager(StringRedisTemplate redis, ObjectMapper objectMapper,
                              MemoryStreamManager fallback) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
    }

    private static String key(String runId) {
        return KEY_PREFIX + runId;
    }

    @Override
    public void appendEvent(String runId, Map<String, Object> event) {
        if (degraded.get()) {
            fallback.appendEvent(runId, event);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            String k = key(runId);
            redis.opsForList().rightPush(k, json);
            // 超限丢最旧：只保留最近 MAX_EVENTS_PER_RUN 条
            redis.opsForList().trim(k, -MAX_EVENTS_PER_RUN, -1);
            redis.expire(k, TTL);
        } catch (Exception e) {
            degrade(e);
            fallback.appendEvent(runId, event);
        }
    }

    @Override
    public List<Map<String, Object>> getEvents(String runId, long afterSeq) {
        if (degraded.get()) {
            return fallback.getEvents(runId, afterSeq);
        }
        List<String> raw;
        try {
            raw = redis.opsForList().range(key(runId), 0, -1);
        } catch (Exception e) {
            degrade(e);
            return fallback.getEvents(runId, afterSeq);
        }
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String json : raw) {
            Map<String, Object> event = deserialize(json);
            if (event == null) {
                continue;
            }
            if (seqOf(event) > afterSeq) {
                result.add(event);
            }
        }
        result.sort(Comparator.comparingLong(RedisStreamManager::seqOf));
        return result;
    }

    @Override
    public void clear(String runId) {
        fallback.clear(runId);
        if (degraded.get()) {
            return;
        }
        try {
            redis.delete(key(runId));
        } catch (Exception e) {
            degrade(e);
        }
    }

    private void degrade(Exception cause) {
        if (degraded.compareAndSet(false, true)) {
            log.warn("[RedisStreamManager] Redis unavailable, degrade to memory stream: {}",
                    cause.getMessage());
        }
    }

    private Map<String, Object> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[RedisStreamManager] Skip corrupt event json: {}", e.getMessage());
            return null;
        }
    }

    private static long seqOf(Map<String, Object> event) {
        Object seq = event.get("seq");
        return seq instanceof Number n ? n.longValue() : 0L;
    }
}
