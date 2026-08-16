package com.fastrag.module.agent.backend;

import com.fastrag.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent后端注册中心与管理器，负责发现、注册和查找所有{@link AgentBackend}实现。
 *
 * <p>核心职责：
 * <ul>
 *   <li>在Spring容器启动时（@PostConstruct），自动从ApplicationContext中扫描所有AgentBackend类型的Bean并注册</li>
 *   <li>维护一个以backendId为Key的有序Map，支持按ID查找后端</li>
 *   <li>对外提供统一的查询接口，包括按ID获取、判断是否存在、获取所有已注册后端等</li>
 *   <li>封装后端信息查询逻辑，可直接返回适用于API响应的后端信息Map</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>使用LinkedHashMap保持后端的注册顺序</li>
 *   <li>当出现重复ID时会打印警告日志并覆盖已注册的后端</li>
 *   <li>查找不存在的后端时抛出BusinessException（NOT_FOUND）</li>
 * </ul></p>
 *
 * @see AgentBackend Agent后端接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentBackendManager {

    private final ApplicationContext applicationContext;

    private final Map<String, AgentBackend> backends = new LinkedHashMap<>();

    /**
     * Discovers all AgentBackend beans from the application context and registers them.
     */
    @PostConstruct
    public void init() {
        Collection<AgentBackend> beans = applicationContext.getBeansOfType(AgentBackend.class).values();
        for (AgentBackend backend : beans) {
            register(backend);
        }
        log.info("Registered {} agent backends: {}", backends.size(), backends.keySet());
    }

    /**
     * Registers a backend into the registry.
     *
     * @param backend the backend to register
     */
    public void register(AgentBackend backend) {
        String id = backend.getId();
        if (backends.containsKey(id)) {
            log.warn("Overwriting existing agent backend with id '{}'", id);
        }
        backends.put(id, backend);
    }

    /**
     * Gets a backend by its unique identifier.
     *
     * @param backendId the backend identifier
     * @return the matching AgentBackend
     * @throws BusinessException if the backend is not found
     */
    public AgentBackend getBackend(String backendId) {
        AgentBackend backend = backends.get(backendId);
        if (backend == null) {
            throw BusinessException.notFound("Agent backend not found: " + backendId);
        }
        return backend;
    }

    /**
     * Checks whether a backend with the given id is registered.
     *
     * @param backendId the backend identifier
     * @return true if registered
     */
    public boolean hasBackend(String backendId) {
        return backends.containsKey(backendId);
    }

    /**
     * Returns an unmodifiable view of all registered backends.
     *
     * @return map of backend id to backend instance
     */
    public Map<String, AgentBackend> getAllBackends() {
        return Collections.unmodifiableMap(backends);
    }

    /**
     * Gets backend info as a map suitable for API responses.
     *
     * @param backendId                the backend identifier
     * @param includeConfigurableItems whether to include configurable items
     * @param userRole                 the role of the requesting user
     * @return info map from the backend
     */
    public Map<String, Object> getBackendInfoVO(String backendId, boolean includeConfigurableItems, String userRole) {
        AgentBackend backend = getBackend(backendId);
        return backend.getInfo(includeConfigurableItems, userRole);
    }
}
