package com.fastrag.module.agent.sandbox.redis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fastrag.module.agent.sandbox.BindingRecord;
import com.fastrag.module.agent.sandbox.SandboxBindingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis 绑定存储（SandboxBindingStore 生产适配器——多副本共享，REQ-H01）。
 *
 * <p>Key：{@code sandbox:binding:{userId}:{sessionId}}；Value：BindingRecord JSON。
 * <b>不设 Redis TTL</b>——data.md 明示：绑定存活由 sandbox_config 的 idle_ttl 与 reclaimIdle
 * 驱动删除；Redis 过期会造成「provider 侧实例仍在、绑定已失」的泄漏形态（孤儿对账兜底）。</p>
 *
 * <p>CAS 换绑以 Lua 脚本原子完成（GET → 比对 sandboxId → SET，对齐 WeKnora session_binding）；
 * 按配置反查与全量列举经 SCAN（绑定键规模 = 会话数，量级小）。</p>
 */
@Slf4j
@Component
@Primary
public class RedisSandboxBindingStore implements SandboxBindingStore {

    /** key 前缀：sandbox:binding:{userId}:{sessionId} */
    public static final String KEY_PREFIX = "sandbox:binding:";

    private static final String CAS_REBIND_LUA = """
            local current = redis.call('GET', KEYS[1])
            if not current then return 0 end
            local ok, record = pcall(cjson.decode, current)
            if not ok or record == nil then return 0 end
            if record['sandboxId'] ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2])
            return 1
            """;

    private static final DefaultRedisScript<Long> CAS_SCRIPT =
            new DefaultRedisScript<>(CAS_REBIND_LUA, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSandboxBindingStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static String key(String userId, String sessionId) {
        return KEY_PREFIX + userId + ":" + sessionId;
    }

    @Override
    public BindingRecord get(String userId, String sessionId) {
        String json = redis.opsForValue().get(key(userId, sessionId));
        return deserialize(json);
    }

    @Override
    public void put(String userId, String sessionId, BindingRecord record) {
        redis.opsForValue().set(key(userId, sessionId), serialize(record)); // 无 TTL
    }

    @Override
    public boolean casRebind(String userId, String sessionId, String oldSandboxId, BindingRecord newBinding) {
        Long result = redis.execute(CAS_SCRIPT, List.of(key(userId, sessionId)),
                oldSandboxId == null ? "" : oldSandboxId, serialize(newBinding));
        return result != null && result == 1L;
    }

    @Override
    public void markStaleByConfigId(String configId) {
        Instant now = Instant.now();
        scanAll((userId, sessionId, record) -> {
            if (record.getConfigId() != null && record.getConfigId().equals(configId)) {
                redis.opsForValue().set(key(userId, sessionId), serialize(record.withStaleAt(now)));
            }
        });
    }

    @Override
    public void delete(String userId, String sessionId) {
        redis.delete(key(userId, sessionId));
    }

    @Override
    public List<BindingRecord> listByConfigId(String configId) {
        List<BindingRecord> result = new ArrayList<>();
        scanAll((userId, sessionId, record) -> {
            if (record.getConfigId() != null && record.getConfigId().equals(configId)) {
                result.add(record);
            }
        });
        return result;
    }

    @Override
    public List<BindingEntry> listAll() {
        List<BindingEntry> result = new ArrayList<>();
        scanAll((userId, sessionId, record) -> result.add(new BindingEntry(userId, sessionId, record)));
        return result;
    }

    private interface RecordVisitor {
        void visit(String userId, String sessionId, BindingRecord record);
    }

    private void scanAll(RecordVisitor visitor) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "*").count(500).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                BindingRecord record = deserialize(redis.opsForValue().get(key));
                if (record == null) {
                    continue;
                }
                String rest = key.substring(KEY_PREFIX.length());
                int split = rest.indexOf(':');
                String userId = split > 0 ? rest.substring(0, split) : rest;
                String sessionId = split > 0 ? rest.substring(split + 1) : "";
                visitor.visit(userId, sessionId, record);
            }
        } catch (Exception e) {
            // 绑定存储不可读不阻断调用方（回收/对账为旁路任务），按零结果记账
            log.warn("[RedisSandboxBindingStore] scan failed, treat as empty: {}", e.getMessage());
        }
    }

    private String serialize(BindingRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize sandbox binding", e);
        }
    }

    private BindingRecord deserialize(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BindingRecord.class);
        } catch (Exception e) {
            log.warn("[RedisSandboxBindingStore] Failed to deserialize binding, drop: {}", e.getMessage());
            return null;
        }
    }
}
