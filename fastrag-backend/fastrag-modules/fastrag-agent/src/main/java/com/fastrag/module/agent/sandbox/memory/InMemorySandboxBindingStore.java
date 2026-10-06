package com.fastrag.module.agent.sandbox.memory;

import com.fastrag.module.agent.sandbox.BindingRecord;
import com.fastrag.module.agent.sandbox.SandboxBindingStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内绑定存储（SandboxBindingStore 内存适配器——单实例开发/测试，design 缝 6 附属缝假件）。
 *
 * <p>CAS 换绑用 {@code ConcurrentHashMap.compute} 原子完成；生产多副本部署用
 * {@link com.fastrag.module.agent.sandbox.redis.RedisSandboxBindingStore}。</p>
 */
@Component
public class InMemorySandboxBindingStore implements SandboxBindingStore {

    /** key = "{userId}:{sessionId}" */
    private final Map<String, BindingRecord> bindings = new ConcurrentHashMap<>();

    private static String key(String userId, String sessionId) {
        return userId + ":" + sessionId;
    }

    @Override
    public BindingRecord get(String userId, String sessionId) {
        BindingRecord record = bindings.get(key(userId, sessionId));
        return record == null ? null : copyOf(record);
    }

    @Override
    public void put(String userId, String sessionId, BindingRecord record) {
        bindings.put(key(userId, sessionId), copyOf(record));
    }

    @Override
    public boolean casRebind(String userId, String sessionId, String oldSandboxId, BindingRecord newBinding) {
        String[] outcome = new String[1];
        bindings.compute(key(userId, sessionId), (k, current) -> {
            if (current == null || oldSandboxId == null
                    || !oldSandboxId.equals(current.getSandboxId())) {
                outcome[0] = "miss";
                return current; // 不变
            }
            outcome[0] = "hit";
            return copyOf(newBinding);
        });
        return "hit".equals(outcome[0]);
    }

    @Override
    public void markStaleByConfigId(String configId) {
        Instant now = Instant.now();
        for (Map.Entry<String, BindingRecord> entry : bindings.entrySet()) {
            BindingRecord record = entry.getValue();
            if (record.getConfigId() != null && record.getConfigId().equals(configId)) {
                entry.setValue(record.withStaleAt(now));
            }
        }
    }

    @Override
    public void delete(String userId, String sessionId) {
        bindings.remove(key(userId, sessionId));
    }

    @Override
    public List<BindingRecord> listByConfigId(String configId) {
        List<BindingRecord> result = new ArrayList<>();
        for (BindingRecord record : bindings.values()) {
            if (record.getConfigId() != null && record.getConfigId().equals(configId)) {
                result.add(copyOf(record));
            }
        }
        return result;
    }

    @Override
    public List<BindingEntry> listAll() {
        List<BindingEntry> result = new ArrayList<>(bindings.size());
        for (Map.Entry<String, BindingRecord> entry : bindings.entrySet()) {
            int split = entry.getKey().indexOf(':');
            String userId = split > 0 ? entry.getKey().substring(0, split) : entry.getKey();
            String sessionId = split > 0 ? entry.getKey().substring(split + 1) : "";
            result.add(new BindingEntry(userId, sessionId, copyOf(entry.getValue())));
        }
        return result;
    }

    private BindingRecord copyOf(BindingRecord record) {
        return new BindingRecord(record.getVersion(), record.getProvider(), record.getSandboxId(),
                record.getConfigId(), record.getCreatedAt(), record.getStaleAt());
    }
}
