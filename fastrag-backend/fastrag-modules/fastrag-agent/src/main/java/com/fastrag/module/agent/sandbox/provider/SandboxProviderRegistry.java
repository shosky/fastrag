package com.fastrag.module.agent.sandbox.provider;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 后端类型 → 适配器解析器（缝 6 的装配点）。
 * <p>
 * Spring 注入全部 {@link SandboxProviderClient} 实现并按 {@link SandboxProviderClient#provider()}
 * 建立映射；未注册类型（含 disabled）按 UNSUPPORTED 分类拒绝。
 */
@Component
public class SandboxProviderRegistry {

    private final Map<String, SandboxProviderClient> byType = new HashMap<>();

    public SandboxProviderRegistry(List<SandboxProviderClient> clients) {
        for (SandboxProviderClient client : clients) {
            byType.put(client.provider(), client);
        }
    }

    public Optional<SandboxProviderClient> find(String sandboxType) {
        return Optional.ofNullable(byType.get(sandboxType));
    }

    /**
     * 取适配器，缺失/未接线抛 UNSUPPORTED 分类异常。
     */
    public SandboxProviderClient require(String sandboxType) {
        SandboxProviderClient client = byType.get(sandboxType);
        if (client == null) {
            throw SandboxProviderException.unsupported(
                    "sandbox type '" + sandboxType + "' has no wired provider adapter");
        }
        return client;
    }
}
