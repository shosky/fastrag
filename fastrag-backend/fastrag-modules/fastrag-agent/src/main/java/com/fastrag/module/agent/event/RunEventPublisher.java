package com.fastrag.module.agent.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发布与存储 Agent 运行事件.
 * <p>
 * 当前使用内存 {@link ConcurrentHashMap} 进行本地事件存储，
 * 后续可替换为 Redis pub/sub 或 Stream 实现分布式事件发布.
 * <p>
 * 事件结构:
 * <pre>
 * {
 *   "runId": "xxx",
 *   "eventType": "status" | "message" | "tool_call" | "artifact" | "subagent" | ...,
 *   "payload": { ... },
 *   "seq": timestamp
 * }
 * </pre>
 */
@Slf4j
@Component
public class RunEventPublisher {

    /**
     * 内存事件存储. Key: runId, Value: 事件列表.
     */
    private final ConcurrentHashMap<String, List<Map<String, Object>>> eventStore =
            new ConcurrentHashMap<>();

    /**
     * 推送事件.
     *
     * @param runId     运行 ID
     * @param eventType 事件类型 (status / message / tool_call / artifact / subagent ...)
     * @param payload   事件载荷
     */
    public void pushEvent(String runId, String eventType, Object payload) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("runId", runId);
            event.put("eventType", eventType);
            event.put("payload", payload);
            event.put("seq", System.currentTimeMillis());

            log.debug("Run event: runId={}, eventType={}", runId, eventType);

            eventStore.computeIfAbsent(runId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(event);

            // TODO: Redis pub/sub integration for distributed scenarios
        } catch (Exception e) {
            log.error("Failed to publish run event: runId={}, eventType={}", runId, eventType, e);
        }
    }

    /**
     * 获取某次运行的全部事件 (内存).
     *
     * @param runId 运行 ID
     * @return 事件列表，不存在则返回空列表
     */
    public List<Map<String, Object>> getEvents(String runId) {
        return eventStore.getOrDefault(runId, Collections.emptyList());
    }
}
