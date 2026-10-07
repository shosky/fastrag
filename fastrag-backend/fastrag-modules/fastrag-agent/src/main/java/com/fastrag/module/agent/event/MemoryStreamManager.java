package com.fastrag.module.agent.event;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存版事件通道（I2）。单 run 容量 {@link StreamManager#MAX_EVENTS_PER_RUN}（超限丢最旧），
 * 拉取按 seq 升序过滤。作为 RedisStreamManager 的降级兜底，也可独立用于单机部署/测试。
 */
@Component
public class MemoryStreamManager implements StreamManager {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>> store =
            new ConcurrentHashMap<>();

    @Override
    public void appendEvent(String runId, Map<String, Object> event) {
        CopyOnWriteArrayList<Map<String, Object>> list =
                store.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            list.add(event);
            if (list.size() > MAX_EVENTS_PER_RUN) {
                // 超限丢最旧（按 seq 判定，防并发乱序误删新事件）
                list.sort(Comparator.comparingLong(MemoryStreamManager::seqOf));
                list.remove(0);
            }
        }
    }

    @Override
    public List<Map<String, Object>> getEvents(String runId, long afterSeq) {
        CopyOnWriteArrayList<Map<String, Object>> list = store.get(runId);
        if (list == null) {
            return List.of();
        }
        List<Map<String, Object>> snapshot = new ArrayList<>(list);
        snapshot.removeIf(e -> seqOf(e) <= afterSeq);
        snapshot.sort(Comparator.comparingLong(MemoryStreamManager::seqOf));
        return snapshot;
    }

    @Override
    public void clear(String runId) {
        store.remove(runId);
    }

    private static long seqOf(Map<String, Object> event) {
        Object seq = event.get("seq");
        return seq instanceof Number n ? n.longValue() : 0L;
    }
}
