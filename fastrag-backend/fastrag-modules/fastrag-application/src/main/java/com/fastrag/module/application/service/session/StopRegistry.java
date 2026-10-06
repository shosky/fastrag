package com.fastrag.module.application.service.session;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 停止注册表（new-conversation）：单实例进行中对话的取消协议（contract new-conversation.StopRegistry）。
 *
 * <p>字段面钉死为 handle 三件套——禁止演进为事件重放/多副本结构；StreamManager 事件模型
 * 归 agent 域，落地后本组件整体替换为其适配（design §1 硬边界）。
 *
 * <p>并发契约：cancel 幂等（首次 true 且恰触发一次 partialSave）；register 为覆盖语义
 * （后注册者取代取消目标——同会话并发双流的互斥治理不在本特性，已登记债务）。
 */
@Component
public class StopRegistry {

    /** 取消句柄：cancelled=取消标志；saved=落库幂等守卫（防 complete 回调与 cancel 双写）；partialSave=部分落库动作 */
    @Data
    public static class CancelHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean saved = new AtomicBoolean(false);
        private final Runnable partialSave;
    }

    private final Map<String, CancelHandle> handles = new ConcurrentHashMap<>();

    /** 注册进行中对话的取消句柄；同会话重复注册为覆盖语义（后注册者成为取消目标） */
    public void register(String conversationId, CancelHandle handle) {
        handles.put(conversationId, handle);
    }

    /**
     * 取消对话：首次调用置位并触发恰一次部分落库；其后调用返回 false（幂等）。
     *
     * @return 是否由本次调用触发取消
     */
    public boolean cancel(String conversationId) {
        CancelHandle handle = handles.get(conversationId);
        if (handle == null) return false;
        if (!handle.cancelled.compareAndSet(false, true)) return false;
        if (handle.saved.compareAndSet(false, true)) {
            handle.partialSave.run();
        }
        return true;
    }

    /** 流终态（完成/超时/异常/停止）清理；之后 cancel 返回 false */
    public void unregister(String conversationId) {
        handles.remove(conversationId);
    }

    /** 该会话当前是否有活跃（已注册未清理）句柄 */
    public boolean isActive(String conversationId) {
        return handles.containsKey(conversationId);
    }
}
