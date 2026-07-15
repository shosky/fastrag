package com.fastrag.module.agent.context;

import com.fastrag.module.agent.executor.ModelConfig;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BaseContext {

    @ConfigField
    private String threadId;

    @ConfigField
    private String uid;

    @ConfigField(hide = true)
    private String runId;

    @ConfigField(hide = true)
    private String requestId;

    @ConfigField(kind = "prompt")
    private String systemPrompt;

    @ConfigField(kind = "llm")
    private String model;

    @ConfigField(type = "list", kind = "tools")
    private List<String> tools;

    @ConfigField(type = "list", kind = "knowledges")
    private List<String> knowledges;

    @ConfigField(type = "list", kind = "mcps")
    private List<String> mcps;

    @ConfigField(type = "list", kind = "skills")
    private List<String> skills;

    @ConfigField(type = "list", kind = "databases")
    private List<String> databases;

    @ConfigField(type = "number")
    private Integer maxSteps;

    @ConfigField(type = "number", auth = "admin")
    private Integer summaryThreshold;

    @ConfigField(type = "number", auth = "admin")
    private Integer modelRetryTimes;

    private transient List<String> visibleKnowledgeBases;

    private transient List<String> promptSkills;

    private transient List<String> readableSkills;

    private transient Map<String, Object> runtimeSkillMetadata;

    private transient Map<String, Object> runtimeSkillDependencyMap;

    /** 运行时模型配置（由 ContextMiddleware 或 AppServiceImpl 填充） */
    private transient ModelConfig modelConfig;

    /** 运行时状态（todos、artifacts 等，由中间件读写） */
    private transient Map<String, Object> runtimeState = new HashMap<>();

    /**
     * 获取运行时状态 Map，确保非 null。
     */
    public Map<String, Object> getRuntimeState() {
        if (runtimeState == null) {
            runtimeState = new HashMap<>();
        }
        return runtimeState;
    }

    public void updateFromMap(Map<String, Object> map) {
        if (map == null) {
            return;
        }
        // Traverse the class hierarchy to pick up @ConfigField from superclasses too
        Class<?> clazz = this.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                ConfigField configField = field.getAnnotation(ConfigField.class);
                if (configField == null) {
                    continue;
                }
                String fieldName = field.getName();
                if (map.containsKey(fieldName)) {
                    field.setAccessible(true);
                    try {
                        field.set(this, map.get(fieldName));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Failed to set field: " + fieldName, e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public List<ConfigurableItem> getConfigurableItems(String userRole) {
        List<ConfigurableItem> items = new ArrayList<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            ConfigField configField = field.getAnnotation(ConfigField.class);
            if (configField == null || !configField.configurable()) {
                continue;
            }
            if (configField.hide()) {
                continue;
            }
            if (!configField.auth().isEmpty() && !configField.auth().equals(userRole)) {
                continue;
            }
            ConfigurableItem item = new ConfigurableItem();
            item.setField(field.getName());
            item.setName(configField.name().isEmpty() ? field.getName() : configField.name());
            item.setType(configField.type());
            item.setKind(configField.kind());
            item.setDescription(configField.description());
            item.setAuth(configField.auth());
            items.add(item);
        }
        return items;
    }
}
