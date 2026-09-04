package com.fastrag.module.bpm.executor;

import com.fastrag.module.bpm.enums.BpmErrorCode;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** NodeExecutor 注册表(基于 Spring 容器扫描,key = executor.type()) */
@Component
public class NodeExecutorRegistry implements BeanPostProcessor, ApplicationContextAware {
    private final Map<String, NodeExecutor> registry = new HashMap<>();
    private ApplicationContext applicationContext;

    public NodeExecutor getExecutor(String type) {
        NodeExecutor e = registry.get(type);
        if (e == null) throw BpmErrorCode.NODE_TYPE_NOT_REGISTERED.of(type);
        return e;
    }

    @Override public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof NodeExecutor ex) {
            registry.put(ex.type(), ex);
        }
        return bean;
    }

    @Override public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public Map<String, NodeExecutor> all() { return java.util.Collections.unmodifiableMap(registry); }
}