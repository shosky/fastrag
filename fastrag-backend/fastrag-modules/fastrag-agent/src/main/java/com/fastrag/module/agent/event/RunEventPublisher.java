package com.fastrag.module.agent.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent运行事件发布与存储组件，负责记录和推送Agent执行过程中的各类事件。
 *
 * <p>核心职责：
 * <ul>
 *   <li>接收Agent执行过程中产生的各类事件（状态变更、消息、工具调用、产物输出、子Agent事件等）</li>
 *   <li>将事件存储到内存中，供SSE事件流接口查询和推送</li>
 *   <li>为每个事件附加序列号（seq，基于时间戳），支持客户端断点续传</li>
 * </ul></p>
 *
 * <p>事件结构包含四个字段：
 * <ul>
 *   <li>runId - 关联的Agent运行ID</li>
 *   <li>eventType - 事件类型（status、message、tool_call、artifact、subagent等）</li>
 *   <li>payload - 事件负载数据，不同事件类型有不同结构</li>
 *   <li>seq - 事件序列号（毫秒时间戳），用于客户端断点续传</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>当前使用ConcurrentHashMap进行本地内存存储，后续可替换为Redis pub/sub或Stream实现分布式事件发布</li>
 *   <li>eventStore以runId为Key，每个运行维护一个同步事件列表</li>
 *   <li>pushEvent方法线程安全，通过computeIfAbsent和synchronizedList保证并发写入安全</li>
 *   <li>由AgentRunServiceImpl的SSE事件流接口调用getEvents方法获取事件并推送给客户端</li>
 * </ul></p>
 *
 * @see com.fastrag.module.agent.service.impl.AgentRunServiceImpl 事件的使用者（SSE推送）
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
