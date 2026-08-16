package com.fastrag.module.agent.context;

import com.fastrag.module.agent.executor.ModelConfig;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent运行时上下文基类，是所有Agent后端上下文的公共父类。
 *
 * <p>核心职责：
 * <ul>
 *   <li>定义Agent运行时所需的全部配置字段，包括线程标识、用户标识、系统提示词、模型选择、工具列表等</li>
 *   <li>通过{@link ConfigField}注解声明字段的元数据（类型、分类、权限、是否可配置等），用于前端配置界面的动态渲染</li>
 *   <li>提供从Map批量填充配置的{@link #updateFromMap(Map)}方法和获取可配置项列表的{@link #getConfigurableItems(String)}方法</li>
 *   <li>维护运行时状态数据（runtimeState）和运行时模型配置（modelConfig），供中间件在执行过程中读写</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>使用@ConfigField注解标记的持久化字段会被序列化到Agent配置JSON中保存</li>
 *   <li>transient字段（如visibleKnowledgeBases、promptSkills、runtimeState等）仅在单次运行期间有效，不会被持久化</li>
 *   <li>updateFromMap方法通过反射遍历类层次结构（包括父类），将Map中的键值对设置到对应字段</li>
 *   <li>getConfigurableItems方法根据用户角色（userRole）过滤返回可配置的UI项，支持auth权限控制</li>
 * </ul></p>
 *
 * <p>字段分类说明：
 * <ul>
 *   <li>基础标识：threadId、uid、runId、requestId</li>
 *   <li>模型配置：model、systemPrompt、maxSteps</li>
 *   <li>工具能力：tools、skills、mcps、databases、knowledges</li>
 *   <li>运行时控制：summaryThreshold（摘要触发阈值）、modelRetryTimes（模型重试次数，仅管理员可配）</li>
 * </ul></p>
 *
 * @see ConfigField 配置字段元数据注解
 * @see ConfigurableItem 可配置项数据模型
 * @see ChatBotContext 聊天机器人扩展上下文
 */
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
