package com.fastrag.module.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent序列化视图对象（VO），用于将Agent实体转换为前端友好的API响应格式。
 *
 * <p>使用场景：作为所有Agent相关GET接口的返回值类型，由{@link com.fastrag.module.agent.service.AgentService#serialize}方法构建。</p>
 *
 * <p>字段分为两部分：
 * <ul>
 *   <li>Agent基础字段 - 直接从{@link com.fastrag.module.agent.entity.Agent}实体映射而来：
 *     id、backendId、name、description、slug、icon、configJson、shareConfig、accessLevel、
 *     isDefault、isSubagent、createdBy、updatedBy、createdAt、updatedAt</li>
 *   <li>扩展字段 - 由AgentService在序列化时动态计算和填充：
 *     canManage（当前用户是否有管理权限）、isBuiltin（是否为系统内置）、
 *     isSubagentFlag（是否为子Agent标记）、capabilities（后端能力列表）、
 *     metadata（后端元数据）、configurableItems（可配置项列表）、permissionLocked（权限是否锁定）</li>
 * </ul></p>
 *
 * @see com.fastrag.module.agent.service.AgentService#serialize 构建此VO的方法
 * @see com.fastrag.module.agent.entity.Agent 源实体类
 */
@Data
public class AgentSerializeVO {

    // --- Agent fields ---
    private String id;
    private String backendId;
    private String name;
    private String description;
    private String slug;
    private String icon;
    private Map<String, Object> configJson;
    private Map<String, Object> shareConfig;
    private String accessLevel;
    private boolean isDefault;
    private boolean isSubagent;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Extra fields ---
    private boolean canManage;
    private boolean isBuiltin;
    private boolean isSubagentFlag;
    private List<String> capabilities;
    private Map<String, Object> metadata;
    private Map<String, Object> configurableItems;
    private boolean permissionLocked;
}
